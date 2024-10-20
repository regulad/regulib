package xyz.regulad.regulib

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Suspends until the state is true. If the state is already true, this function returns immediately.
 */
suspend fun StateFlow<Boolean>.waitForTrue() {
    if (!value) {
        // a race condition could occur if the value changes between the check and the collect, but this is EXTREMELY unlikely and is as such not handled here
        first { it }
    }
}
