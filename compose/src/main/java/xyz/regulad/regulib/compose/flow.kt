package xyz.regulad.regulib.compose

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Collects a flow into a list and returns the state of the collected items.
 *
 * @param keys the keys to remember the state by
 * @returns a pair of the state of the collected items and a state of whether the flow has finished. Hot flows will never finish, but the list will be updated as new items are emitted.
 */
@Composable
fun <T> Flow<T>.produceState(vararg keys: Any?): Pair<List<T>, Boolean> {
    var flowFinished by remember(*keys) { mutableStateOf(false) }

    val itemState by produceState(initialValue = emptyList<T>(), *keys) {
        flowFinished = false
        collect { item ->
            value += item
        }
        flowFinished = true
    }

    return itemState to flowFinished
}

/**
 * Returns the first item that matches the predicate, and null otherwise.
 *
 * @param keys the keys to remember the state by
 * @param predicate the predicate to match the item against
 * @returns the first item that matches the predicate, and null otherwise
 */
@Composable
fun <T> Flow<T>.firstState(vararg keys: Any?, predicate: (T) -> Boolean): T? {
    val firstItem by produceState<T?>(null, *keys) {
        value = first(predicate)
    }

    return firstItem
}
