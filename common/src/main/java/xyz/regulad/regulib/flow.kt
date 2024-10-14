package xyz.regulad.regulib

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Suspends until the state is true. If the state is already true, this function returns immediately.
 */
suspend fun StateFlow<Boolean>.waitForTrue() {
    if (!value) {
        first { it }
    }
}
