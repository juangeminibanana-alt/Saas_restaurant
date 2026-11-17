package app.platform

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
