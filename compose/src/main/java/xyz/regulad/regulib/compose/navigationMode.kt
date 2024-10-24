package xyz.regulad.regulib.compose

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext

enum class NavigationMode {
    THREE_BUTTON,
    TWO_BUTTON,
    GESTURE,
    UNKNOWN
}

@Composable
fun rememberNavigationMode(): State<NavigationMode> {
    val context = LocalContext.current
    val navigationMode = remember { mutableStateOf(getCurrentNavigationMode(context)) }

    DisposableEffect(context) {
        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                navigationMode.value = getCurrentNavigationMode(context)
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("navigation_mode"),
            false,
            contentObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    return navigationMode
}

/**
 * A [Modifier] that applies padding only when the system navigation bar is visible.
 *
 * This is useful for [Scaffold]s that do not respect the system navigation bar visibility in terms of their padding.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Suppress("unused")
fun Modifier.navigationAwarePadding(paddingValues: PaddingValues): Modifier = composed {
    val navigationMode by rememberNavigationMode()
    val systemBarsVisible by rememberContextIsImmersive()

    val shouldNotUsePadding by remember {
        derivedStateOf {
            navigationMode != NavigationMode.GESTURE && !systemBarsVisible
        }
    }

    return@composed if (!shouldNotUsePadding) {
        this.padding(paddingValues)
    } else {
        this
    }
}

private fun getCurrentNavigationMode(context: Context): NavigationMode {
    return when {
        // Android Q (API 29) and above - use system navigation mode
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            getAndroidQNavigationMode(context)
        }

        // Android P (API 28) - can detect gesture navigation on some devices
        Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> {
            getAndroidPNavigationMode(context)
        }

        // Below Android P - always three button navigation
        else -> NavigationMode.THREE_BUTTON
    }
}

private fun getAndroidQNavigationMode(context: Context): NavigationMode {
    val resources = context.resources
    val resourceId = resources.getIdentifier(
        "config_navBarInteractionMode",
        "integer",
        "android"
    )

    return if (resourceId > 0) {
        try {
            when (resources.getInteger(resourceId)) {
                0 -> NavigationMode.THREE_BUTTON
                1 -> NavigationMode.TWO_BUTTON
                2 -> NavigationMode.GESTURE
                else -> NavigationMode.UNKNOWN
            }
        } catch (e: Exception) {
            // Handle cases where the resource exists but can't be accessed
            getNavigationModeFromSystemSettings(context)
        }
    } else {
        getNavigationModeFromSystemSettings(context)
    }
}

private fun getNavigationModeFromSystemSettings(context: Context): NavigationMode {
    return try {
        val mode = Settings.Secure.getInt(
            context.contentResolver,
            "navigation_mode",
            0
        )
        when (mode) {
            0 -> NavigationMode.THREE_BUTTON
            1 -> NavigationMode.TWO_BUTTON
            2 -> NavigationMode.GESTURE
            else -> NavigationMode.UNKNOWN
        }
    } catch (e: Exception) {
        NavigationMode.UNKNOWN
    }
}

private fun getAndroidPNavigationMode(context: Context): NavigationMode {
    return try {
        // Check for presence of home button
        val hasHomeKey = ViewConfiguration.get(context).hasPermanentMenuKey()
        // Check if navigation bar is present
        val resourceId = context.resources.getIdentifier("config_showNavigationBar", "bool", "android")
        val hasNavBar = if (resourceId > 0) {
            context.resources.getBoolean(resourceId)
        } else {
            false
        }

        when {
            !hasHomeKey && hasNavBar -> NavigationMode.THREE_BUTTON
            !hasHomeKey && !hasNavBar -> NavigationMode.GESTURE
            else -> NavigationMode.THREE_BUTTON
        }
    } catch (e: Exception) {
        NavigationMode.THREE_BUTTON
    }
}
