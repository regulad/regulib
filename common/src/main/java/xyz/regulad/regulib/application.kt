package xyz.regulad.regulib

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.pm.ApplicationInfo

fun ApplicationInfo.isDebuggable(): Boolean {
    return (this.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/**
 * Returns true if the service is running, false otherwise. Only works for services that are part of the same app.
 */
fun Service.isRunning(): Boolean {
    val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in @Suppress("DEPRECATION") manager.getRunningServices(Integer.MAX_VALUE)) { // as of android O, only returns true for our own services, which is fine
        if (this::class.java.name == service.service.className) {
            return true
        }
    }
    return false
}
