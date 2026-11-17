package server.integrations

import app.shared.api.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import server.config.AppConfig
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong
import kotlin.random.Random

// ───────────────────────── Contrato de reparto ─────────────────────────

data class DeliveryStop(
    val lat: Double,
    val lng: Double,
    val address: String,
    val contactName: String = "",
    val contactPhone: String = "",
)

data class DeliveryQuote(
    val quotationId: String,
    val priceCents: Long,
    val currency: String = "MXN",
    val etaMinutes: Int = 30,
    val expiresAt: Long = 0,
    val stopIds: List<String> = emptyList(),   // [recolección, entrega]
)

enum class DeliveryStatus { ASSIGNING, ON_GOING, PICKED_UP, COMPLETED, CANCELED, REJECTED, EXPIRED }

data class DeliveryJob(
    val id: String,
    val provider: String,
    val status: DeliveryStatus,
    val priceCents: Long = 0,
    val trackingUrl: String? = null,
    val driverId: String? = null,
)

/** Proveedor de reparto de última milla. */
interface DeliveryProvider {
    val name: String
    /** "mock" | "sandbox" | "live" */
    val mode: String
    suspend fun quote(pickup: DeliveryStop, dropoff: DeliveryStop): DeliveryQuote
    suspend fun createDelivery(quote: DeliveryQuote, pickup: DeliveryStop, dropoff: DeliveryStop, orderRef: String): DeliveryJob
    suspend fun cancel(deliveryId: String): Boolean
    suspend fun status(deliveryId: String): DeliveryJob
}

/** Elige Lalamove cuando hay llaves; si no, el mock. */
object Deliveries {
    val provider: DeliveryProvider by lazy {
        val key = AppConfig.lalamoveApiKey
        val secret = AppConfig.lalamoveApiSecret
        if (!key.isNullOrBlank() && !secret.isNullOrBlank()) LalamoveClient(key, secret, AppConfig.lalamoveBaseUrl) else MockDeliveryProvider
    }
}

// ───────────────────────── Mock ─────────────────────────

/** Sin red: tarifas y estados simulados para demostraciones. */
object MockDeliveryProvider : DeliveryProvider {
    override val name = "mock"
    override val mode = "mock"

    override suspend fun quote(pickup: DeliveryStop, dropoff: DeliveryStop): DeliveryQuote {
        val km = haversineKm(pickup, dropoff)
        return DeliveryQuote(
            quotationId = "mock-q-" + UUID.randomUUID().toString().take(8),
            priceCents = (3500 + (km * 900).roundToLong()).coerceAtMost(25_000),
            etaMinutes = (15 + km * 4).toInt().coerceAtMost(90),
            expiresAt = System.currentTimeMillis() + 5 * 60_000,
            stopIds = listOf("mock-stop-1", "mock-stop-2"),
        )
    }

    override suspend fun createDelivery(quote: DeliveryQuote, pickup: DeliveryStop, dropoff: DeliveryStop, orderRef: String): DeliveryJob =
        DeliveryJob(
            id = "mock-d-" + UUID.randomUUID().toString().take(8),
            provider = name,
            status = DeliveryStatus.ASSIGNING,
            priceCents = quote.priceCents,
            trackingUrl = null,
        )

    override suspend fun cancel(deliveryId: String): Boolean = true

    override suspend fun status(deliveryId: String): DeliveryJob =
        DeliveryJob(id = deliveryId, provider = name, status = DeliveryStatus.entries[Random.nextInt(0, 4)])

    private fun haversineKm(a: DeliveryStop, b: DeliveryStop): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}

// ───────────────────────── Lalamove v3 ─────────────────────────

class LalamoveException(val status: Int, message: String) : RuntimeException(message)

/**
 * Lalamove REST v3 (sandbox: https://rest.sandbox.lalamove.com, producción: https://rest.lalamove.com).
 *
 * Firma: HMAC-SHA256(secret, "<timestamp>\r\n<METHOD>\r\n<path>\r\n\r\n<body>") en hex minúsculas.
 * Headers: `Authorization: hmac <key>:<timestamp>:<signature>`, `Market: MX`, `Request-ID: <uuid>`.
 *
 *  - POST   /v3/quotations   {"data":{"serviceType","language","stops":[{"coordinates":{"lat","lng"},"address"}]}}
 *  - POST   /v3/orders       {"data":{"quotationId","sender":{"stopId","name","phone"},"recipients":[{"stopId","name","phone","remarks"}]}}
 *  - GET    /v3/orders/{id}
 *  - DELETE /v3/orders/{id}
 */
class LalamoveClient(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String,
    private val market: String = "MX",
    private val serviceType: String = "MOTORCYCLE",
) : DeliveryProvider {
    override val name = "lalamove"
    override val mode: String get() = if (baseUrl.contains("sandbox")) "sandbox" else "live"

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000 }
        }
    }

    internal fun sign(timestamp: String, method: String, path: String, body: String): String {
        val raw = "$timestamp\r\n$method\r\n$path\r\n\r\n$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private suspend fun call(method: HttpMethod, path: String, body: JsonObject? = null): JsonElement {
        val timestamp = System.currentTimeMillis().toString()
        val bodyText = body?.toString() ?: ""
        val signature = sign(timestamp, method.value, path, bodyText)
        val resp = client.request(baseUrl.trimEnd('/') + path) {
            this.method = method
            header("Authorization", "hmac $apiKey:$timestamp:$signature")
            header("Market", market)
            header("Request-ID", UUID.randomUUID().toString())
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(bodyText)
            }
        }
        val text = resp.bodyAsText()
        if (resp.status.value !in 200..299) throw LalamoveException(resp.status.value, "Lalamove ${method.value} $path -> ${resp.status.value}: ${text.take(300)}")
        return runCatching { AppJson.parseToJsonElement(text) }.getOrDefault(JsonNull)
    }

    private fun JsonElement?.at(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement?.text(key: String): String? = (at(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    override suspend fun quote(pickup: DeliveryStop, dropoff: DeliveryStop): DeliveryQuote {
        val body = buildJsonObject {
            putJsonObject("data") {
                put("serviceType", serviceType)
                put("language", "es_MX")
                putJsonArray("stops") {
                    for (s in listOf(pickup, dropoff)) add(buildJsonObject {
                        putJsonObject("coordinates") { put("lat", s.lat.toString()); put("lng", s.lng.toString()) }
                        put("address", s.address)
                    })
                }
            }
        }
        val data = call(HttpMethod.Post, "/v3/quotations", body).at("data")
        val total = data.at("priceBreakdown").text("total")?.toDoubleOrNull() ?: 0.0
        val stops = (data.at("stops") as? JsonArray)?.mapNotNull { it.text("stopId") } ?: emptyList()
        return DeliveryQuote(
            quotationId = data.text("quotationId") ?: throw LalamoveException(502, "Cotización sin quotationId"),
            priceCents = (total * 100).roundToLong(),
            currency = data.at("priceBreakdown").text("currency") ?: "MXN",
            expiresAt = System.currentTimeMillis() + 5 * 60_000,
            stopIds = stops,
        )
    }

    override suspend fun createDelivery(quote: DeliveryQuote, pickup: DeliveryStop, dropoff: DeliveryStop, orderRef: String): DeliveryJob {
        val body = buildJsonObject {
            putJsonObject("data") {
                put("quotationId", quote.quotationId)
                putJsonObject("sender") {
                    put("stopId", quote.stopIds.getOrNull(0))
                    put("name", pickup.contactName)
                    put("phone", pickup.contactPhone)
                }
                putJsonArray("recipients") {
                    add(buildJsonObject {
                        put("stopId", quote.stopIds.getOrNull(1))
                        put("name", dropoff.contactName)
                        put("phone", dropoff.contactPhone)
                        put("remarks", "Pedido $orderRef")
                    })
                }
                put("isPODEnabled", false)
                putJsonObject("metadata") { put("orderRef", orderRef) }
            }
        }
        return jobFrom(call(HttpMethod.Post, "/v3/orders", body).at("data"), quote.priceCents)
    }

    override suspend fun cancel(deliveryId: String): Boolean {
        call(HttpMethod.Delete, "/v3/orders/$deliveryId")
        return true
    }

    override suspend fun status(deliveryId: String): DeliveryJob = jobFrom(call(HttpMethod.Get, "/v3/orders/$deliveryId").at("data"), 0)

    private fun jobFrom(data: JsonElement?, fallbackPrice: Long): DeliveryJob {
        val total = data.at("priceBreakdown").text("total")?.toDoubleOrNull()
        return DeliveryJob(
            id = data.text("orderId") ?: throw LalamoveException(502, "Respuesta sin orderId"),
            provider = name,
            status = DeliveryStatus.entries.firstOrNull { it.name == data.text("status") } ?: DeliveryStatus.ASSIGNING,
            priceCents = total?.let { (it * 100).roundToLong() } ?: fallbackPrice,
            trackingUrl = data.text("shareLink"),
            driverId = data.text("driverId")?.takeIf { it.isNotBlank() },
        )
    }
}
