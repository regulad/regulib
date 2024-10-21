package xyz.regulad.regulib

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import xyz.regulad.regulib.StateFlowMapView.Companion.asMutableMap

@ExperimentalCoroutinesApi
class StateFlowMapViewTest {

    private lateinit var flow: MutableStateFlow<Map<String, Int>>
    private lateinit var flowMap: MutableMap<String, Int>
    private lateinit var standardMap: MutableMap<String, Int>

    @Before
    fun setup() {
        flow = MutableStateFlow(mapOf("one" to 1, "two" to 2, "three" to 3))
        flowMap = flow.asMutableMap()
        standardMap = mutableMapOf("one" to 1, "two" to 2, "three" to 3)
    }

    @Test
    fun testSize() {
        assertEquals(standardMap.size, flowMap.size)
    }

    @Test
    fun testContainsKey() {
        assertTrue(flowMap.containsKey("one"))
        assertFalse(flowMap.containsKey("four"))
    }

    @Test
    fun testContainsValue() {
        assertTrue(flowMap.containsValue(2))
        assertFalse(flowMap.containsValue(4))
    }

    @Test
    fun testGet() {
        assertEquals(standardMap["two"], flowMap["two"])
        assertNull(flowMap["four"])
    }

    @Test
    fun testPut() {
        val oldValue = flowMap.put("four", 4)
        assertNull(oldValue)
        assertEquals(4, flowMap["four"])
        assertEquals(4, flow.value["four"])
        assertEquals(4, flowMap.size)
    }

    @Test
    fun testRemove() {
        val removedValue = flowMap.remove("two")
        assertEquals(2, removedValue)
        assertFalse(flowMap.containsKey("two"))
        assertEquals(2, flowMap.size)
    }

    @Test
    fun testClear() {
        flowMap.clear()
        assertTrue(flowMap.isEmpty())
    }

    @Test
    fun testPutAll() {
        val newEntries = mapOf("four" to 4, "five" to 5)
        flowMap.putAll(newEntries)
        assertEquals(5, flowMap.size)
        assertEquals(4, flowMap["four"])
        assertEquals(5, flowMap["five"])
    }

    @Test
    fun testEntrySet() {
        val entrySet = flowMap.entries
        assertEquals(standardMap.entries.size, entrySet.size)
        for (entry in entrySet) {
            assertTrue(standardMap.containsKey(entry.key))
            assertEquals(standardMap[entry.key], entry.value)
        }
    }

    @Test
    fun testEntrySetIterator() {
        val iterator = flowMap.entries.iterator()
        var count = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            assertTrue(standardMap.containsKey(entry.key))
            assertEquals(standardMap[entry.key], entry.value)
            count++
        }
        assertEquals(standardMap.size, count)
    }

    @Test
    fun testEntrySetRemove() {
        val iterator = flowMap.entries.iterator()
        iterator.next()
        iterator.remove()
        assertEquals(2, flowMap.size)
    }

    @Test(expected = ConcurrentModificationException::class)
    fun testConcurrentModification() {
        val iterator = flowMap.entries.iterator()
        iterator.next()
        flowMap["four"] = 4
        iterator.next()
    }

    @Test
    fun testEntrySetValue() {
        val entry = flowMap.entries.find { it.key == "one" }
        assertNotNull(entry)
        entry?.setValue(10)
        assertEquals(10, flowMap["one"])
    }

    @Test
    fun testFlowUpdate() = runTest {
        val flow = MutableStateFlow(mapOf("one" to 1, "two" to 2))
        val flowMap = flow.asMutableMap()

        flowMap["three"] = 3
        assertEquals(mapOf("one" to 1, "two" to 2, "three" to 3), flow.value)

        flow.value = mapOf("four" to 4, "five" to 5)
        assertEquals(2, flowMap.size)
        assertEquals(4, flowMap["four"])
        assertEquals(5, flowMap["five"])
    }
}
