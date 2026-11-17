package server.payments

import app.shared.model.Order
import app.shared.model.PaymentInfo
import app.shared.model.PaymentMethod
import app.shared.model.Plan
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import server.config.AppConfig
import server.integrations.integrationRoutes

/**
 * Fachada de pagos que usa el resto del backend.
 *
 * - `demoMode` = no hay credenciales de la app de Mercado Pago (MP_CLIENT_ID / MP_CLIENT_SECRET):
 *   nada sale a la red, el checkout se aprueba con el endpoint de simulación.
 * - Con credenciales: cada restaurante conecta SU cuenta por OAuth y el cobro cae directo en ella;
 *   la plataforma cobra su comisión con `marketplace_fee`.
 */
object Payments {
    private val log = LoggerFactory.getLogger("payments")

    val demoMode: Boolean = AppConfig.mpClientId.isNullOrBlank() || AppConfig.mpClientSecret.isNullOrBlank()

    internal val gateway: PaymentGateway = if (demoMode) DemoGateway else MercadoPagoGateway

    /** Registra las rutas de pagos e integraciones y arranca el worker de webhooks. */
    fun install(app: Application) {
        app.routing {
            mercadoPagoRoutes()
            integrationRoutes()
        }
        MpWebhookWorker.start(app)
        log.info("Pagos: gateway={} demoMode={}", gateway.name, demoMode)
    }

    /**
     * Crea el checkout del pedido. `checkoutUrl == null` ⇒ modo demostración (o el restaurante aún no
     * conecta su cuenta): el cliente aprueba con simulate-payment.
     */
    suspend fun createCheckout(tenantId: String, tenantSlug: String, order: Order, feeBps: Int): PaymentInfo {
        val method = order.payment?.method ?: PaymentMethod.CARD
        val fee = marketplaceFeeCents(order, feeBps)
        return try {
            gateway.createCheckout(tenantId, tenantSlug, order, feeBps)
        } catch (e: Exception) {
            log.warn("createCheckout falló para pedido {}: {}", order.id, e.message)
            PaymentInfo(method = method, marketplaceFeeCents = fee)
        }
    }

    /** Reembolso total del pedido en Mercado Pago (demo: siempre true). */
    suspend fun refund(tenantId: String, order: Order): Boolean = try {
        gateway.refund(tenantId, order)
    } catch (e: Exception) {
        log.warn("refund falló para pedido {}: {}", order.id, e.message)
        false
    }

    /** init_point de la suscripción mensual (preapproval) con el token de la PLATAFORMA; null en demo. */
    suspend fun createSubscriptionCheckout(tenantId: String, plan: Plan, payerEmail: String): String? = try {
        gateway.createSubscriptionCheckout(tenantId, plan, payerEmail)
    } catch (e: Exception) {
        log.warn("createSubscriptionCheckout falló para tenant {}: {}", tenantId, e.message)
        null
    }

    internal fun marketplaceFeeCents(order: Order, feeBps: Int): Long = order.subtotalCents * feeBps / 10_000
}
