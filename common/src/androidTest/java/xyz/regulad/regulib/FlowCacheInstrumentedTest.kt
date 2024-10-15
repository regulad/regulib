package xyz.regulad.regulib

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.regulad.regulib.FlowCache.Companion.DATABASE_NAME
import xyz.regulad.regulib.FlowCache.Companion.asCached

@Serializable
data class TestData(val value: Int)

@Serializable
data class AnotherTestData(val value: Int)

@RunWith(AndroidJUnit4::class)
class FlowCacheInstrumentedTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        // Any setup code if needed
    }

    @After
    fun tearDown() {
        // Clean up the database after each test
        appContext.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun testFlowCaching() = runBlocking {
        val testFlow: Flow<TestData> = flow {
            Log.d("FlowCacheInstrumentedTest", "Emitting test data! This should only happen once.")
            emit(TestData(1))
            emit(TestData(2))
            emit(TestData(3))
        }

        val cachedFlow = testFlow.asCached(appContext, "testKey")

        // Collect from the cached flow
        val result1 = cachedFlow.toList()
        assertEquals(listOf(TestData(1), TestData(2), TestData(3)), result1)
        Log.d("FlowCacheInstrumentedTest", "result1: $result1")

        // Collect again to verify that the cache is working
        val result2 = cachedFlow.toList()
        assertEquals(result1, result2)
        Log.d("FlowCacheInstrumentedTest", "result2: $result2")

        // Create a new cached flow with the same key to test persistence
        val newCachedFlow = flowOf<TestData>().asCached(appContext, "testKey")
        val result3 = newCachedFlow.toList()
        assertEquals(result1, result3)
        Log.d("FlowCacheInstrumentedTest", "result3: $result3")

        // last step: check serialization by clearing out the internal cache and re-fetching
        FlowCache.Companion.flowCaches[appContext]!!.flowCache.clear()
        val secondNewCachedFlow = flowOf<TestData>().asCached(appContext, "testKey")
        val result4 = secondNewCachedFlow.toList()
        assertEquals(result1, result4)
        Log.d("FlowCacheInstrumentedTest", "result4: $result4")

        return@runBlocking // assure the test completes with a void value
    }

    @Test
    fun testFlowCachingWithoutKeys() = runBlocking {
        val testFlow: Flow<AnotherTestData> = flow {
            Log.d("FlowCacheInstrumentedTest", "Emitting test data! This should only happen once.")
            emit(AnotherTestData(1))
            emit(AnotherTestData(2))
            emit(AnotherTestData(3))
        }

        val cachedFlow = testFlow.asCached(appContext)

        // Collect from the cached flow
        val result1 = cachedFlow.toList()
        assertEquals(listOf(AnotherTestData(1), AnotherTestData(2), AnotherTestData(3)), result1)
        Log.d("FlowCacheInstrumentedTest", "result1: $result1")

        // Collect again to verify that the cache is working
        val result2 = cachedFlow.toList()
        assertEquals(result1, result2)
        Log.d("FlowCacheInstrumentedTest", "result2: $result2")

        // Create a new cached flow with the same key to test persistence
        val newCachedFlow = flowOf<AnotherTestData>().asCached(appContext)
        val result3 = newCachedFlow.toList()
        assertEquals(result1, result3)
        Log.d("FlowCacheInstrumentedTest", "result3: $result3")

        // last step: check serialization by clearing out the internal cache and re-fetching
        FlowCache.Companion.flowCaches[appContext]!!.flowCache.clear()
        val secondNewCachedFlow = flowOf<AnotherTestData>().asCached(appContext)
        val result4 = secondNewCachedFlow.toList()
        assertEquals(result1, result4)
        Log.d("FlowCacheInstrumentedTest", "result4: $result4")

        return@runBlocking // assure the test completes with a void value
    }
}
