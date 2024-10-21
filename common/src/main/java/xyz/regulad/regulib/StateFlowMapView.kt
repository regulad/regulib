package xyz.regulad.regulib

import kotlinx.coroutines.flow.MutableStateFlow
import xyz.regulad.regulib.StateFlowMapView.Companion.asMutableMap

/**
 * A mutable map that is backed by a [MutableStateFlow]. This map is thread-safe.
 *
 * This class is not meant to be used directly. Instead, use the [asMutableMap] extension function on [MutableStateFlow].
 *
 * @see MutableStateFlow.asMutableMap
 */
class StateFlowMapView<K, V> private constructor(private val backingFlow: MutableStateFlow<Map<K, V>>) :
    AbstractMutableMap<K, V>() {
    companion object {
        /**
         * Returns a mutable map that is backed by the given [MutableStateFlow].
         */
        fun <K, V> MutableStateFlow<Map<K, V>>.asMutableMap(): MutableMap<K, V> {
            return StateFlowMapView(this)
        }
    }

    private fun <J> modifyMap(modifier: MutableMap<K, V>.() -> J): J {
        synchronized(this) {
            val oldValueAsMutable = backingFlow.value.toMutableMap()
            val outputValue = oldValueAsMutable.modifier()
            backingFlow.value = oldValueAsMutable
            return outputValue
        }
    }

    override fun put(key: K, value: V): V? = modifyMap {
        return@modifyMap put(key, value)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> =
        object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean = modifyMap {
                return@modifyMap entries.add(element)
            }

            override val size: Int
                get() = backingFlow.value.size

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                var ourMap = backingFlow.value
                val backingIterator = ourMap.entries.iterator()

                var lastReturned: Map.Entry<K, V>? = null

                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    override fun hasNext(): Boolean {
                        if (ourMap !== backingFlow.value) {
                            throw ConcurrentModificationException()
                        }

                        return backingIterator.hasNext()
                    }

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        if (ourMap !== backingFlow.value) {
                            throw ConcurrentModificationException()
                        }

                        val backingNext = backingIterator.next()
                        lastReturned = backingNext

                        return object : MutableMap.MutableEntry<K, V> {
                            override val key: K
                                get() = backingNext.key
                            override val value: V
                                get() = backingNext.value

                            override fun setValue(newValue: V): V = modifyMap {
                                return@modifyMap put(key, newValue) ?: throw NoSuchElementException()
                            }
                        }
                    }

                    override fun remove() {
                        modifyMap {
                            if (lastReturned == null) {
                                throw IllegalStateException()
                            }

                            remove(lastReturned!!.key) ?: throw IllegalStateException()
                        }

                        ourMap = backingFlow.value  // we modified the map, so we need to update our reference
                    }
                }
            }
        }
}
