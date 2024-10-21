package xyz.regulad.regulib.compose

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Adding this composable to your composition keeps the screen on while the composable is in the composition.
 */
@Composable
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
 * Set the desired orientation for the activity while the composable is in the composition.
 *
 * Note: If the requestedOrientation is further modified while this element is in the composition, the original orientation will be restored when the composable is removed. Be wary of this race condition.
 */
@Composable
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

/**
 * Show a fullscreen content that hides the system UI (status bar, navigation bar) while the composable is active.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ImmersiveFullscreenContent() {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return // just wait for the activity to be available

    val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
    val windowInsetsControllerCompat = WindowInsetsControllerCompat(window, window.decorView)

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
