package xyz.regulad.regulib.wifi

import android.Manifest
import android.os.Build


/**
 * A version-agnostic set containing all the permissions required to use the WiFi APIs.
 */
@Suppress("unused")
val RUNTIME_REQUIRED_WIFI_PERMISSIONS = setOfNotNull(
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.CHANGE_WIFI_STATE,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    },
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        null
    } else {
        Manifest.permission.ACCESS_COARSE_LOCATION
    }
)
