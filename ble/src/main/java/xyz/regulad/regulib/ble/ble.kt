package xyz.regulad.regulib.ble

import android.Manifest
import android.os.Build

/**
 * A version-agnotic set containing all the permissions required to use the Bluetooth APIs.
 */
val RUNTIME_REQUIRED_BLUETOOTH_PERMISSIONS = setOfNotNull(
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) Manifest.permission.BLUETOOTH else null,
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) Manifest.permission.BLUETOOTH_ADMIN else null,
    if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.R) {
        Manifest.permission.ACCESS_FINE_LOCATION
    } else null,
    if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) {
        Manifest.permission.ACCESS_COARSE_LOCATION
    } else null,
    if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) {
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_ADVERTISE else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else null
)
