package xyz.regulad.regulib

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Builds an [AlertDialog] using the given [builder] function.
 *
 * The [builder] function is an extension function on [AlertDialog.Builder].
 *
 * Example:
 * ```
 * val dialog = buildDialog {
 *    setTitle("Title")
 *    setMessage("Message")
 *    setPositiveButton("OK") { _, _ -> showToast("OK clicked") }
 *    setNegativeButton("Cancel") { _, _ -> showToast("Cancel clicked") }
 *    setNeutralButton("Ignore") { _, _ -> showToast("Ignore clicked") }
 * }
 * ```
 */
fun Context.buildDialog(builder: AlertDialog.Builder.() -> Unit): AlertDialog {
    val dialogBuilder = AlertDialog.Builder(this)
    dialogBuilder.builder()
    return dialogBuilder.create()
}

/**
 * Shows an [AlertDialog] using the given [builder] function.
 *
 * The [builder] function is an extension function on [AlertDialog.Builder].
 *
 * Example:
 * ```
 * showDialog {
 *    setTitle("Title")
 *    setMessage("Message")
 *    setPositiveButton("OK") { _, _ -> showToast("OK clicked") }
 *    setNegativeButton("Cancel") { _, _ -> showToast("Cancel clicked") }
 *    setNeutralButton("Ignore") { _, _ -> showToast("Ignore clicked") }
 * }
 * ```
 */
fun Context.showDialog(builder: AlertDialog.Builder.() -> Unit) {
    Handler(Looper.getMainLooper()).post {
        buildDialog(builder).show()
    }
}

/**
 * Shows an [AlertDialog] using the given [builder] function.
 *
 * The [builder] function is an extension function on [AlertDialog.Builder].
 *
 * Example:
 * ```
 * showDialogSuspend {
 *    setTitle("Title")
 *    setMessage("Message")
 *    setPositiveButton("OK") { _, _ -> showToast("OK clicked") }
 *    setNegativeButton("Cancel") { _, _ -> showToast("Cancel clicked") }
 *    setNeutralButton("Ignore") { _, _ -> showToast("Ignore clicked") }
 * }
 * ```
 */
suspend fun Context.showDialogSuspend(builder: AlertDialog.Builder.() -> Unit) {
    suspendCoroutine { continuation ->
        Handler(Looper.getMainLooper()).post {
            buildDialog(builder).show()
            continuation.resume(Unit)
        }
    }
}

/**
 * Shows a toast with the given message.
 *
 * A toast is a "lower third" message that appears on the screen for a short time.
 */
fun Context.showToast(message: String, length: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this@showToast, message, length).show()
}

/**
 * Shows a toast with the given message.
 *
 * A toast is a "lower third" message that appears on the screen for a short time.
 */
fun Context.showToast(@StringRes resId: Int, length: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this@showToast, resId, length).show()
}

fun Context.launchAppInfoSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/**
 * Starts a foreground service, using [Context.startForegroundService] on Android Oreo and later, and [Context.startService] on earlier versions.
 *
 * This difference in API is not documented.
 */
fun Context.versionAgnosticStartServiceForeground(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.startForegroundService(intent)
    } else {
        this.startService(intent)
    }
}
