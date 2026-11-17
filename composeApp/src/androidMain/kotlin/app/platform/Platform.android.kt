package app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Holds the application context so platform helpers can start activities. */
object AndroidPlatform {
    @Volatile
    var appContext: Context? = null
}

actual fun platformName(): String = "android"

actual fun initialRouteFromPlatform(): String = "/"

actual fun pushRouteToPlatform(route: String) {
}

// 10.0.2.2 is the host machine as seen from the Android emulator.
actual fun defaultApiBaseUrl(): String = "http://10.0.2.2:8080"

actual fun openExternalUrl(url: String) {
    val context = AndroidPlatform.appContext ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
