package xyz.regulad.regulib

/**
 * Returns a sequence that loops over the elements of this iterable indefinitely.
 */
fun <T> Iterable<T>.asLoopingSequence(): Sequence<T> = sequence {
    while (true) {
        yieldAll(this@asLoopingSequence)
    }
}
