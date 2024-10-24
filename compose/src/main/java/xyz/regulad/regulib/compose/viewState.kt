package xyz.regulad.regulib.compose

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/**
 * Adding this composable to your composition keeps the screen on while the composable is in the composition.
 */
@Composable
@Suppress("unused")
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}

/**
 * Set the screen brightness while the composable is in the composition.
 */
@Composable
@Suppress("unused")
fun WithBrightness(brightness: Float) {
    val context = LocalContext.current
    val activity = (context as? Activity) ?: return

    DisposableEffect(brightness) {
        val attrs = activity.window.attributes
        attrs.screenBrightness = brightness
        activity.window.attributes = attrs

        onDispose {
            val attrs2 = activity.window.attributes
            attrs2.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = attrs2
        }
    }
}

/**
 * Set the desired orientation for the activity while the composable is in the composition.
 *
 * Note: If the requestedOrientation is further modified while this element is in the composition, the original orientation will be restored when the composable is removed. Be wary of this race condition.
 */
@Composable
@Suppress("unused")
fun WithDesiredOrientation(desiredOrientation: Int) {
    val context = LocalContext.current
    val activity = (context as? Activity) ?: return

    DisposableEffect(Unit) {
        val oldOrientation = activity.requestedOrientation
        activity.requestedOrientation = desiredOrientation
        onDispose {
            activity.requestedOrientation = oldOrientation
        }
    }
}

private val immersiveFullscreenContentComposablesInComposition = MutableStateFlow(0)

/**
 * Show a fullscreen content that hides the system UI (status bar, navigation bar) while the composable is active.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Composable
@Suppress("unused")
fun ImmersiveFullscreenContent() {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return // just wait for the activity to be available

    val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
    val windowInsetsControllerCompat = WindowInsetsControllerCompat(window, window.decorView)

    DisposableEffect(Unit) {
        immersiveFullscreenContentComposablesInComposition.value++
        onDispose {
            immersiveFullscreenContentComposablesInComposition.value--
        }
    }

    fun hideUI() {
        if (!windowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())) { // unreliable, sometimes will return false when the IME is visible
            windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun showUI() {
        windowInsetsControllerCompat.show(WindowInsetsCompat.Type.systemBars())
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            hideUI()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        windowInsetsControllerCompat.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        WindowCompat.setDecorFitsSystemWindows(window, false)

        hideUI()

        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            showUI()
        }
    }
}

/**
 * Returns a [State] that represents the immersibility of the context.
 *
 * This only respects the system bars changed by [ImmersiveFullscreenContent].
 */
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun rememberContextIsImmersive(): State<Boolean> {
    val numberOfImmersiveComposables by immersiveFullscreenContentComposablesInComposition.collectAsState()
    return remember {
        derivedStateOf {
            numberOfImmersiveComposables > 0
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Preview
@Composable
private fun ImmersiveFullscreenContentPreview() {
    val durationSinceComposition by rememberDurationSinceComposition()

    if (durationSinceComposition == null) return

    val didSetupImmersion = durationSinceComposition!! >= 10.seconds

    if (didSetupImmersion) {
        ImmersiveFullscreenContent()
    }

    val systemBarsVisible by rememberContextIsImmersive()

    Scaffold(
        Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            Modifier.navigationAwarePadding(innerPadding)
        ) {
            Text("System bars visible: $systemBarsVisible")
            Text("Duration since composition: $durationSinceComposition")
            Text("Did setup immersion: $didSetupImmersion")
        }
    }
}
