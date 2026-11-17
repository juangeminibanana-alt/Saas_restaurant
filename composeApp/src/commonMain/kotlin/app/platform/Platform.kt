package app.platform

import androidx.compose.runtime.Composable

/** Short platform id: "web", "desktop", "android" or "ios". */
expect fun platformName(): String

/** Route the app should open with (web: location.hash without '#'; others "/"). */
expect fun initialRouteFromPlatform(): String

/** Mirrors the current route into the platform (web: location.hash; others no-op). */
expect fun pushRouteToPlatform(route: String)

/** Base URL of the backend API for this platform. */
expect fun defaultApiBaseUrl(): String

/** Opens a URL outside the app (browser tab / system browser). */
expect fun openExternalUrl(url: String)

/** Dirección del servidor guardada por la persona en este dispositivo (null = usar la predeterminada). */
expect fun savedApiBaseUrl(): String?

/** Guarda (o borra con null) la dirección del servidor para este dispositivo. */
expect fun saveApiBaseUrl(url: String?)

/** Botón/gesto "atrás" del sistema (Android). En las demás plataformas no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Tipo de aviso: cada uno tiene su propio sonido. */
enum class NotifyKind { NEW_ORDER, ORDER_READY, ORDER_UPDATE }

/** Aviso con sonido propio y notificación del sistema (Android). En las demás plataformas no hace nada todavía. */
expect fun notifyUser(kind: NotifyKind, title: String, body: String, dedupKey: String? = null)

/** Mantiene (o detiene, con token nulo) la escucha de pedidos nuevos en segundo plano para el personal. */
expect fun syncStaffAlerts(baseUrl: String?, token: String?, role: String?)

/** Sigue (o deja de seguir, con orderId nulo) el pedido en curso del comensal en segundo plano. */
expect fun syncOrderAlerts(baseUrl: String?, orderId: String?, orderNumber: String?)
