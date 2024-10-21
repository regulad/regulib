package xyz.regulad.regulib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Transforms the value of a state flow, so long as the scope is active.
 */
inline fun <I, O> StateFlow<I>.transformState(scope: CoroutineScope, crossinline transformer: (I) -> O): StateFlow<O> {
    return map { transformer(it) }.stateIn(scope, SharingStarted.Eagerly, transformer(value))
}
