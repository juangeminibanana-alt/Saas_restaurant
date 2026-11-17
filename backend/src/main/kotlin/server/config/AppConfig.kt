package server.config

import java.io.File

/** Configuración del servidor: todo sale de variables de entorno con defaults de desarrollo. */
object AppConfig {
    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    private fun env(name: String, default: String): String = env(name) ?: default

    val port: Int = env("PORT", "8080").toIntOrNull() ?: 8080

    val databaseUrl: String = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/saas")
    val dbAppUser: String = env("DB_APP_USER", "saas_app")
    val dbAppPassword: String = env("DB_APP_PASSWORD", "saas_app_dev")
    val dbAdminUser: String = env("DB_ADMIN_USER", "saas_admin")
    val dbAdminPassword: String = env("DB_ADMIN_PASSWORD", "saas_admin_dev")

    val redisUrl: String = env("REDIS_URL", "redis://localhost:6379")

    val jwtSecret: String = env("JWT_SECRET", "dev-secret-change-me-please-32-bytes-min")
    val jwtIssuer: String = env("JWT_ISSUER", "restaurant-saas")

    val publicBaseUrl: String = env("PUBLIC_BASE_URL", "http://localhost:8080").trimEnd('/')

    /** Carpeta con el bundle web (Wasm). Si no se define, se usa el build de desarrollo cuando existe. */
    val webDist: String? = env("WEB_DIST") ?: listOf(
        "composeApp/build/dist/wasmJs/developmentExecutable",
        "../composeApp/build/dist/wasmJs/developmentExecutable",
        "composeApp/build/dist/wasmJs/productionExecutable",
        "../composeApp/build/dist/wasmJs/productionExecutable",
    ).firstOrNull { File(it).isDirectory }

    // Mercado Pago (marketplace). Ausentes ⇒ modo demostración.
    val mpClientId: String? = env("MP_CLIENT_ID")
    val mpClientSecret: String? = env("MP_CLIENT_SECRET")
    val mpAccessToken: String? = env("MP_ACCESS_TOKEN")
    val mpWebhookSecret: String? = env("MP_WEBHOOK_SECRET")
    val mpRedirectUri: String = env("MP_REDIRECT_URI", "$publicBaseUrl/api/oauth/mercadopago/callback")

    // Lalamove (reparto). Ausentes ⇒ mock.
    val lalamoveApiKey: String? = env("LALAMOVE_API_KEY")
    val lalamoveApiSecret: String? = env("LALAMOVE_API_SECRET")
    val lalamoveBaseUrl: String = env("LALAMOVE_BASE_URL", "https://rest.sandbox.lalamove.com")

    /** Siembra datos de demostración cuando la tabla de tenants está vacía. */
    val seedDemo: Boolean = env("SEED_DEMO", "true").lowercase() != "false"
}
