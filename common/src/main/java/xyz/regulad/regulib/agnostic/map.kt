package xyz.regulad.regulib.agnostic

import android.os.Build
import java.util.*

fun <K : Comparable<K>?, V> comparingByKey(): Comparator<Map.Entry<K, V>> {
    return Comparator { c1: Map.Entry<K, V>, c2: Map.Entry<K, V> -> c1.key!!.compareTo(c2.key) }
}

fun <K, V : Comparable<V>?> comparingByValue(): Comparator<Map.Entry<K, V>> {
    return java.util.Comparator { c1: Map.Entry<K, V>, c2: Map.Entry<K, V> -> c1.value!!.compareTo(c2.value) }
}

fun <K, V> comparingByKey(cmp: Comparator<in K>): Comparator<Map.Entry<K, V>> {
    Objects.requireNonNull(cmp)
    return java.util.Comparator { c1: Map.Entry<K, V>, c2: Map.Entry<K, V> -> cmp.compare(c1.key, c2.key) }
}

fun <K, V> comparingByValue(cmp: Comparator<in V>): Comparator<Map.Entry<K, V>> {
    Objects.requireNonNull(cmp)
    return Comparator { c1: Map.Entry<K, V>, c2: Map.Entry<K, V> -> cmp.compare(c1.value, c2.value) }
}

fun <K, V> MutableMap<K, V>.versionAgnosticGetOrDefault(key: K, defaultValue: V): V {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.getOrDefault(key, defaultValue)
    } else {
        this[key] ?: defaultValue
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticForEach(action: (K, V) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.forEach(action)
    } else {
        for ((key, value) in this) {
            action(key, value)
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticReplaceAll(function: (K, V) -> V) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.replaceAll(function)
    } else {
        for ((key, value) in this) {
            this[key] = function(key, value)
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticPutIfAbsent(key: K, value: V): V? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.putIfAbsent(key, value)
    } else {
        val existingValue = this[key]
        if (existingValue == null) {
            this[key] = value
        }
        return existingValue
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticRemove(key: K, value: V): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.remove(key, value)
    } else {
        val existingValue = this[key]
        if (existingValue == value) {
            this.remove(key)
            return true
        } else {
            return false
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticReplace(key: K, oldValue: V, newValue: V): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.replace(key, oldValue, newValue)
    } else {
        val existingValue = this[key]
        if (existingValue == oldValue) {
            this[key] = newValue
            return true
        } else {
            return false
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticReplace(key: K, value: V): V? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.replace(key, value)
    } else {
        val existingValue = this[key]
        if (existingValue != null) {
            this[key] = value
        }
        return existingValue
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticComputeIfAbsent(key: K, mappingFunction: (K) -> V): V {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.computeIfAbsent(key, mappingFunction)
    } else {
        val value = this[key]
        if (value == null) {
            val newValue = mappingFunction(key)
            this[key] = newValue
            return newValue
        } else {
            return value
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticMerge(key: K, value: V & Any, remappingFunction: (V, V) -> V): V? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.merge(key, value, remappingFunction)
    } else {
        val existingValue = this[key]
        if (existingValue == null) {
            this[key] = value
            return value
        } else {
            val newValue = remappingFunction(existingValue, value)
            this[key] = newValue
            return newValue
        }
    }
}

fun <K, V> MutableMap<K, V>.versionAgnosticComputeIfPresent(key: K, remappingFunction: (K, V) -> V): V? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.computeIfPresent(key, remappingFunction)
    } else {
        val value = this[key]
        if (value != null) {
            val newValue = remappingFunction(key, value)
            this[key] = newValue
            return newValue
        } else {
            return null
        }
    }
}
