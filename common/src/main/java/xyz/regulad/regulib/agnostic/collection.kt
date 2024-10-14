package xyz.regulad.regulib.agnostic

import android.os.Build

fun <T> MutableCollection<T>.versionAgnosticRemoveIf(predicate: (T) -> Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.removeIf(predicate)
    } else {
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
            }
        }
    }
}
