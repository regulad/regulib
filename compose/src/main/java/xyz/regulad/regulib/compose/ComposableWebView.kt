package xyz.regulad.regulib.compose

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Implements compose-wise ways to use a [WebView].
 *
 * Does not currently allow any execution of [WebView] methods, but this is planned.
 *
 * @see WebView
 */
@Experimental
@Composable
fun ComposableWebView(
    url: String? = null,
    networkIsAvailable: Boolean? = null,
    stateBundle: Bundle? = null,
) {
    var lastStateBundle by rememberSaveable { mutableStateOf(stateBundle) }
    var lastUrl by rememberSaveable { mutableStateOf<String?>(null) }

    AndroidView(factory = {
        WebView(it).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }, update = { view ->
        networkIsAvailable?.let { view.setNetworkAvailable(it) }

        stateBundle?.let {
            if (lastStateBundle != it) {
                view.restoreState(it)
                lastStateBundle = it
            }
        }

        if (url != lastUrl) {
            view.loadUrl(url ?: "about:blank")
            lastUrl = url
        }
    }, onRelease = { view ->
        stateBundle?.let { view.saveState(it) }
        view.destroy()
    })
}
