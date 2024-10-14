package xyz.regulad.regulib.compose

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow

/**
 * Collects a cold flow into a state.
 *
 * @returns a pair of the state of the collected items and a state of whether the flow has finished. Hot flows will never finish, but the list will be updated as new items are emitted.
 */
@Composable
fun <T> Flow<T>.produceState(): Pair<List<T>, Boolean> {
    var flowFinished by remember { mutableStateOf(false) }

    val itemState by produceState(initialValue = emptyList<T>()) {
        flowFinished = false
        collect { item ->
            value += item
        }
        flowFinished = true
    }

    return itemState to flowFinished
}
