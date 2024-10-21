package xyz.regulad.regulib.wifi

import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Safely starts a local-only hotspot, and returns a [LocalOnlyHotspotReservation].
 *
 * @see [WifiManager.startLocalOnlyHotspot]
 */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun WifiManager.startLocalOnlyHotSpotSafe(
    handle: Handler? = null,
    stoppedCallback: (() -> Unit) = {}
): LocalOnlyHotspotReservation {
    val result = suspendCoroutine<Result<LocalOnlyHotspotReservation>> { continuation ->
        startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: LocalOnlyHotspotReservation) {
                continuation.resume(Result.success(reservation))
            }

            override fun onStopped() {
                stoppedCallback()
            }

            override fun onFailed(reason: Int) {
                continuation.resume(Result.failure(Exception("Failed to start local-only hotspot: $reason")))
            }
        }, handle)
    }
    return result.getOrThrow()
}
