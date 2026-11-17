package app.platform

import kotlinx.browser.window

actual fun platformName(): String = "web"

actual fun initialRouteFromPlatform(): String =
    window.location.hash.removePrefix("#").ifBlank { "/" }

actual fun pushRouteToPlatform(route: String) {
    if (window.location.hash.removePrefix("#") != route) {
        window.location.hash = route
    }
}

actual fun defaultApiBaseUrl(): String = window.location.origin

actual fun openExternalUrl(url: String) {
    window.open(url, "_blank")
}

private var apiBaseOverride: String? = null

actual fun savedApiBaseUrl(): String? = apiBaseOverride

actual fun saveApiBaseUrl(url: String?) {
    apiBaseOverride = url
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
