package app.shared.api

import app.shared.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Json único para cliente y servidor. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    classDiscriminator = "event"
}

// ───────────────────────── Requests / responses ─────────────────────────

@Serializable data class ApiError(val message: String, val code: String = "error")

@Serializable data class LoginRequest(val email: String, val password: String)

@Serializable
data class CartLineRequest(
    val itemId: String,
    val quantity: Int,
    val optionIds: List<String> = emptyList(),   // ids de ModifierOption elegidas
    val notes: String = "",
)

@Serializable
data class CreateOrderRequest(
    val type: OrderType,
    val lines: List<CartLineRequest>,
    val customerName: String = "",
    val customerPhone: String = "",
    val tableLabel: String = "",
    val address: String = "",
    val notes: String = "",
    val tipCents: Long = 0,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
)

/**
 * `checkoutUrl` = init_point de Mercado Pago cuando el tenant tiene cuenta conectada y hay credenciales.
 * `sandbox == true` y `checkoutUrl == null` ⇒ modo demostración: el cliente llama a simulatePayment.
 */
@Serializable data class CreateOrderResponse(val order: Order, val checkoutUrl: String? = null, val sandbox: Boolean = true)

@Serializable data class UpdateOrderStatusRequest(val status: OrderStatus)
@Serializable data class SetAvailabilityRequest(val available: Boolean)

@Serializable
data class UpdateBrandRequest(
    val name: String,
    val tagline: String = "",
    val description: String = "",
    val address: String = "",
    val phone: String = "",
    val theme: BrandTheme = BrandTheme(),
    val customDomain: String? = null,
)

@Serializable
data class CreateTenantRequest(
    val name: String,
    val slug: String,
    val cuisine: String = "",
    val ownerName: String,
    val ownerEmail: String,
    val ownerPassword: String = "demo1234",
    val planId: String,
)

@Serializable data class SetTenantStatusRequest(val status: TenantStatus)
@Serializable data class AssignPlanRequest(val planId: String)

@Serializable
data class SignupRequest(
    val restaurantName: String,
    val ownerName: String,
    val email: String,
    val password: String,
    val planId: String,
)

/** `subscriptionCheckoutUrl` = init_point del preapproval (suscripción mensual) de Mercado Pago, si hay credenciales. */
@Serializable data class SignupResponse(val tenant: TenantSummary, val session: UserSession, val subscriptionCheckoutUrl: String? = null)

// ───────────────────────── Tiempo real (WebSocket, JSON por frame de texto) ─────────────────────────

@Serializable
sealed class RealtimeEvent {
    @Serializable @SerialName("order_created") data class OrderCreated(val order: Order) : RealtimeEvent()
    @Serializable @SerialName("order_updated") data class OrderUpdated(val order: Order) : RealtimeEvent()
}

// ───────────────────────── Rutas HTTP (contrato cliente ⇄ servidor) ─────────────────────────
//
// Auth: header "Authorization: Bearer <UserSession.token>" en /api/tenant/** y /api/operator/**.
// Errores: status 4xx/5xx con body ApiError. Todo JSON con AppJson.
//
// PÚBLICO (comensal, sin auth)
//   GET  /api/public/tenants                         -> List<TenantPublic>   (solo ACTIVE/TRIAL)
//   GET  /api/public/tenants/{slug}                  -> TenantPublic
//   GET  /api/public/tenants/{slug}/menu             -> Menu                 (solo items de categorías existentes)
//   POST /api/public/tenants/{slug}/orders           CreateOrderRequest -> CreateOrderResponse (status PENDING_PAYMENT)
//   GET  /api/public/orders/{orderId}                -> Order
//   POST /api/public/orders/{orderId}/simulate-payment -> Order  (solo sandbox/demo: aprueba el pago ⇒ PAID ⇒ cocina)
//   WS   /ws/public/orders/{orderId}                 -> frames RealtimeEvent.OrderUpdated de ese pedido
//   POST /api/public/signup                          SignupRequest -> SignupResponse
//   GET  /api/public/plans                           -> List<Plan>
//
// SESIÓN
//   POST /api/auth/login                             LoginRequest -> UserSession (con token)
//   GET  /api/auth/me                                -> UserSession
//
// RESTAURANTE (roles OWNER/MANAGER; CASHIER: pedidos; KITCHEN: pedidos) — tenant tomado del token
//   GET  /api/tenant/brand                           -> TenantPublic
//   PUT  /api/tenant/brand                           UpdateBrandRequest -> TenantPublic
//   GET  /api/tenant/menu                            -> Menu (incluye no disponibles)
//   PUT  /api/tenant/menu/categories/{id}            MenuCategory -> MenuCategory   (upsert; id nuevo lo genera el cliente)
//   DELETE /api/tenant/menu/categories/{id}          -> 204
//   PUT  /api/tenant/menu/items/{id}                 MenuItem -> MenuItem           (upsert)
//   DELETE /api/tenant/menu/items/{id}               -> 204
//   PUT  /api/tenant/menu/items/{id}/availability    SetAvailabilityRequest -> MenuItem
//   GET  /api/tenant/orders?active=true|false        -> List<Order> (active=true: PAID, PREPARING, READY; false: todos, recientes primero, máx 100)
//   PUT  /api/tenant/orders/{id}/status              UpdateOrderStatusRequest -> Order
//   POST /api/tenant/orders/{id}/refund              -> Order (REFUNDED; sincroniza reembolso con Mercado Pago)
//   WS   /ws/tenant/orders?token=<jwt>               -> frames RealtimeEvent (creados = pagados que entran a cocina, y actualizados)
//   GET  /api/tenant/stats                           -> TenantStats
//   GET  /api/tenant/staff                           -> List<StaffMember>
//   GET  /api/tenant/payments/mercadopago            -> MpConnectionStatus
//   POST /api/tenant/payments/mercadopago/connect    -> MpConnectionStatus (con oauthUrl si hay credenciales; en demo conecta directo)
//   POST /api/tenant/payments/mercadopago/disconnect -> MpConnectionStatus
//   GET  /api/tenant/integrations                    -> List<IntegrationStatus>
//   POST /api/tenant/integrations/{provider}/toggle  -> List<IntegrationStatus>   (conecta/desconecta mock)
//
// OPERADOR (rol OPERATOR)
//   GET  /api/operator/metrics                       -> OperatorMetrics
//   GET  /api/operator/tenants                       -> List<TenantSummary>
//   POST /api/operator/tenants                       CreateTenantRequest -> TenantSummary
//   PUT  /api/operator/tenants/{id}/status           SetTenantStatusRequest -> TenantSummary
//   PUT  /api/operator/tenants/{id}/plan             AssignPlanRequest -> TenantSummary
//   GET  /api/operator/plans                         -> List<Plan>
//   PUT  /api/operator/plans/{id}                    Plan -> Plan (upsert)
//
// WEBHOOKS / OAUTH (servidor ⇄ Mercado Pago)
//   POST /api/webhooks/mercadopago                   notificación de pago / reembolso / preapproval
//   GET  /api/oauth/mercadopago/callback?code=&state= canje del código OAuth del restaurante
//
// MEDIA
//   Las imágenes "res://demo/<archivo>" se resuelven en el cliente (recursos empaquetados).
object ApiRoutes {
    const val HEALTH = "/api/health"
    const val LOGIN = "/api/auth/login"
    const val ME = "/api/auth/me"
    const val PUBLIC_TENANTS = "/api/public/tenants"
    const val PUBLIC_PLANS = "/api/public/plans"
    const val PUBLIC_SIGNUP = "/api/public/signup"
    fun publicTenant(slug: String) = "/api/public/tenants/$slug"
    fun publicMenu(slug: String) = "/api/public/tenants/$slug/menu"
    fun publicOrders(slug: String) = "/api/public/tenants/$slug/orders"
    fun publicOrder(orderId: String) = "/api/public/orders/$orderId"
    fun simulatePayment(orderId: String) = "/api/public/orders/$orderId/simulate-payment"
    fun wsPublicOrder(orderId: String) = "/ws/public/orders/$orderId"
    const val TENANT_BRAND = "/api/tenant/brand"
    const val TENANT_MENU = "/api/tenant/menu"
    fun tenantCategory(id: String) = "/api/tenant/menu/categories/$id"
    fun tenantItem(id: String) = "/api/tenant/menu/items/$id"
    fun tenantItemAvailability(id: String) = "/api/tenant/menu/items/$id/availability"
    const val TENANT_ORDERS = "/api/tenant/orders"
    fun tenantOrderStatus(id: String) = "/api/tenant/orders/$id/status"
    fun tenantOrderRefund(id: String) = "/api/tenant/orders/$id/refund"
    const val WS_TENANT_ORDERS = "/ws/tenant/orders"
    const val TENANT_STATS = "/api/tenant/stats"
    const val TENANT_STAFF = "/api/tenant/staff"
    const val TENANT_MP = "/api/tenant/payments/mercadopago"
    const val TENANT_MP_CONNECT = "/api/tenant/payments/mercadopago/connect"
    const val TENANT_MP_DISCONNECT = "/api/tenant/payments/mercadopago/disconnect"
    const val TENANT_INTEGRATIONS = "/api/tenant/integrations"
    fun tenantIntegrationToggle(provider: IntegrationProvider) = "/api/tenant/integrations/${provider.name}/toggle"
    const val OPERATOR_METRICS = "/api/operator/metrics"
    const val OPERATOR_TENANTS = "/api/operator/tenants"
    fun operatorTenantStatus(id: String) = "/api/operator/tenants/$id/status"
    fun operatorTenantPlan(id: String) = "/api/operator/tenants/$id/plan"
    const val OPERATOR_PLANS = "/api/operator/plans"
    fun operatorPlan(id: String) = "/api/operator/plans/$id"
    const val WEBHOOK_MP = "/api/webhooks/mercadopago"
    const val OAUTH_MP_CALLBACK = "/api/oauth/mercadopago/callback"
}
