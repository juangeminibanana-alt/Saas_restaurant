package app.feature.diner

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.shared.api.CartLineRequest
import app.shared.api.CreateOrderRequest
import app.shared.model.Menu
import app.shared.model.MenuItem
import app.shared.model.ModifierGroup
import app.shared.model.ModifierOption
import app.shared.model.Order
import app.shared.model.OrderType
import app.shared.model.PaymentMethod
import app.shared.model.TenantPublic
import app.shared.util.newId

/** Costo fijo de envío a domicilio (MXN, centavos). */
const val DELIVERY_FEE_CENTS: Long = 3_500L
val TIP_OPTIONS: List<Int> = listOf(0, 10, 15, 20)
val DINER_PAYMENT_METHODS: List<PaymentMethod> =
    listOf(PaymentMethod.CARD, PaymentMethod.OXXO, PaymentMethod.SPEI, PaymentMethod.MP_WALLET)

/** `slug` puede venir como "brasa-demo?mesa=4" (QR por mesa) o "brasa-demo?order=<id>" (regreso de Mercado Pago). */
data class DinerRoute(val slug: String, val table: String? = null, val orderId: String? = null)

fun parseDinerSlug(raw: String): DinerRoute {
    val trimmed = raw.trim().trim('/')
    val slug = trimmed.substringBefore('?').substringBefore('&').trim('/')
    val query = if (trimmed.contains('?')) trimmed.substringAfter('?') else ""
    val params = query.split('&')
        .filter { it.contains('=') }
        .associate { it.substringBefore('=').lowercase() to it.substringAfter('=').replace("%20", " ").replace("+", " ").trim() }
    val table = (params["mesa"] ?: params["table"])?.takeIf { it.isNotBlank() }
    val orderId = (params["order"] ?: params["pedido"] ?: params["external_reference"])?.takeIf { it.isNotBlank() }
    return DinerRoute(slug = slug, table = table, orderId = orderId)
}

data class CartLine(
    val key: String,
    val item: MenuItem,
    val quantity: Int,
    val optionIds: List<String> = emptyList(),
    val notes: String = "",
) {
    val options: List<Pair<ModifierGroup, ModifierOption>>
        get() = item.modifierGroups.flatMap { g -> g.options.filter { it.id in optionIds }.map { g to it } }
    val unitCents: Long get() = item.priceCents + options.sumOf { it.second.priceDeltaCents }
    val totalCents: Long get() = unitCents * quantity
    val summary: String
        get() {
            val parts = options.map { it.second.name }.toMutableList()
            if (notes.isNotBlank()) parts.add("“" + notes.trim() + "”")
            return parts.joinToString(" · ")
        }
}

enum class DinerScreen { MENU, CART, WAITING, TRACKING, HISTORY }
enum class PayPhase { IDLE, CREATING, SANDBOX, PROCESSING, SUCCESS }

/**
 * Pedidos de la visita por restaurante. Viven mientras viva la app (no solo la pantalla del menú),
 * para que "Mis pedidos" no se vacíe al salir del menú y volver a entrar.
 */
private val sessionHistory = mutableMapOf<String, SnapshotStateList<Order>>()

@Stable
class DinerState(val route: DinerRoute) {
    // ── Carga
    var tenant by mutableStateOf<TenantPublic?>(null)
    var menu by mutableStateOf(Menu())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var loadedAt by mutableStateOf(0L)
    var reloadTick by mutableStateOf(0)

    // ── Navegación / menú
    var screen by mutableStateOf(DinerScreen.MENU)
    var query by mutableStateOf("")
    var scrollTopTick by mutableStateOf(0)

    // ── Carrito
    val cart = mutableStateListOf<CartLine>()
    var addPulse by mutableStateOf(0)

    // ── Hoja de platillo
    var sheetItem by mutableStateOf<MenuItem?>(null)
    var sheetVisible by mutableStateOf(false)
    var sheetSession by mutableStateOf(0)

    // ── Datos del pedido
    var orderType by mutableStateOf(OrderType.DINE_IN)
    var tableLabel by mutableStateOf(route.table ?: "")
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")
    var address by mutableStateOf("")
    var orderNotes by mutableStateOf("")
    var tipPercent by mutableStateOf(0)
    var paymentMethod by mutableStateOf(PaymentMethod.CARD)
    var showErrors by mutableStateOf(false)

    // ── Pago / seguimiento
    var payPhase by mutableStateOf(PayPhase.IDLE)
    var pendingOrder by mutableStateOf<Order?>(null)
    var activeOrder by mutableStateOf<Order?>(null)
    var checkoutUrl by mutableStateOf<String?>(null)
    val history: SnapshotStateList<Order> = sessionHistory.getOrPut(route.slug) { mutableStateListOf() }

    val itemCount: Int get() = cart.sumOf { it.quantity }
    val subtotalCents: Long get() = cart.sumOf { it.totalCents }
    val deliveryFeeCents: Long get() = if (orderType == OrderType.DELIVERY && cart.isNotEmpty()) DELIVERY_FEE_CENTS else 0L
    val tipCents: Long get() = subtotalCents * tipPercent / 100L
    val totalCents: Long get() = subtotalCents + deliveryFeeCents + tipCents

    fun applyTenantDefaults(tenant: TenantPublic) {
        val types = tenant.orderTypes.ifEmpty { OrderType.entries.toList() }
        orderType = when {
            route.table != null && OrderType.DINE_IN in types -> OrderType.DINE_IN
            orderType in types -> orderType
            else -> types.first()
        }
    }

    fun quantityOf(itemId: String): Int = cart.filter { it.item.id == itemId }.sumOf { it.quantity }

    fun requiresChoice(item: MenuItem): Boolean = item.modifierGroups.any { it.minSelect > 0 }

    fun add(item: MenuItem, quantity: Int = 1, optionIds: List<String> = emptyList(), notes: String = "") {
        if (quantity <= 0) return
        val sorted = optionIds.sorted()
        val index = cart.indexOfFirst { it.item.id == item.id && it.optionIds == sorted && it.notes == notes }
        if (index >= 0) {
            val line = cart[index]
            cart[index] = line.copy(quantity = (line.quantity + quantity).coerceAtMost(99))
        } else {
            cart.add(CartLine(key = newId("ln"), item = item, quantity = quantity, optionIds = sorted, notes = notes))
        }
        addPulse++
    }

    /** Quita una unidad del último renglón de ese platillo (para el "–" de la tarjeta). */
    fun removeOne(itemId: String) {
        val index = cart.indexOfLast { it.item.id == itemId }
        if (index < 0) return
        val line = cart[index]
        if (line.quantity <= 1) cart.removeAt(index) else cart[index] = line.copy(quantity = line.quantity - 1)
    }

    fun setQuantity(key: String, quantity: Int) {
        val index = cart.indexOfFirst { it.key == key }
        if (index < 0) return
        if (quantity <= 0) cart.removeAt(index) else {
            if (quantity > cart[index].quantity) addPulse++
            cart[index] = cart[index].copy(quantity = quantity.coerceAtMost(99))
        }
    }

    fun remove(key: String) {
        val index = cart.indexOfFirst { it.key == key }
        if (index >= 0) cart.removeAt(index)
    }

    fun clearCart() {
        cart.clear()
        orderNotes = ""
        tipPercent = 0
        showErrors = false
    }

    fun openSheet(item: MenuItem) {
        sheetItem = item
        sheetSession++
        sheetVisible = true
    }

    fun closeSheet() {
        sheetVisible = false
    }

    fun validate(): String? = when {
        cart.isEmpty() -> "Tu pedido está vacío."
        orderType == OrderType.DINE_IN && tableLabel.isBlank() -> "Indica el número de tu mesa."
        orderType != OrderType.DINE_IN && customerName.isBlank() -> "Escribe tu nombre."
        orderType != OrderType.DINE_IN && customerPhone.count { it.isDigit() } < 8 -> "Escribe un teléfono válido."
        orderType == OrderType.DELIVERY && address.isBlank() -> "Escribe la dirección de entrega."
        else -> null
    }

    fun buildRequest(): CreateOrderRequest = CreateOrderRequest(
        type = orderType,
        lines = cart.map { CartLineRequest(itemId = it.item.id, quantity = it.quantity, optionIds = it.optionIds, notes = it.notes) },
        customerName = customerName.trim(),
        customerPhone = customerPhone.trim(),
        tableLabel = if (orderType == OrderType.DINE_IN) tableLabel.trim() else "",
        address = if (orderType == OrderType.DELIVERY) address.trim() else "",
        notes = orderNotes.trim(),
        tipCents = tipCents,
        paymentMethod = paymentMethod,
    )

    /** Inserta o actualiza el pedido en el historial de la sesión (más reciente primero). */
    fun recordOrder(order: Order) {
        val index = history.indexOfFirst { it.id == order.id }
        if (index >= 0) history[index] = order else history.add(0, order)
        if (activeOrder?.id == order.id) activeOrder = order
        if (pendingOrder?.id == order.id) pendingOrder = order
    }

    /** "Pedir de nuevo": rellena el carrito con lo que siga disponible. Devuelve cuántas piezas agregó. */
    fun reorder(order: Order): Int {
        cart.clear()
        var added = 0
        for (line in order.lines) {
            val item = menu.items.firstOrNull { it.id == line.itemId } ?: continue
            if (!item.available) continue
            val optionIds = line.modifiers.mapNotNull { m ->
                item.modifierGroups.firstOrNull { it.name == m.groupName }?.options?.firstOrNull { it.name == m.optionName }?.id
            }
            add(item, line.quantity, optionIds, line.notes)
            added += line.quantity
        }
        return added
    }

    fun startNewOrder() {
        clearCart()
        query = ""
        scrollTopTick++
        screen = DinerScreen.MENU
    }
}
