package xyz.regulad.regulib

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Suspends until the state is true. If the state is already true, this function returns immediately.
 */
suspend fun StateFlow<Boolean>.waitForTrue() {
    if (!value) {
        // a race condition could occur if the value changes between the check and the collect, but this is EXTREMELY unlikely and is as such not handled here
        first { it }
    }
}

/**
 * Combines multiple flows into a single flow. The resulting flow will emit values from all the input flows.
 *
 * If any flows are "hot" shared flows and do not close, the resulting flow will not close either.
 */
fun <T> Iterable<Flow<T>>.merge(): Flow<T> = channelFlow {
    coroutineScope {
        val jobs = map { flow ->
            launch {
                flow.collect {
                    send(it)
                }
            }
        }

        jobs.joinAll()
    }
}

/**
 * @see merge
 */
fun <T> Flow<T>.merge(vararg otherFlows: Flow<T>): Flow<T> = listOf(this, *otherFlows).merge()
