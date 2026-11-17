package server.integrations

import app.shared.api.AppJson
import app.shared.model.IntegrationProvider
import app.shared.model.Menu
import app.shared.model.OrderChannel
import app.shared.model.OrderLine
import app.shared.model.OrderType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong

/** Pedido recibido de un marketplace, ya normalizado a los modelos del producto. */
data class IncomingMarketplaceOrder(
    val externalId: String,
    val channel: OrderChannel,
    val customerName: String,
    val type: OrderType = OrderType.DELIVERY,
    val lines: List<OrderLine> = emptyList(),
    val notes: String = "",
)

/** Credenciales que entrega cada marketplace al aprobar la integración. */
data class MarketplaceCredentials(
    val clientId: String,
    val clientSecret: String,
    val storeId: String,
    val webhookSecret: String = clientSecret,
)

/**
 * Conector de marketplace de comida. El flujo es el mismo en todos:
 * webhook entrante → verifyWebhook → parseIncomingOrder → OrderService.createExternalOrder (entra a cocina)
 * → acceptOrder → markReady cuando cocina lo termina. syncMenu publica el menú del restaurante.
 */
interface MarketplaceConnector {
    val provider: IntegrationProvider
    val channel: OrderChannel
    /** true cuando hay credenciales reales cargadas. */
    val configured: Boolean
    fun verifyWebhook(headers: Map<String, String>, rawBody: String): Boolean
    fun parseIncomingOrder(rawBody: String): IncomingMarketplaceOrder?
    suspend fun acceptOrder(externalId: String): Boolean
    suspend fun markReady(externalId: String): Boolean
    suspend fun syncMenu(menu: Menu): Boolean
}

object Marketplaces {
    private val real: Map<IntegrationProvider, MarketplaceConnector> = listOf(
        UberEatsConnector(null), RappiConnector(null), DidiFoodConnector(null),
    ).associateBy { it.provider }

    fun channelOf(provider: IntegrationProvider): OrderChannel? = when (provider) {
        IntegrationProvider.UBER_EATS -> OrderChannel.UBER_EATS
        IntegrationProvider.RAPPI -> OrderChannel.RAPPI
        IntegrationProvider.DIDI_FOOD -> OrderChannel.DIDI_FOOD
        else -> null
    }

    /** Conector real si tiene credenciales; si no, el mock del mismo canal. */
    fun connector(provider: IntegrationProvider): MarketplaceConnector? {
        val channel = channelOf(provider) ?: return null
        return real[provider]?.takeIf { it.configured } ?: MockMarketplaceConnector(provider, channel)
    }
}

// ───────────────────────── Utilidades ─────────────────────────

internal fun hmacHex(secret: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun safeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

private fun parse(raw: String): JsonObject? = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
private fun JsonElement?.at(key: String): JsonElement? = (this as? JsonObject)?.get(key)
private fun JsonElement?.text(key: String): String? = (at(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private fun JsonElement?.list(key: String): List<JsonElement> = (at(key) as? JsonArray) ?: emptyList()
private fun headerOf(headers: Map<String, String>, name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

/** Base de los conectores reales: sin credenciales no se llama a nadie. */
abstract class PendingCredentialsConnector(protected val credentials: MarketplaceCredentials?) : MarketplaceConnector {
    protected val log = LoggerFactory.getLogger("integrations." + javaClass.simpleName)
    override val configured: Boolean get() = credentials != null

    /** PENDIENTE DE CREDENCIALES: aquí va la llamada HTTP real descrita en el KDoc de cada conector. */
    protected fun pending(operation: String): Boolean {
        log.info("{} {}: pendiente de credenciales del marketplace; no se envió nada.", provider, operation)
        return false
    }
}

// ───────────────────────── Uber Eats ─────────────────────────

/**
 * Uber Eats Marketplace API — PENDIENTE DE CREDENCIALES (requiere app aprobada por Uber y store_id).
 *
 *  - Token: POST https://auth.uber.com/oauth/v2/token (grant_type=client_credentials, scope "eats.store eats.order").
 *  - Webhook `orders.notification`: header `X-Uber-Signature` = HMAC-SHA256 hex del cuerpo con el client secret.
 *    El cuerpo trae `meta.resource_id`; el pedido completo se lee con GET /v2/eats/order/{order_id}.
 *  - Aceptar: POST /v1/eats/orders/{order_id}/accept_pos_order   {"reason":"accepted"}
 *  - Listo:   POST /v1/eats/orders/{order_id}/restaurantdelivery/status   {"status":"ready_for_pickup"}
 *  - Menú:    PUT  /v2/eats/stores/{store_id}/menus   {"menus":[],"categories":[],"items":[],"modifier_groups":[]}
 */
class UberEatsConnector(credentials: MarketplaceCredentials?) : PendingCredentialsConnector(credentials) {
    override val provider = IntegrationProvider.UBER_EATS
    override val channel = OrderChannel.UBER_EATS

    override fun verifyWebhook(headers: Map<String, String>, rawBody: String): Boolean {
        val secret = credentials?.webhookSecret ?: return false
        val signature = headerOf(headers, "X-Uber-Signature") ?: return false
        return safeEquals(hmacHex(secret, rawBody), signature.lowercase())
    }

    /** Recibe el JSON de GET /v2/eats/order/{id}: { id, eater:{first_name}, cart:{items:[{id,title,quantity,price:{unit_price:{amount}}}]} }. */
    override fun parseIncomingOrder(rawBody: String): IncomingMarketplaceOrder? {
        val o = parse(rawBody) ?: return null
        val id = o.text("id") ?: return null
        val lines = o.at("cart").list("items").mapNotNull { item ->
            OrderLine(
                itemId = item.text("external_data") ?: item.text("id") ?: return@mapNotNull null,
                name = item.text("title") ?: "Platillo",
                quantity = item.text("quantity")?.toIntOrNull() ?: 1,
                unitPriceCents = item.at("price").at("unit_price").text("amount")?.toLongOrNull() ?: 0,
            )
        }
        return IncomingMarketplaceOrder(id, channel, o.at("eater").text("first_name") ?: "Cliente Uber Eats", lines = lines)
    }

    override suspend fun acceptOrder(externalId: String): Boolean = pending("acceptOrder($externalId)")
    override suspend fun markReady(externalId: String): Boolean = pending("markReady($externalId)")
    override suspend fun syncMenu(menu: Menu): Boolean = pending("syncMenu(${menu.items.size} platillos)")
}

// ───────────────────────── Rappi ─────────────────────────

/**
 * Rappi Restaurants Integrations API — PENDIENTE DE CREDENCIALES (client_id/secret y store_id de Rappi Aliados).
 *
 *  - Token: POST https://rests-integrations.auth0.com/oauth/token
 *           {"client_id","client_secret","audience":"https://int-public-api-v2/api","grant_type":"client_credentials"}
 *           y se envía en el header `x-authorization: bearer <token>`.
 *  - Base MX: https://microservices.dev.rappi.com/api/v2/restaurants-integrations-public-api (sandbox).
 *  - Webhook NEW_ORDER: header `Rappi-Signature: t=<ts>,sign=<hmac>` con HMAC-SHA256 hex de "<ts>.<cuerpo>".
 *  - Aceptar: PUT  /orders/{orderId}/take/{cookingTime}
 *  - Listo:   POST /orders/{orderId}/ready-for-pickup
 *  - Menú:    POST /menu   {"storeId","items":[{"name","description","price","sku","category":{...},"children":[...]}]}
 */
class RappiConnector(credentials: MarketplaceCredentials?) : PendingCredentialsConnector(credentials) {
    override val provider = IntegrationProvider.RAPPI
    override val channel = OrderChannel.RAPPI

    override fun verifyWebhook(headers: Map<String, String>, rawBody: String): Boolean {
        val secret = credentials?.webhookSecret ?: return false
        val header = headerOf(headers, "Rappi-Signature") ?: return false
        val parts = header.split(',').associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val ts = parts["t"] ?: return false
        val sign = parts["sign"] ?: return false
        return safeEquals(hmacHex(secret, "$ts.$rawBody"), sign.lowercase())
    }

    /** { order_detail:{ order_id, items:[{sku,name,quantity,unit_price_with_discount}] }, customer:{first_name} } (precios en pesos). */
    override fun parseIncomingOrder(rawBody: String): IncomingMarketplaceOrder? {
        val o = parse(rawBody) ?: return null
        val detail = o.at("order_detail")
        val id = detail.text("order_id") ?: return null
        val lines = detail.list("items").mapNotNull { item ->
            val price = item.text("unit_price_with_discount")?.toDoubleOrNull() ?: item.text("price")?.toDoubleOrNull() ?: 0.0
            OrderLine(
                itemId = item.text("sku") ?: return@mapNotNull null,
                name = item.text("name") ?: "Platillo",
                quantity = item.text("quantity")?.toIntOrNull() ?: 1,
                unitPriceCents = (price * 100).roundToLong(),
                notes = item.text("comments") ?: "",
            )
        }
        return IncomingMarketplaceOrder(id, channel, o.at("customer").text("first_name") ?: "Cliente Rappi", lines = lines)
    }

    override suspend fun acceptOrder(externalId: String): Boolean = pending("acceptOrder($externalId)")
    override suspend fun markReady(externalId: String): Boolean = pending("markReady($externalId)")
    override suspend fun syncMenu(menu: Menu): Boolean = pending("syncMenu(${menu.items.size} platillos)")
}

// ───────────────────────── DiDi Food ─────────────────────────

/**
 * DiDi Food Open Platform — PENDIENTE DE CREDENCIALES (app_id/app_secret y app_shop_id que asigna DiDi al aliado).
 * La documentación es privada para aliados: las rutas de abajo son la forma esperada y deben confirmarse
 * contra el portal del aliado antes de salir a producción.
 *
 *  - Token: POST {base}/v1/auth/authtoken/get   {"app_id","app_secret","app_shop_id"}
 *  - Webhook "orderNew": el cuerpo trae `app_id`, `timestamp`, `data` y `sign` (HMAC-SHA256 hex de "<timestamp><data>" con app_secret).
 *  - Aceptar: POST {base}/v1/order/order/confirm   {"auth_token","order_id"}
 *  - Listo:   POST {base}/v1/order/order/ready     {"auth_token","order_id"}
 *  - Menú:    POST {base}/v1/item/item/upload      {"auth_token","categories":[...],"items":[...]}
 */
class DidiFoodConnector(credentials: MarketplaceCredentials?) : PendingCredentialsConnector(credentials) {
    override val provider = IntegrationProvider.DIDI_FOOD
    override val channel = OrderChannel.DIDI_FOOD

    override fun verifyWebhook(headers: Map<String, String>, rawBody: String): Boolean {
        val secret = credentials?.webhookSecret ?: return false
        val o = parse(rawBody) ?: return false
        val sign = o.text("sign") ?: return false
        val data = o.at("data")?.toString() ?: ""
        return safeEquals(hmacHex(secret, (o.text("timestamp") ?: "") + data), sign.lowercase())
    }

    /** { data:{ order_id, receive_address:{name}, order_items:[{app_item_id,name,amount,sku_price}] } } (precios en centavos). */
    override fun parseIncomingOrder(rawBody: String): IncomingMarketplaceOrder? {
        val data = parse(rawBody).at("data")
        val id = data.text("order_id") ?: return null
        val lines = data.list("order_items").mapNotNull { item ->
            OrderLine(
                itemId = item.text("app_item_id") ?: return@mapNotNull null,
                name = item.text("name") ?: "Platillo",
                quantity = item.text("amount")?.toIntOrNull() ?: 1,
                unitPriceCents = item.text("sku_price")?.toLongOrNull() ?: 0,
            )
        }
        return IncomingMarketplaceOrder(id, channel, data.at("receive_address").text("name") ?: "Cliente DiDi Food", lines = lines)
    }

    override suspend fun acceptOrder(externalId: String): Boolean = pending("acceptOrder($externalId)")
    override suspend fun markReady(externalId: String): Boolean = pending("markReady($externalId)")
    override suspend fun syncMenu(menu: Menu): Boolean = pending("syncMenu(${menu.items.size} platillos)")
}

// ───────────────────────── Mock ─────────────────────────

/**
 * Conector simulado: acepta todo y entiende un formato propio y simple
 * { externalId, customerName, items:[{itemId,name,quantity,unitPriceCents,imageUrl}] }.
 */
class MockMarketplaceConnector(
    override val provider: IntegrationProvider,
    override val channel: OrderChannel,
) : MarketplaceConnector {
    override val configured = true

    override fun verifyWebhook(headers: Map<String, String>, rawBody: String): Boolean = true

    override fun parseIncomingOrder(rawBody: String): IncomingMarketplaceOrder? {
        val o = parse(rawBody) ?: return null
        val lines = o.list("items").mapNotNull { item ->
            OrderLine(
                itemId = item.text("itemId") ?: return@mapNotNull null,
                name = item.text("name") ?: "Platillo",
                quantity = item.text("quantity")?.toIntOrNull() ?: 1,
                unitPriceCents = item.text("unitPriceCents")?.toLongOrNull() ?: 0,
                imageUrl = item.text("imageUrl"),
            )
        }
        if (lines.isEmpty()) return null
        return IncomingMarketplaceOrder(
            externalId = o.text("externalId") ?: return null,
            channel = channel,
            customerName = o.text("customerName") ?: "Cliente",
            lines = lines,
        )
    }

    override suspend fun acceptOrder(externalId: String): Boolean = true
    override suspend fun markReady(externalId: String): Boolean = true
    override suspend fun syncMenu(menu: Menu): Boolean = true
}
