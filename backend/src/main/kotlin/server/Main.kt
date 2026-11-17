package server

import app.shared.api.ApiError
import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import server.auth.installAuth
import server.config.AppConfig
import server.db.Db
import server.payments.Payments
import server.realtime.Realtime
import server.routes.authRoutes
import server.routes.operatorRoutes
import server.routes.publicRoutes
import server.routes.realtimeRoutes
import server.routes.tenantRoutes
import server.seed.Seed
import java.io.File

private val log = LoggerFactory.getLogger("Server")

fun main() {
    // Con EMBEDDED_DB=true el servidor trae su propia base de datos (sin Docker).
    server.db.EmbeddedDb.startIfRequested()
    embeddedServer(Netty, port = AppConfig.port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    // Infra: migraciones → seed → tiempo real.
    Db.migrate()
    if (AppConfig.seedDemo) Seed.seedIfEmpty()
    Realtime.start()
    monitor.subscribe(ApplicationStopped) { Realtime.stop() }

    install(ContentNegotiation) { json(AppJson) }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") || call.request.path().startsWith("/ws") }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.message, e.code)) }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("La solicitud no es válida.", "bad_request"))
        }
        exception<kotlinx.serialization.SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("La solicitud no es válida.", "bad_request"))
        }
        exception<Throwable> { call, e ->
            log.error("Error no controlado en {}", call.request.path(), e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Algo salió mal. Intenta de nuevo.", "internal"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            if (call.request.path().startsWith("/api")) call.respond(status, ApiError("No encontrado.", "not_found"))
        }
    }
    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 60_000
        maxFrameSize = 1L shl 20
    }
    installAuth()

    // Rutas de pagos e integraciones (Mercado Pago, webhooks, OAuth, marketplaces).
    Payments.install(this)

    routing {
        get(ApiRoutes.HEALTH) {
            call.respondText("""{"ok":true,"redis":${Realtime.redisAvailable},"paymentsDemo":${Payments.demoMode}}""", ContentType.Application.Json)
        }
        publicRoutes()
        authRoutes()
        tenantRoutes()
        operatorRoutes()
        realtimeRoutes()

        val dist = AppConfig.webDist?.let { File(it) }?.takeIf { it.isDirectory }
        if (dist != null) {
            log.info("Sirviendo la app web desde {}", dist.absolutePath)
            staticFiles("/", dist) { default("index.html") }
        } else {
            log.info("Sin bundle web (WEB_DIST); solo API")
        }
    }
}
