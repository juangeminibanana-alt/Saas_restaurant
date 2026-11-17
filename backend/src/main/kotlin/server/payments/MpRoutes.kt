package server.payments

import app.shared.api.ApiError
import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.model.MpConnectionStatus
import app.shared.model.Role
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import server.auth.authenticated
import server.auth.user
import server.config.AppConfig
import server.realtime.Realtime

private val log = LoggerFactory.getLogger("payments.routes")

internal suspend fun <T> ApplicationCall.json(serializer: KSerializer<T>, value: T, status: HttpStatusCode = HttpStatusCode.OK) =
    respondText(AppJson.encodeToString(serializer, value), ContentType.Application.Json, status)

internal suspend fun ApplicationCall.error(status: HttpStatusCode, message: String, code: String = "error") =
    json(ApiError.serializer(), ApiError(message, code), status)

/** tenantId de la sesión o null (operador) ya respondiendo 403. */
internal suspend fun ApplicationCall.tenantOrForbid(): String? {
    val id = user.tenantId
    if (id.isNullOrBlank()) error(HttpStatusCode.Forbidden, "Esta sección es solo para restaurantes.", "forbidden")
    return id?.takeIf { it.isNotBlank() }
}

internal suspend fun mpStatus(tenantId: String, oauthUrl: String? = null): MpConnectionStatus {
    val conn = MpStore.getForTenant(tenantId)
    val feeBps = MpStore.tenantInfo(tenantId)?.feeBps ?: 150
    return MpConnectionStatus(
        connected = conn != null,
        mpUserId = conn?.mpUserId,
        liveMode = conn?.liveMode ?: false,
        feeBps = feeBps,
        oauthUrl = oauthUrl,
    )
}

internal fun Route.mercadoPagoRoutes() {
    authenticated(Role.OWNER, Role.MANAGER) {
        get(ApiRoutes.TENANT_MP) {
            val tenantId = call.tenantOrForbid() ?: return@get
            call.json(MpConnectionStatus.serializer(), mpStatus(tenantId))
        }

        post(ApiRoutes.TENANT_MP_CONNECT) {
            val tenantId = call.tenantOrForbid() ?: return@post
            val start = Payments.gateway.startConnect(tenantId)
            call.json(MpConnectionStatus.serializer(), mpStatus(tenantId, oauthUrl = start.oauthUrl))
        }

        post(ApiRoutes.TENANT_MP_DISCONNECT) {
            val tenantId = call.tenantOrForbid() ?: return@post
            MpStore.delete(tenantId)
            call.json(MpConnectionStatus.serializer(), mpStatus(tenantId))
        }
    }

    // Mercado Pago regresa aquí al restaurante después de autorizar la app.
    get(ApiRoutes.OAUTH_MP_CALLBACK) {
        val code = call.request.queryParameters["code"]
        val tenantId = OAuthState.verify(call.request.queryParameters["state"])
        if (Payments.demoMode || code.isNullOrBlank() || tenantId == null) {
            call.respondText(
                "No se pudo conectar la cuenta de Mercado Pago: el enlace no es válido o expiró. Vuelve al panel e inténtalo de nuevo.",
                ContentType.Text.Plain, HttpStatusCode.BadRequest,
            )
            return@get
        }
        try {
            MercadoPagoGateway.exchangeCode(tenantId, code)
        } catch (e: Exception) {
            log.warn("OAuth de Mercado Pago falló para {}: {}", tenantId, e.message)
            call.respondText(
                "Mercado Pago rechazó la conexión. Vuelve al panel e inténtalo de nuevo.",
                ContentType.Text.Plain, HttpStatusCode.BadGateway,
            )
            return@get
        }
        call.respondRedirect(AppConfig.publicBaseUrl + "/#/panel")
    }

    // Notificaciones de pago / reembolso / suscripción. Responde 200 rápido; el trabajo va a la cola.
    post(ApiRoutes.WEBHOOK_MP) {
        val raw = runCatching { call.receiveText() }.getOrDefault("")
        val body = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val q = call.request.queryParameters

        val topic = (body.str("type") ?: body.str("topic") ?: q["type"] ?: q["topic"] ?: "").lowercase()
        val dataId = q["data.id"] ?: body.obj("data").str("id") ?: q["id"] ?: body.str("id") ?: ""
        val tenantId = q["tenant"]?.takeIf { isUuid(it) }
        val action = body.str("action") ?: ""

        val signatureOk = MpWebhookSignature.isValid(
            xSignature = call.request.headers["x-signature"],
            requestId = call.request.headers["x-request-id"],
            dataId = q["data.id"] ?: dataId,
        )
        if (!signatureOk) {
            call.error(HttpStatusCode.Unauthorized, "Firma inválida.", "invalid_signature")
            return@post
        }
        if (topic.isEmpty() || dataId.isEmpty()) {
            call.respondText("""{"ok":true,"ignored":true}""", ContentType.Application.Json)
            return@post
        }

        val eventKey = listOf(topic, dataId, action.ifEmpty { "-" }, tenantId ?: "-").joinToString(":")
        val pending = try {
            MpStore.recordEvent(eventKey, tenantId, topic, dataId, raw.ifBlank { "{}" }.let { if (body == null) "{}" else it })
        } catch (e: Exception) {
            log.warn("No se pudo guardar el webhook {}: {}", eventKey, e.message)
            true
        }
        if (pending) {
            Realtime.publishQueue(
                MpWebhookWorker.QUEUE,
                buildJsonObject {
                    put("eventKey", eventKey)
                    put("topic", topic)
                    put("dataId", dataId)
                    put("tenantId", tenantId)
                    put("attempt", 0)
                }.toString(),
            )
        }
        call.respondText("""{"ok":true}""", ContentType.Application.Json)
    }
}
