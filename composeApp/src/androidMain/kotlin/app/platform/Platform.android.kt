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

private const val PREFS = "app_settings"
private const val KEY_API_BASE = "api_base_url"

actual fun savedApiBaseUrl(): String? =
    AndroidPlatform.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ?.getString(KEY_API_BASE, null)?.takeIf { it.isNotBlank() }

actual fun saveApiBaseUrl(url: String?) {
    val prefs = AndroidPlatform.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
    prefs.edit().apply { if (url.isNullOrBlank()) remove(KEY_API_BASE) else putString(KEY_API_BASE, url) }.apply()
}

// La dirección guardada en Ajustes del servidor manda; si no hay, 10.0.2.2 es la computadora anfitriona
// vista desde el emulador de Android.
actual fun defaultApiBaseUrl(): String = savedApiBaseUrl() ?: "http://10.0.2.2:8080"

actual fun openExternalUrl(url: String) {
    val context = AndroidPlatform.appContext ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
