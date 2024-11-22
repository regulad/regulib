package xyz.regulad.regulib

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.regulad.regulib.FlowCache.Companion.asCached
import xyz.regulad.regulib.agnostic.versionAgnosticComputeIfAbsent
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KClass

/**
 * A cache for flows that persists between application restarts, effectively turning cold flows into hot flows, maintaining their ability to be completed.
 *
 * This class should not be used directly. Instead, use [Flow.asCached] to create a cached proxy of a flow.
 */
class FlowCache @PublishedApi internal constructor(context: Context) :
    SQLiteOpenHelper(context, File(context.cacheDir, DATABASE_NAME).absolutePath, null, DATABASE_VERSION) {
    companion object {
        // no need to declare a custom initial size of a hashmap: it will grow as needed efficiently
        const val TAG = "FlowCache"

        internal const val DATABASE_NAME = "regulib_flow_cache.db"
        private const val DATABASE_VERSION = 1

        @PublishedApi // we use these annotations since the asCached is inlined for the reified type, this effectively makes the internal accessible from the inline but maintains clarity that they should not be used
        internal const val TABLE_NAME = "cache_table"

        @PublishedApi
        internal const val COLUMN_ID = "id"

        @PublishedApi
        internal const val COLUMN_DATA = "data"

        @PublishedApi
        internal val flowCaches: MutableMap<Context, FlowCache> = Collections.synchronizedMap(WeakHashMap())

        /**
         * Caches a flow relative to a context, only calling the backing flow once and replaying all items to any new collectors. If a non-null `cacheKey` is provided, the flow will be persisted between application restarts.
         *
         * @param context the context to cache the flow in, typically an activity context. Data will be stored in the context's app's cache directory.
         * @param cacheKey The key of the cache. Make sure that this value is unique for each flow you cache. The system's board name is used by default.
         * @return a cached version of the flow, which is "hot" yet has infinite replay meaning that all items will be replayed to any new collectors
         * @throws IllegalArgumentException if the flow is not of the correct type
         */
        inline fun <reified T : @Serializable Any> Flow<T>.asCached(
            context: Context,
            cacheKey: String? = Build.BOARD
        ): Flow<T> {
            val flowCache = synchronized(this@Companion) {
                flowCaches.versionAgnosticComputeIfAbsent(context) { FlowCache(context) }
            }
            return flowCache.cachedFlowOf(this, cacheKey)
        }
    }

    @PublishedApi
    internal val collectionCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PublishedApi
    internal val dbLock = ReentrantReadWriteLock()

    @PublishedApi
    internal val flowCache = ConcurrentHashMap<Pair<KClass<*>, String>, Flow<*>>()

    @PublishedApi
    internal inline fun <reified T : @Serializable Any> cachedFlowOf(flow: Flow<T>, cacheKey: String?): Flow<T> {
        if (cacheKey != null) {
            val map = flowCache.versionAgnosticComputeIfAbsent(T::class to cacheKey) {
                // we can't use a hot flow because hot flows don't have the ability to "finish" a stream
                // thus, we must implement a custom solution that sends in all the values from the flow once they are

                val newProxyFlow = calculateCachedFlow(cacheKey, flow)

                return@versionAgnosticComputeIfAbsent newProxyFlow
            }

            @Suppress("UNCHECKED_CAST") // we just manually verified that the flow is of the correct type
            return map as Flow<T>
        } else {
            return calculateCachedFlow(cacheKey, flow)
        }
    }

    /**
     * Creates a cached flow that is replayed to all collectors, and is persisted between application restarts if a cache key is provided.
     */
    @PublishedApi
    internal inline fun <reified T : @Serializable Any> calculateCachedFlow(
        cacheKey: String?,
        flow: Flow<T>
    ): Flow<T> {
        val collectionLock = Mutex() // enforces linear collection of items
        val itemsReceived = mutableListOf<T>()
        val newItemFlow = MutableSharedFlow<T>(0)
        val streamCompletedFlow = MutableStateFlow(false) // beware: no replay of this state

        collectionCoroutineScope.launch {
            Log.d(TAG, "Starting collection of flow for cache key $cacheKey")

            Log.d(TAG, "Reading cached items for key $cacheKey")
            val cachedItems = try {
                if (cacheKey != null) {
                    readListOfId<T>(cacheKey)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cached items for key $cacheKey, reproducing cache", e)
                null
            }

            if (cachedItems != null) {
                Log.d(TAG, "Replaying cached items for key $cacheKey")
                cachedItems.forEach { newItemFlow.emit(it) }
                itemsReceived.addAll(cachedItems)
                streamCompletedFlow.value = true
            } else {
                Log.d(TAG, "No cached items found for key $cacheKey, collecting from flow")
                flow.collect {
                    collectionLock.withLock {
                        itemsReceived.add(it)
                        newItemFlow.emit(it)
                    }
                }
                streamCompletedFlow.value = true

                if (cacheKey == null) {
                    // we don't want to save it
                    return@launch
                }

                // store in db
                try {
                    Log.d(TAG, "Writing cached items for key $cacheKey")
                    writeListOfId(cacheKey, itemsReceived)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write cached items for key $cacheKey", e)
                }
            }
        }

        // we use a channel flow instead of flow since we launch a coroutine that adds items to the flow
        val newProxyFlow = channelFlow {
            // although we could choose just to reply on the replay of the newItemFlow here,
            // since we already have to collect the items in order to save them,
            // we might as well just send them all and avoid registering as a collector earlier

            // in addition, otherwise we could miss items if the streamCompletedFlow turns true while collecting but before we emit all items
            if (streamCompletedFlow.value) {
                itemsReceived.forEach { channel.send(it) }
                return@channelFlow
            } else {
                val alreadyEmittedItems = mutableListOf<T>()

                // tricky problem: if we cancel this job when the streamCompleted is true, we may have missed some items
                val itemCollectionJob = collectionCoroutineScope.launch {
                    newItemFlow.collect {
                        channel.send(it)
                        alreadyEmittedItems.add(it)
                    }
                }

                streamCompletedFlow.waitForTrue()
                itemCollectionJob.cancelAndJoin() // everything in alreadyEmittedItems is guaranteed to be emitted, and itemsReceived is guaranteed to be complete

                // emit items we haven't emitted yet but have received, remembering that all items are received in order
                if (alreadyEmittedItems.size < itemsReceived.size) {
                    itemsReceived.subList(alreadyEmittedItems.size, itemsReceived.size).forEach { channel.send(it) }
                }

                return@channelFlow
            }
        }

        return newProxyFlow
    }

    @PublishedApi
    internal suspend inline fun <reified T : @Serializable Any> readListOfId(id: String): List<T>? {
        // we don't need to wait for the DB to open, it will open when lazily needed
        return withContext(Dispatchers.IO) {
            dbLock.read {
                readableDatabase.use { db ->
                    db.query(
                        TABLE_NAME,
                        arrayOf(COLUMN_DATA),
                        "$COLUMN_ID = ?",
                        arrayOf("${T::class.java.name}::$id"),
                        null,
                        null,
                        null
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            @SuppressLint("Range") // we know that the data is in the column
                            val data = cursor.getString(cursor.getColumnIndex(COLUMN_DATA))
                            return@read Json.decodeFromString(data)
                        } else {
                            return@read null
                        }
                    }
                }
            }
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T : @Serializable Any> writeListOfId(id: String, data: List<T>) {
        // we don't need to wait for the DB to open, it will open when lazily needed
        return withContext(Dispatchers.IO) {
            dbLock.write {
                writableDatabase.use { db ->
                    db.insertWithOnConflict(
                        TABLE_NAME,
                        null,
                        ContentValues().apply {
                            put(COLUMN_ID, "${T::class.java.name}::$id")
                            put(COLUMN_DATA, Json.encodeToString(data))
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        dbLock.write {
            val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_DATA TEXT
            )
        """.trimIndent()
            db.execSQL(createTableQuery)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        dbLock.write {
            // Handle database upgrades here
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    protected fun finalize() {
        collectionCoroutineScope.coroutineContext.cancel()
    }
}
