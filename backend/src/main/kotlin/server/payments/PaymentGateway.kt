package server.payments

import app.shared.model.Order
import app.shared.model.PaymentInfo
import app.shared.model.PaymentMethod
import app.shared.model.Plan
import kotlin.random.Random

/** Pago tal como lo reporta el proveedor (GET /v1/payments/{id}). */
data class GatewayPayment(
    val id: String,
    val status: String,                 // approved | pending | in_process | rejected | refunded | charged_back | cancelled
    val externalReference: String?,     // = Order.id
    val amountCents: Long,
)

/** Suscripción (preapproval) tal como la reporta el proveedor. */
data class GatewaySubscription(
    val id: String,
    val status: String,                 // pending | authorized | paused | cancelled
    val externalReference: String?,     // = tenant id
)

/** Resultado de iniciar la conexión de la cuenta del restaurante. */
data class ConnectStart(val oauthUrl: String?, val connectedNow: Boolean)

/**
 * Pasarela de pagos. Dos implementaciones: [MercadoPagoGateway] (HTTP real, sandbox o producción)
 * y [DemoGateway] (sin red, para demostraciones sin credenciales).
 */
interface PaymentGateway {
    val name: String

    /** Inicia la conexión de la cuenta del restaurante (OAuth) o la conecta directo en demo. */
    suspend fun startConnect(tenantId: String): ConnectStart

    suspend fun createCheckout(tenantId: String, tenantSlug: String, order: Order, feeBps: Int): PaymentInfo

    suspend fun refund(tenantId: String, order: Order): Boolean

    suspend fun createSubscriptionCheckout(tenantId: String, plan: Plan, payerEmail: String): String?

    /** Consulta un pago con el token del vendedor (restaurante). */
    suspend fun getPayment(tenantId: String, paymentId: String): GatewayPayment?

    /** Consulta una suscripción con el token de la plataforma. */
    suspend fun getSubscription(preapprovalId: String): GatewaySubscription?
}

/** Sin credenciales: nada sale a la red. El pago se aprueba con simulate-payment. */
object DemoGateway : PaymentGateway {
    override val name = "demo"

    override suspend fun startConnect(tenantId: String): ConnectStart {
        MpStore.upsert(
            MpConnection(
                tenantId = tenantId, mpUserId = "demo-seller", accessToken = "DEMO", refreshToken = null,
                publicKey = null, liveMode = false, expiresAt = null,
            ),
        )
        return ConnectStart(oauthUrl = null, connectedNow = true)
    }

    override suspend fun createCheckout(tenantId: String, tenantSlug: String, order: Order, feeBps: Int): PaymentInfo {
        val method = order.payment?.method ?: PaymentMethod.CARD
        return PaymentInfo(
            method = method,
            provider = "mercadopago",
            checkoutUrl = null,
            marketplaceFeeCents = Payments.marketplaceFeeCents(order, feeBps),
            reference = when (method) {
                PaymentMethod.OXXO -> digits(14)                 // referencia de pago en tienda (ficticia)
                PaymentMethod.SPEI -> "646180" + digits(12)      // CLABE de 18 dígitos (ficticia)
                else -> null
            },
        )
    }

    override suspend fun refund(tenantId: String, order: Order): Boolean = true
    override suspend fun createSubscriptionCheckout(tenantId: String, plan: Plan, payerEmail: String): String? = null
    override suspend fun getPayment(tenantId: String, paymentId: String): GatewayPayment? = null
    override suspend fun getSubscription(preapprovalId: String): GatewaySubscription? = null

    private fun digits(n: Int): String = (1..n).map { Random.nextInt(10) }.joinToString("")
}
