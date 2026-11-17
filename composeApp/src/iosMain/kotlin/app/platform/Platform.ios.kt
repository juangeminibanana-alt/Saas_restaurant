package app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun platformName(): String = "ios"

actual fun initialRouteFromPlatform(): String = "/"

actual fun pushRouteToPlatform(route: String) {
}

actual fun defaultApiBaseUrl(): String = "http://localhost:8080"

actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

private var apiBaseOverride: String? = null

actual fun savedApiBaseUrl(): String? = apiBaseOverride

actual fun saveApiBaseUrl(url: String?) {
    apiBaseOverride = url
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

actual fun notifyUser(kind: NotifyKind, title: String, body: String, dedupKey: String?) {
}

actual fun syncStaffAlerts(baseUrl: String?, token: String?, role: String?) {
}

actual fun syncOrderAlerts(baseUrl: String?, orderId: String?, orderNumber: String?) {
}
