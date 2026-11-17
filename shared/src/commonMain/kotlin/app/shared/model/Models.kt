package app.shared.model

import kotlinx.serialization.Serializable

// ───────────────────────── Marca / tema por tenant ─────────────────────────

@Serializable enum class FontChoice { INTER, FRAUNCES, SPACE_GROTESK, NUNITO }
@Serializable enum class RadiusStyle { SHARP, SOFT, ROUND, PILL }
@Serializable enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Tema de marca de un restaurante. Todo lo visual que cambia por tenant vive aquí,
 * se guarda como JSON y se traduce a tokens del sistema de diseño sin tocar código.
 * `primary == null` significa "neutro" (tinta sobre papel), el default de la plataforma.
 */
@Serializable
data class BrandTheme(
    val primary: String? = null,            // hex "#RRGGBB"
    val fontDisplay: FontChoice = FontChoice.INTER,
    val fontBody: FontChoice = FontChoice.INTER,
    val radius: RadiusStyle = RadiusStyle.ROUND,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val logoUrl: String? = null,
    val coverUrl: String? = null,
)

// ───────────────────────── Tenants / planes ─────────────────────────

@Serializable enum class TenantStatus { TRIAL, ACTIVE, PAST_DUE, SUSPENDED }
@Serializable enum class OrderType { DINE_IN, PICKUP, DELIVERY }

/** day: 1 = lunes … 7 = domingo. Horas en "HH:mm". */
@Serializable data class OpeningHours(val day: Int, val open: String, val close: String)

/** Lo que ve el comensal (y cualquiera con el link/QR). */
@Serializable
data class TenantPublic(
    val id: String,
    val slug: String,
    val name: String,
    val tagline: String = "",
    val description: String = "",
    val cuisine: String = "",
    val theme: BrandTheme = BrandTheme(),
    val currency: String = "MXN",
    val address: String = "",
    val phone: String = "",
    val hours: List<OpeningHours> = emptyList(),
    val isOpen: Boolean = true,
    val orderTypes: List<OrderType> = listOf(OrderType.DINE_IN, OrderType.PICKUP, OrderType.DELIVERY),
    val etaMinutes: Int = 25,
    val rating: Double = 4.8,
    val customDomain: String? = null,
    val acceptsPayments: Boolean = true,
)

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val priceCents: Long,                   // mensual, MXN
    val description: String = "",
    val features: List<String> = emptyList(),
    val feeBps: Int = 150,                  // comisión de plataforma (marketplace_fee) en puntos base
    val highlighted: Boolean = false,
    val maxLocations: Int = 1,
)

/** Vista del operador sobre un tenant. */
@Serializable
data class TenantSummary(
    val id: String,
    val slug: String,
    val name: String,
    val status: TenantStatus,
    val planId: String,
    val planName: String,
    val ownerName: String = "",
    val ownerEmail: String = "",
    val createdAt: Long = 0,
    val mpConnected: Boolean = false,
    val orders30d: Int = 0,
    val gmv30dCents: Long = 0,
    val mrrCents: Long = 0,
    val primary: String? = null,
)

// ───────────────────────── Menú ─────────────────────────

@Serializable data class ModifierOption(val id: String, val name: String, val priceDeltaCents: Long = 0)

@Serializable
data class ModifierGroup(
    val id: String,
    val name: String,
    val minSelect: Int = 0,
    val maxSelect: Int = 1,
    val options: List<ModifierOption> = emptyList(),
)

@Serializable
data class MenuItem(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String = "",
    val priceCents: Long,
    val imageUrl: String? = null,           // "res://demo/x.jpg" (recurso empaquetado) o URL http(s)
    val tags: List<String> = emptyList(),   // "popular", "nuevo", "picante", "vegetariano", "vegano", "sin gluten"
    val available: Boolean = true,
    val modifierGroups: List<ModifierGroup> = emptyList(),
    val prepMinutes: Int = 15,
    val sortOrder: Int = 0,
)

@Serializable data class MenuCategory(val id: String, val name: String, val description: String = "", val sortOrder: Int = 0)

@Serializable data class Menu(val categories: List<MenuCategory> = emptyList(), val items: List<MenuItem> = emptyList())

// ───────────────────────── Pedidos / pagos ─────────────────────────

/** PENDING_PAYMENT → PAID (entra a cocina como "nuevo") → PREPARING → READY → COMPLETED. */
@Serializable enum class OrderStatus { PENDING_PAYMENT, PAID, PREPARING, READY, COMPLETED, CANCELLED, REFUNDED }
@Serializable enum class OrderChannel { DIRECT, UBER_EATS, RAPPI, DIDI_FOOD }
@Serializable enum class PaymentMethod { CARD, OXXO, SPEI, MP_WALLET, PAYMENT_LINK }
@Serializable enum class PaymentStatus { PENDING, APPROVED, REJECTED, REFUNDED }

@Serializable data class OrderLineModifier(val groupName: String, val optionName: String, val priceDeltaCents: Long = 0)

@Serializable
data class OrderLine(
    val itemId: String,
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long,               // precio base del platillo
    val modifiers: List<OrderLineModifier> = emptyList(),
    val notes: String = "",
    val imageUrl: String? = null,
) {
    val lineTotalCents: Long get() = (unitPriceCents + modifiers.sumOf { it.priceDeltaCents }) * quantity
}

@Serializable
data class PaymentInfo(
    val method: PaymentMethod,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val provider: String = "mercadopago",
    val providerPaymentId: String? = null,
    val checkoutUrl: String? = null,        // init_point de Mercado Pago (sandbox o producción)
    val marketplaceFeeCents: Long = 0,
    val reference: String? = null,          // referencia OXXO / CLABE SPEI
)

@Serializable
data class Order(
    val id: String,
    val tenantId: String,
    val tenantSlug: String = "",
    val number: String,                     // corto y legible: "A-042"
    val status: OrderStatus,
    val type: OrderType,
    val channel: OrderChannel = OrderChannel.DIRECT,
    val lines: List<OrderLine> = emptyList(),
    val subtotalCents: Long = 0,
    val tipCents: Long = 0,
    val deliveryFeeCents: Long = 0,
    val totalCents: Long = 0,
    val customerName: String = "",
    val customerPhone: String = "",
    val tableLabel: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = 0,                // epoch millis
    val paidAt: Long? = null,
    val preparingAt: Long? = null,
    val readyAt: Long? = null,
    val completedAt: Long? = null,
    val etaMinutes: Int = 20,
    val payment: PaymentInfo? = null,
)

// ───────────────────────── Usuarios / roles ─────────────────────────

/** OPERATOR = super admin del SaaS. El resto son roles dentro de un restaurante. */
@Serializable enum class Role { OPERATOR, OWNER, MANAGER, CASHIER, KITCHEN }

@Serializable
data class UserSession(
    val userId: String,
    val name: String,
    val email: String,
    val role: Role,
    val tenantId: String? = null,
    val tenantSlug: String? = null,
    val tenantName: String? = null,
    val token: String = "",
)

@Serializable data class StaffMember(val id: String, val name: String, val email: String, val role: Role)

// ───────────────────────── Métricas ─────────────────────────

@Serializable data class MetricPoint(val label: String, val value: Double)

@Serializable
data class OperatorMetrics(
    val mrrCents: Long = 0,
    val mrrDeltaPct: Double = 0.0,
    val activeTenants: Int = 0,
    val trialTenants: Int = 0,
    val suspendedTenants: Int = 0,
    val churnPct: Double = 0.0,
    val activationPct: Double = 0.0,
    val gmv30dCents: Long = 0,
    val platformFees30dCents: Long = 0,
    val orders30d: Int = 0,
    val mrrSeries: List<MetricPoint> = emptyList(),
    val signupsSeries: List<MetricPoint> = emptyList(),
)

@Serializable data class TopItem(val name: String, val quantity: Int, val revenueCents: Long)

@Serializable
data class TenantStats(
    val ordersToday: Int = 0,
    val salesTodayCents: Long = 0,
    val avgTicketCents: Long = 0,
    val avgPrepMinutes: Int = 0,
    val salesSeries: List<MetricPoint> = emptyList(),
    val topItems: List<TopItem> = emptyList(),
)

// ───────────────────────── Integraciones ─────────────────────────

@Serializable enum class IntegrationProvider { MERCADO_PAGO, LALAMOVE, UBER_EATS, RAPPI, DIDI_FOOD }

@Serializable
data class IntegrationStatus(
    val provider: IntegrationProvider,
    val name: String,
    val description: String = "",
    val category: String = "",              // "Pagos" | "Reparto" | "Marketplaces"
    val connected: Boolean = false,
    val mode: String = "mock",              // "mock" | "sandbox" | "live"
)

@Serializable
data class MpConnectionStatus(
    val connected: Boolean = false,
    val mpUserId: String? = null,
    val liveMode: Boolean = false,
    val feeBps: Int = 150,
    val oauthUrl: String? = null,           // URL de autorización OAuth cuando hay credenciales de app
    val methods: List<PaymentMethod> = PaymentMethod.entries,
)
