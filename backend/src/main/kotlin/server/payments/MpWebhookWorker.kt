package server.payments

import app.shared.api.AppJson
import io.ktor.server.application.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import server.orders.OrderService
import server.realtime.Realtime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Worker de webhooks: saca trabajos de la cola Redis "mp-webhooks", consulta el pago con el token del
 * restaurante y mueve el pedido (approved ⇒ PAID ⇒ cocina en tiempo real; refunded/charged_back ⇒ REFUNDED).
 * Las notificaciones de suscripción (preapproval) actualizan el estado del tenant.
 */
internal object MpWebhookWorker {
    const val QUEUE = "mp-webhooks"
    private const val MAX_ATTEMPTS = 5
    private val log = LoggerFactory.getLogger("payments.worker")
    private val started = AtomicBoolean(false)

    fun start(app: Application) {
        if (!started.compareAndSet(false, true)) return
        val scope: CoroutineScope = app
        scope.launch(Dispatchers.IO) {
            log.info("Worker de webhooks escuchando la cola '{}'", QUEUE)
            while (isActive) {
                val msg: String? = try {
                    Realtime.popQueue(QUEUE, 5)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    delay(3_000)
                    null
                }
                if (msg != null) handle(scope, msg)
            }
        }
    }

    private suspend fun handle(scope: CoroutineScope, msg: String) {
        val job = runCatching { AppJson.parseToJsonElement(msg) as? JsonObject }.getOrNull() ?: return
        val eventKey = job.str("eventKey") ?: return
        val topic = job.str("topic") ?: ""
        val dataId = job.str("dataId") ?: return
        val tenantId = job.str("tenantId")
        val attempt = job.str("attempt")?.toIntOrNull() ?: 0
        try {
            val result = process(topic, dataId, tenantId)
            log.info("Webhook {} -> {}", eventKey, result)
            runCatching { MpStore.markEventProcessed(eventKey, result) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Webhook {} falló (intento {}): {}", eventKey, attempt + 1, e.message)
            runCatching { MpStore.markEventFailed(eventKey, e.message ?: e.javaClass.simpleName) }
            if (attempt + 1 < MAX_ATTEMPTS) {
                scope.launch {
                    delay((attempt + 1) * 10_000L)
                    Realtime.publishQueue(
                        QUEUE,
                        buildJsonObject {
                            put("eventKey", eventKey)
                            put("topic", topic)
                            put("dataId", dataId)
                            put("tenantId", tenantId)
                            put("attempt", attempt + 1)
                        }.toString(),
                    )
                }
            }
        }
    }

    internal suspend fun process(topic: String, dataId: String, tenantId: String?): String = when (topic) {
        "payment" -> processPayment(dataId, tenantId)
        "subscription_preapproval", "preapproval" -> processSubscription(dataId)
        else -> "ignorado: topic $topic"
    }

    private suspend fun processPayment(paymentId: String, tenantId: String?): String {
        if (tenantId == null) return "ignorado: pago sin tenant"
        val payment = Payments.gateway.getPayment(tenantId, paymentId) ?: return "ignorado: pago no encontrado o tenant sin cuenta"
        val orderId = payment.externalReference ?: return "ignorado: pago sin external_reference"
        val order = OrderService.getOrder(orderId) ?: return "ignorado: pedido $orderId no existe"
        // El pago se consultó con el token de ESTE restaurante: el pedido tiene que ser suyo.
        if (order.tenantId != tenantId) return "rechazado: el pedido $orderId es de otro restaurante"
        return when (payment.status) {
            "approved" -> {
                if (payment.amountCents < order.totalCents) return "rechazado: monto ${payment.amountCents} menor al total ${order.totalCents}"
                OrderService.markPaid(orderId, payment.id)
                "pedido $orderId pagado"
            }
            "refunded", "charged_back" -> {
                OrderService.markRefunded(orderId)
                "pedido $orderId reembolsado"
            }
            else -> "sin cambio: estado ${payment.status}"
        }
    }

    private suspend fun processSubscription(preapprovalId: String): String {
        val sub = Payments.gateway.getSubscription(preapprovalId) ?: return "ignorado: suscripción no disponible"
        val status = when (sub.status) {
            "authorized" -> "ACTIVE"
            "paused", "cancelled" -> "PAST_DUE"
            else -> return "sin cambio: estado ${sub.status}"
        }
        val tenantRef = sub.externalReference?.takeIf { Regex("^[0-9a-fA-F-]{36}$").matches(it) }
        val rows = MpStore.setTenantStatus(tenantRef, sub.id, status)
        return "tenant ${tenantRef ?: "(por preapproval)"} -> $status ($rows fila/s)"
    }
}
