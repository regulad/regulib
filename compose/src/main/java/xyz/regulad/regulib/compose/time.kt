package xyz.regulad.regulib.compose

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Remember a [TimeMark] that is updated at a specified [accuracy].
 *
 * Because of the frequency of updates, this composable should be used in composition with [derivedStateOf]
 */
@Composable
@Suppress("unused")
fun rememberDurationSinceComposition(accuracy: Duration = 100.milliseconds): State<Duration?> {
    val initialTimeMark by rememberCompositionTimeMark()
    val timeMarkState = remember { mutableStateOf<Duration?>(null) }

    LaunchedEffect(accuracy, initialTimeMark) {
        while (isActive && initialTimeMark != null) {
            timeMarkState.value = TimeSource.Monotonic.markNow() - initialTimeMark!!
            delay(accuracy)
        }
    }

    return timeMarkState
}

/**
 * Remember a [TimeMark] updated when the composition is first created.
 *
 * Because of the frequency of updates, this composable should be used in composition with [derivedStateOf]
 */
@Composable
private fun rememberCompositionTimeMark(): State<TimeSource.Monotonic.ValueTimeMark?> {
    val timeMarkState = remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    val timeSource = remember { TimeSource.Monotonic }

    LaunchedEffect(Unit) {
        timeMarkState.value = timeSource.markNow()
    }

    return timeMarkState
}

/**
 * Remember the current instant.
 *
 * Because of the frequency of updates, this composable should be used in composition with [derivedStateOf]
 *
 * @param accuracy The accuracy at which the instant should be updated.
 * @return A state that contains the current instant. (UTC)
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
@Suppress("unused")
fun rememberCurrentInstant(accuracy: Duration = 100.milliseconds): State<Instant?> {
    val instantState = remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(accuracy) {
        while (isActive) {
            instantState.value = Instant.now()
            delay(accuracy)
        }
    }

    return instantState
}

/**
 * Remember the instant at which the composition was created.
 *
 * Because of the frequency of updates, this composable should be used in composition with [derivedStateOf]
 *
 * @return A state that contains the instant at which the composition was created. (UTC)
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
@Suppress("unused")
fun rememberCompositionInstant(): State<Instant?> {
    val instantState = remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(Unit) {
        instantState.value = Instant.now()
    }

    return instantState
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
private fun TimeMarkPreview() {
    val currentTimeMark by rememberDurationSinceComposition()
    val currentTimeInstant by rememberCurrentInstant()
    val compositionInstant by rememberCompositionInstant()

    Column {
        currentTimeMark?.let {
            Text("Time elapsed since composition: $it")
        }

        currentTimeInstant?.let {
            Text("Current instant: $it")
        }

        compositionInstant?.let {
            Text("Composition instant: $it")
        }
    }
}
