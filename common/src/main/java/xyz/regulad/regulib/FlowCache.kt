package xyz.regulad.regulib

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
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

        const val DATABASE_NAME = "regulib_flow_cache.db"
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
         * Caches the flow in the context, persisting between application restarts.
         *
         * @param context the context to cache the flow in, typically an activity context. Data will be stored in the context's app's cache directory.
         * @param cacheKey The key of the cache. Make sure that this value is unique for each flow you cache. If the value is not unique for two flows of the same type, both flows will share the same cache, and it is undefined behavior which underlying flow will be collected. For two flows of two different types, an `IllegalArgumentException` will be thrown. The default value is `"${Build.BOARD}+${T::class.java.name}"`, which should be fine for most uses including apps that sync data between devices provided that the class is only used in a flow once and the class is not anonymous. In these cases, define a custom cache key.
         * @return a cached version of the flow, which is "hot" yet has infinite replay meaning that all items will be replayed to any new collectors
         * @throws IllegalArgumentException if the flow is not of the correct type
         */
        inline fun <reified T : @Serializable Any> Flow<T>.asCached(
            context: Context,
            cacheKey: String = "${Build.BOARD}+${T::class.java.name}"
        ): Flow<T> =
            flowCaches.versionAgnosticComputeIfAbsent(context) { FlowCache(context) }.cacheFlow(this, cacheKey)
    }

    @PublishedApi
    internal val collectionCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PublishedApi
    internal val dbLock = ReentrantReadWriteLock()

    @PublishedApi
    internal val flowCache = ConcurrentHashMap<String, Flow<*>>()

    @PublishedApi
    // this should never be used with the default key since we include the reified type in the key, but if the user provides a custom key, we need to ensure that the flow is of the correct type to avoid undefined behavior
    internal val flowTypeMap: MutableMap<Flow<*>, KClass<*>> = Collections.synchronizedMap(WeakHashMap())

    @PublishedApi
    internal inline fun <reified T : @Serializable Any> cacheFlow(flow: Flow<T>, cacheKey: String): Flow<T> {
        val map = flowCache.versionAgnosticComputeIfAbsent(cacheKey) {
            // we can't use a hot flow because hot flows don't have the ability to "finish" a stream
            // thus, we must implement a custom solution that sends in all the values from the flow once they are

            val collectionLock = Mutex() // enforces linear collection of items
            val itemsReceived = mutableListOf<T>()
            val newItemFlow = MutableSharedFlow<T>(Int.MAX_VALUE)
            val streamCompletedFlow = MutableStateFlow(false) // beware: no replay of this state

            collectionCoroutineScope.launch {
                val cachedItems = readListOfId<T>(cacheKey)

                if (cachedItems != null) {
                    cachedItems.forEach { newItemFlow.emit(it) }
                    itemsReceived.addAll(cachedItems)
                    streamCompletedFlow.value = true
                } else {
                    flow.collect {
                        collectionLock.withLock {
                            itemsReceived.add(it)
                            newItemFlow.emit(it)
                        }
                    }
                    streamCompletedFlow.value = true

                    // store in db
                    writeListOfId(cacheKey, itemsReceived)
                }
            }

            // we use a channel flow instead of flow since we launch a coroutine that adds items to the flow
            val newProxyFlow = channelFlow {
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

            flowTypeMap[newProxyFlow] = T::class

            newProxyFlow
        }

        // before returning, verify that the flow is the correct type
        if (flowTypeMap[map] != T::class) {
            throw IllegalArgumentException("The flow with key $cacheKey is not of type ${T::class}")
        }

        @Suppress("UNCHECKED_CAST") // we just manually verified that the flow is of the correct type
        return map as Flow<T>
    }

    @PublishedApi
    internal inline fun <reified T : @Serializable Any> readListOfId(id: String): List<T>? = dbLock.read {
        readableDatabase.use { db ->
            db.query(
                TABLE_NAME,
                arrayOf(COLUMN_DATA),
                "$COLUMN_ID = ?",
                arrayOf(id),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    @SuppressLint("Range") // we know that the data is in the column
                    val data = cursor.getString(cursor.getColumnIndex(COLUMN_DATA))
                    return Json.decodeFromString(data)
                } else {
                    return null
                }
            }
        }
    }

    @PublishedApi
    internal inline fun <reified T : @Serializable Any> writeListOfId(id: String, data: List<T>) = dbLock.write {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_NAME,
                null,
                ContentValues().apply {
                    put(COLUMN_ID, id)
                    put(COLUMN_DATA, Json.encodeToString(data))
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
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
