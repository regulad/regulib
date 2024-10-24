package xyz.regulad.regulib.wifi

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.net.wifi.p2p.WifiP2pConfig
import android.os.Build
import android.os.Handler
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.regulad.regulib.reflect.toKotlinBoolean
import xyz.regulad.regulib.transformState
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Safely starts a local-only hotspot, and returns a [LocalOnlyHotspotReservation].
 *
 * @see [WifiManager.startLocalOnlyHotspot]
 */
@Suppress("unused")
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

@Suppress("unused")
val WIFI_AP_STATE_DISABLING
    get() = WifiManager::class.java.getField("WIFI_AP_STATE_DISABLING").getInt(null)

@Suppress("unused")
val WIFI_AP_STATE_DISABLED
    get() = WifiManager::class.java.getField("WIFI_AP_STATE_DISABLED").getInt(null)

@Suppress("unused")
val WIFI_AP_STATE_ENABLING
    get() = WifiManager::class.java.getField("WIFI_AP_STATE_ENABLING").getInt(null)

@Suppress("unused")
val WIFI_AP_STATE_ENABLED
    get() = WifiManager::class.java.getField("WIFI_AP_STATE_ENABLED").getInt(null)

@Suppress("unused")
val WIFI_AP_STATE_FAILED
    get() = WifiManager::class.java.getField("WIFI_AP_STATE_FAILED").getInt(null)

/**
 * Note that using this API requires an additional permission, [android.Manifest.permission.WRITE_SETTINGS], which is a system-only permission.
 *
 *
 */
@Suppress("unused")
@RequiresPermission(
    allOf = [
        android.Manifest.permission.WRITE_SETTINGS,
        android.Manifest.permission.CHANGE_WIFI_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE
    ], conditional = true
)
fun WifiManager.setWifiApEnabledReflect(wifiConfiguration: WifiConfiguration?, enabled: Boolean): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // older method is available on Oreo, but marked as deprecated
        if (enabled) {
            this::class.java.getMethod("startSoftAp", WifiConfiguration::class.java)
                .invoke(this, wifiConfiguration) as java.lang.Boolean
        } else {
            this::class.java.getMethod("stopSoftAp").invoke(this) as java.lang.Boolean
        }
    } else {
        (this::class.java.getMethod("setWifiApEnabled", WifiConfiguration::class.java, java.lang.Boolean.TYPE)
            .invoke(this, wifiConfiguration, enabled) as java.lang.Boolean)
    }.toKotlinBoolean()
}

/**
 * Get the current state of the Wi-Fi Access Point.
 *
 * Returns one of the following:
 * - [WIFI_AP_STATE_DISABLING]
 * - [WIFI_AP_STATE_DISABLED]
 * - [WIFI_AP_STATE_ENABLING]
 * - [WIFI_AP_STATE_ENABLED]
 * - [WIFI_AP_STATE_FAILED]
 */
@Suppress("unused")
@RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
fun WifiManager.getWifiApStateReflect(): Int {
    return this::class.java.getMethod("getWifiApState").invoke(this) as Int
}

@Suppress("unused")
@RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
fun WifiManager.isWifiApEnabledReflect(): Boolean {
    return (this::class.java.getMethod("isWifiApEnabled").invoke(this) as java.lang.Boolean).toKotlinBoolean()
}

@Suppress("unused")
@RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
fun WifiManager.getWifiApConfigurationReflect(): WifiConfiguration? {
    return this::class.java.getMethod("getWifiApConfiguration").invoke(this) as WifiConfiguration?
}

@Suppress("unused")
@RequiresPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
fun WifiManager.setWifiApConfigurationReflect(wifiConfiguration: WifiConfiguration): Boolean {
    return (this::class.java.getMethod("setWifiApConfiguration", WifiConfiguration::class.java)
        .invoke(this, wifiConfiguration) as java.lang.Boolean).toKotlinBoolean()
}

// the other hidden method for localOnlyHotspots, startLocalOnlyHotspot, available in Android 11+, requires android.Manifest.permission.NETWORK_SETTINGS and/or android.Manifest.permission.NETWORK_SETUP_WIZARD, which are system-only

/**
 * Allows for easy use of an Android device's embedded Wi-Fi Access Point on versions below Android 11.
 *
 * For versions greater than Android 10, the [WifiP2pManagerView.createGroup] method with a [WifiP2pConfig] object is far more reliable and non-implementation-specific.
 *
 * Bare minimum example:
 *
 * ```
 * val wifiManagerApView = WifiManagerApView(application.getSystemService<WifiManager>()!!)
 * val wifiConfiguration = wifiManagerApView.getWifiApConfiguration()!! // make sure you get the current config and modify it, vendors add custom data
 *
 * wifiConfiguration.SSID = "MySSID"
 * wifiConfiguration.preSharedKey = "mySuperSecretPassword"
 *
 * wifiConfiguration.allowedKeyManagement.clear()
 * wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK) // this is required to use WPA2
 *
 * wifiConfiguration.setStaticIp("192.168.43.1", 24) // the "static ip" actually configures DHCP for the network, but static for the host
 *
 * wifiManagerApView.setWifiApConfiguration(wifiConfiguration)
 * wifiManagerApView.setWifiApEnabled(null, true) // make sure you pass null here
 * ```
 */
@DeprecatedSinceApi(Build.VERSION_CODES.R)
@Suppress("unused")
class WifiManagerApView(private val wifiManager: WifiManager) {
    companion object {
        private const val TAG = "WifiManagerView"
        private const val STATE_REFRESH_INTERVAL =
            1000L // to map the imperative to the reactive, we need to refresh the state every second to see if an out-of-band change has occurred
    }

    private val backgroundJobCoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        backgroundJobCoroutineScope.launch {
            while (isActive) {
                getWifiApState()
                delay(STATE_REFRESH_INTERVAL)
            }
        }
    }

    fun teardown() {
        backgroundJobCoroutineScope.cancel()
    }

    protected fun finalize() {
        teardown()
    }

    private val _currentWifiApConfiguration = MutableStateFlow<WifiConfiguration?>(null)

    /**
     * The current Wi-Fi Access Point configuration.
     *
     * @see setWifiApEnabled
     * @see setWifiApConfiguration
     */
    val currentWifiApConfiguration = _currentWifiApConfiguration.asStateFlow()

    @RequiresPermission(
        allOf = [
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.ACCESS_WIFI_STATE
        ], conditional = true
    )
    fun setWifiApEnabled(wifiConfiguration: WifiConfiguration?, enabled: Boolean): Boolean {
        return wifiManager.setWifiApEnabledReflect(wifiConfiguration, enabled)
    }

    /**
     * Returns one of the following:
     * - [WIFI_AP_STATE_DISABLING]
     * - [WIFI_AP_STATE_DISABLED]
     * - [WIFI_AP_STATE_ENABLING]
     * - [WIFI_AP_STATE_ENABLED]
     * - [WIFI_AP_STATE_FAILED]
     */
    private val _currentWifiApState = MutableStateFlow<Int?>(null)

    /**
     * The current state of the Wi-Fi Access Point.
     */
    val currentWifiApState = _currentWifiApState.asStateFlow()
    val isWifiApEnabled = currentWifiApState.transformState(backgroundJobCoroutineScope) { it == WIFI_AP_STATE_ENABLED }

    private fun getWifiApState(): Int {
        return wifiManager.getWifiApStateReflect().apply {
            _currentWifiApState.value = this
        }
    }

    /**
     * @see currentWifiApConfiguration
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
    fun getWifiApConfiguration(): WifiConfiguration? {
        return wifiManager.getWifiApConfigurationReflect().apply {
            // don't set the value because the type we get from get is different from the type we set
        }
    }

    @RequiresPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
    fun setWifiApConfiguration(wifiConfiguration: WifiConfiguration): Boolean {
        return wifiManager.setWifiApConfigurationReflect(wifiConfiguration).apply {
            if (this) {
                _currentWifiApConfiguration.value = wifiConfiguration
            }
        }
    }
}
