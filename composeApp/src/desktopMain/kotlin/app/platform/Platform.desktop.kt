package app.platform

import java.awt.Desktop
import java.net.URI

actual fun platformName(): String = "desktop"

actual fun initialRouteFromPlatform(): String = "/"

actual fun pushRouteToPlatform(route: String) {
}

actual fun defaultApiBaseUrl(): String =
    System.getenv("API_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://localhost:8080"

actual fun openExternalUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

private var apiBaseOverride: String? = null

actual fun savedApiBaseUrl(): String? = apiBaseOverride

actual fun saveApiBaseUrl(url: String?) {
    apiBaseOverride = url
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

actual fun notifyUser(kind: NotifyKind, title: String, body: String) {
}
