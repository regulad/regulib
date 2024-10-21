package xyz.regulad.regulib

import kotlinx.coroutines.sync.Semaphore

/**
 * Suspends until the semaphore is available.
 */
suspend fun Semaphore.blockUntilAvailable() {
    acquire()
    release()
}
