package server.payments

import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.model.Order
import app.shared.model.OrderChannel
import app.shared.model.PaymentInfo
import app.shared.model.PaymentMethod
import app.shared.model.Plan
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import server.config.AppConfig
import java.util.UUID
import kotlin.math.roundToLong

class MpApiException(val status: Int, message: String) : RuntimeException(message)

/** Cliente HTTP mínimo contra https://api.mercadopago.com (JSON como texto, sin DTOs intermedios). */
internal object MpHttp {
    const val API = "https://api.mercadopago.com"
    const val AUTH = "https://auth.mercadopago.com.mx/authorization"

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    suspend fun call(
        method: HttpMethod,
        path: String,
        token: String?,
        body: JsonObject? = null,
        idempotencyKey: String? = null,
    ): JsonElement {
        val resp = client.request(API + path) {
            this.method = method
            header(HttpHeaders.Accept, "application/json")
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (idempotencyKey != null) header("X-Idempotency-Key", idempotencyKey)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = resp.bodyAsText()
        if (resp.status.value !in 200..299) {
            throw MpApiException(resp.status.value, "Mercado Pago ${method.value} $path -> ${resp.status.value}: ${text.take(400)}")
        }
        return runCatching { AppJson.parseToJsonElement(text) }.getOrDefault(JsonNull)
    }
}

internal fun JsonElement?.str(key: String): String? {
    val v = (this as? JsonObject)?.get(key) ?: return null
    return if (v is JsonPrimitive && v !is JsonNull) v.content else null
}

internal fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

/** Centavos → pesos con dos decimales, como número JSON. */
internal fun pesos(cents: Long): JsonPrimitive = JsonPrimitive(java.math.BigDecimal.valueOf(cents, 2))

/**
 * Mercado Pago real (modelo marketplace):
 *  - el restaurante autoriza la app por OAuth ⇒ guardamos SU access_token;
 *  - cada cobro se crea con el token del restaurante ⇒ el dinero cae en su cuenta;
 *  - `marketplace_fee` es la comisión que Mercado Pago separa para la plataforma.
 */
object MercadoPagoGateway : PaymentGateway {
    private val log = LoggerFactory.getLogger("mercadopago")
    override val name = "mercadopago"

    private const val REFRESH_WINDOW_MS = 7L * 24 * 3600 * 1000

    // ── OAuth ──

    override suspend fun startConnect(tenantId: String): ConnectStart {
        val url = MpHttp.AUTH +
            "?client_id=" + AppConfig.mpClientId.orEmpty().encodeURLParameter() +
            "&response_type=code&platform_id=mp" +
            "&state=" + OAuthState.sign(tenantId).encodeURLParameter() +
            "&redirect_uri=" + AppConfig.mpRedirectUri.encodeURLParameter()
        return ConnectStart(oauthUrl = url, connectedNow = false)
    }

    /** Canjea el `code` del callback por los tokens del restaurante y los guarda. */
    suspend fun exchangeCode(tenantId: String, code: String): MpConnection {
        val body = buildJsonObject {
            put("client_id", AppConfig.mpClientId)
            put("client_secret", AppConfig.mpClientSecret)
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", AppConfig.mpRedirectUri)
            // MP_OAUTH_TEST_TOKEN=true pide credenciales de prueba del vendedor (sandbox).
            if (System.getenv("MP_OAUTH_TEST_TOKEN")?.lowercase() == "true") put("test_token", true)
        }
        val json = MpHttp.call(HttpMethod.Post, "/oauth/token", token = null, body = body)
        val conn = connectionFrom(tenantId, json, previousRefresh = null)
        MpStore.upsertAdmin(conn)
        return conn
    }

    private suspend fun refresh(conn: MpConnection): MpConnection {
        val refreshToken = conn.refreshToken ?: return conn
        val body = buildJsonObject {
            put("client_id", AppConfig.mpClientId)
            put("client_secret", AppConfig.mpClientSecret)
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }
        val json = MpHttp.call(HttpMethod.Post, "/oauth/token", token = null, body = body)
        val fresh = connectionFrom(conn.tenantId, json, previousRefresh = refreshToken)
        MpStore.upsertAdmin(fresh)
        return fresh
    }

    private fun connectionFrom(tenantId: String, json: JsonElement, previousRefresh: String?): MpConnection {
        val access = json.str("access_token") ?: throw MpApiException(502, "Respuesta OAuth sin access_token")
        val expiresIn = json.str("expires_in")?.toLongOrNull()
        return MpConnection(
            tenantId = tenantId,
            mpUserId = json.str("user_id") ?: "",
            accessToken = access,
            refreshToken = json.str("refresh_token") ?: previousRefresh,
            publicKey = json.str("public_key"),
            liveMode = json.str("live_mode")?.toBooleanStrictOrNull() ?: !access.startsWith("TEST-"),
            expiresAt = expiresIn?.let { System.currentTimeMillis() + it * 1000 },
        )
    }

    /** Conexión vigente del restaurante; renueva el token cuando está por vencer. */
    internal suspend fun sellerConnection(tenantId: String): MpConnection? {
        val conn = MpStore.getAdmin(tenantId) ?: return null
        val exp = conn.expiresAt ?: return conn
        if (exp - System.currentTimeMillis() > REFRESH_WINDOW_MS || conn.refreshToken == null) return conn
        return try {
            refresh(conn)
        } catch (e: Exception) {
            log.warn("No se pudo renovar el token de {}: {}", tenantId, e.message)
            conn
        }
    }

    // ── Checkout Pro ──

    override suspend fun createCheckout(tenantId: String, tenantSlug: String, order: Order, feeBps: Int): PaymentInfo {
        val method = order.payment?.method ?: PaymentMethod.CARD
        val feeCents = Payments.marketplaceFeeCents(order, feeBps)
        val base = PaymentInfo(method = method, marketplaceFeeCents = feeCents)
        // Sin cuenta conectada no hay a quién cobrarle: se comporta como demo (checkoutUrl null).
        val conn = sellerConnection(tenantId) ?: return base
        val tenantName = MpStore.tenantInfo(tenantId)?.name ?: tenantSlug
        val publicBase = AppConfig.publicBaseUrl
        val isPublicUrl = publicBase.startsWith("https://")
        val backUrl = "$publicBase/#/r/$tenantSlug?order=${order.id}"

        val body = buildJsonObject {
            put("items", buildJsonArray {
                for (line in order.lines) add(buildJsonObject {
                    put("id", line.itemId)
                    put("title", line.name.take(250))
                    if (line.modifiers.isNotEmpty()) put("description", line.modifiers.joinToString(", ") { it.optionName }.take(250))
                    put("quantity", line.quantity)
                    put("unit_price", pesos(line.unitPriceCents + line.modifiers.sumOf { it.priceDeltaCents }))
                    put("currency_id", "MXN")
                })
                if (order.deliveryFeeCents > 0) add(extraItem("delivery", "Envío a domicilio", order.deliveryFeeCents))
                if (order.tipCents > 0) add(extraItem("tip", "Propina", order.tipCents))
            })
            put("external_reference", order.id)
            if (feeCents > 0) put("marketplace_fee", pesos(feeCents))
            putJsonObject("back_urls") {
                put("success", backUrl)
                put("pending", backUrl)
                put("failure", backUrl)
            }
            // Mercado Pago solo acepta auto_return y notification_url con URLs públicas https.
            if (isPublicUrl) {
                put("auto_return", "approved")
                put("notification_url", publicBase + ApiRoutes.WEBHOOK_MP + "?tenant=" + tenantId)
            }
            put("statement_descriptor", statementDescriptor(tenantName))
            putJsonObject("metadata") {
                put("tenant_id", tenantId)
                put("order_id", order.id)
                put("order_number", order.number)
            }
            if (order.customerName.isNotBlank()) putJsonObject("payer") { put("name", order.customerName) }
            if (method == PaymentMethod.MP_WALLET) put("purpose", "wallet_purchase")
            paymentMethods(method)?.let { put("payment_methods", it) }
        }

        val json = MpHttp.call(HttpMethod.Post, "/checkout/preferences", conn.accessToken, body, idempotencyKey = "pref-${order.id}")
        val url = if (conn.isTestToken) json.str("sandbox_init_point") ?: json.str("init_point") else json.str("init_point")
        return base.copy(checkoutUrl = url)
    }

    private fun extraItem(id: String, title: String, cents: Long): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("quantity", 1)
        put("unit_price", pesos(cents))
        put("currency_id", "MXN")
    }

    private fun exclude(vararg types: String): JsonObject = buildJsonObject {
        putJsonArray("excluded_payment_types") { for (t in types) add(buildJsonObject { put("id", t) }) }
    }

    /** Tipos de pago de MX: credit_card, debit_card, prepaid_card, ticket (OXXO), bank_transfer (SPEI), atm. */
    private fun paymentMethods(method: PaymentMethod): JsonObject? = when (method) {
        PaymentMethod.CARD -> exclude("ticket", "bank_transfer", "atm")
        PaymentMethod.OXXO -> exclude("credit_card", "debit_card", "prepaid_card", "bank_transfer", "atm")
        PaymentMethod.SPEI -> exclude("credit_card", "debit_card", "prepaid_card", "ticket", "atm")
        PaymentMethod.MP_WALLET, PaymentMethod.PAYMENT_LINK -> null
    }

    /** Máx. 22 caracteres, solo ASCII imprimible. */
    private fun statementDescriptor(name: String): String {
        val ascii = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .trim()
        return ascii.take(22).ifBlank { "RESTAURANTE" }
    }

    // ── Pagos / reembolsos ──

    override suspend fun getPayment(tenantId: String, paymentId: String): GatewayPayment? {
        val conn = sellerConnection(tenantId) ?: return null
        val json = MpHttp.call(HttpMethod.Get, "/v1/payments/${paymentId.encodeURLParameter()}", conn.accessToken)
        return paymentFrom(json)
    }

    private fun paymentFrom(json: JsonElement): GatewayPayment? {
        val id = json.str("id") ?: return null
        val amount = json.str("transaction_amount")?.toDoubleOrNull() ?: 0.0
        return GatewayPayment(
            id = id,
            status = json.str("status") ?: "",
            externalReference = json.str("external_reference"),
            amountCents = (amount * 100).roundToLong(),
        )
    }

    override suspend fun refund(tenantId: String, order: Order): Boolean {
        // Pagos simulados y pedidos de marketplaces no movieron dinero en Mercado Pago: no hay nada que devolver aquí.
        val known = order.payment?.providerPaymentId
        if (order.channel != OrderChannel.DIRECT || known?.startsWith("sim-") == true) return true
        val conn = sellerConnection(tenantId) ?: return false
        val paymentId = known ?: findApprovedPaymentId(conn, order.id) ?: return false
        MpHttp.call(
            HttpMethod.Post, "/v1/payments/${paymentId.encodeURLParameter()}/refunds", conn.accessToken,
            body = buildJsonObject { }, idempotencyKey = "refund-${order.id}",
        )
        return true
    }

    private suspend fun findApprovedPaymentId(conn: MpConnection, orderId: String): String? {
        val json = MpHttp.call(
            HttpMethod.Get,
            "/v1/payments/search?external_reference=${orderId.encodeURLParameter()}&status=approved&sort=date_created&criteria=desc",
            conn.accessToken,
        )
        val results = (json as? JsonObject)?.get("results") as? JsonArray ?: return null
        return results.firstOrNull().str("id")
    }

    // ── Suscripción del restaurante a la plataforma (preapproval) ──

    override suspend fun createSubscriptionCheckout(tenantId: String, plan: Plan, payerEmail: String): String? {
        val platformToken = AppConfig.mpAccessToken ?: return null
        if (plan.priceCents <= 0) return null
        val body = buildJsonObject {
            put("reason", "Suscripción ${plan.name}")
            put("external_reference", tenantId)
            put("payer_email", payerEmail)
            put("back_url", AppConfig.publicBaseUrl + "/#/panel")
            put("status", "pending")
            putJsonObject("auto_recurring") {
                put("frequency", 1)
                put("frequency_type", "months")
                put("transaction_amount", pesos(plan.priceCents))
                put("currency_id", "MXN")
            }
        }
        val json = MpHttp.call(HttpMethod.Post, "/preapproval", platformToken, body, idempotencyKey = UUID.randomUUID().toString())
        json.str("id")?.let { MpStore.setPreapprovalId(tenantId, it) }
        return if (platformToken.startsWith("TEST-")) json.str("sandbox_init_point") ?: json.str("init_point") else json.str("init_point")
    }

    override suspend fun getSubscription(preapprovalId: String): GatewaySubscription? {
        val platformToken = AppConfig.mpAccessToken ?: return null
        val json = MpHttp.call(HttpMethod.Get, "/preapproval/${preapprovalId.encodeURLParameter()}", platformToken)
        val id = json.str("id") ?: return null
        return GatewaySubscription(id = id, status = json.str("status") ?: "", externalReference = json.str("external_reference"))
    }
}
