package app.data

import app.shared.api.CreateOrderRequest
import app.shared.api.CreateOrderResponse
import app.shared.api.CreateTenantRequest
import app.shared.api.RealtimeEvent
import app.shared.api.SignupRequest
import app.shared.api.SignupResponse
import app.shared.api.UpdateBrandRequest
import app.shared.data.AppRepository
import app.shared.data.RepositoryException
import app.shared.demo.DemoData
import app.shared.model.*
import app.shared.util.newId
import app.shared.util.nowMillis
import app.shared.util.slugify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DAY_MS = 86_400_000L
private const val MAX_ACTIVE_FOR_SIMULATION = 12

private val SOLD_STATUSES = setOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED)
private val KITCHEN_STATUSES = setOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY)
private val MARKETPLACES = listOf(IntegrationProvider.UBER_EATS, IntegrationProvider.RAPPI, IntegrationProvider.DIDI_FOOD)
private val WEEKDAY_LABELS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
private val WEEKDAY_FACTORS = listOf(0.72, 0.78, 0.86, 0.95, 1.24, 1.38, 1.1)
private val MONTH_LABELS = listOf("Jun", "Jul", "Ago", "Sep", "Oct", "Nov")
private val HISTORY_MINUTES_AGO = listOf(38, 55, 82, 118, 164, 221, 297, 388, 1510, 2960, 4410, 5830)
private val HISTORY_NAMES = listOf("Regina", "Emilio", "Valeria T.", "Jorge", "Ximena", "Raúl G.", "Daniela", "Iván", "Mónica", "Héctor P.", "Camila", "Santiago")
private val MARKETPLACE_NAMES = listOf("Mariana G.", "José Luis", "Karla P.", "Roberto", "Alejandra S.", "Miguel Á.", "Natalia", "Pablo R.", "Renata", "Gustavo M.")

private fun fail(message: String, code: String = "error"): Nothing = throw RepositoryException(message, code)

private class DemoAccount(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val tenantId: String?,
    val password: String,
)

/** Estado mutable de un restaurante en memoria. Solo se toca con el candado de DemoRepository tomado. */
private class TenantState(
    var info: TenantPublic,
    var status: TenantStatus,
    var planId: String,
    val createdAt: Long,
    var menu: Menu,
    var mpUserId: String?,                 // null = Mercado Pago desconectado
    val baseOrders30d: Int = 0,            // histórico ficticio previo a esta sesión (vista del operador)
    val baseGmv30dCents: Long = 0,
    val dailyBaseCents: Long = 0,          // base determinista para la serie de ventas de 7 días
    var orderCounter: Int = 0,
    var seeded: Boolean = true,            // false = aún faltan los pedidos de ejemplo
    val listed: Boolean = true,            // aparece en el directorio público
) {
    val orders = mutableListOf<Order>()
    val connected = mutableSetOf<IntegrationProvider>()
    val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val simulators = mutableMapOf<IntegrationProvider, Job>()
}

/**
 * Repositorio de demostración: todo vive en memoria, sembrado con DemoData, y es un singleton del proceso
 * para que todas las pantallas (comensal, cocina, panel, operador) compartan el mismo estado en vivo.
 */
object DemoRepository : AppRepository {
    override val isRemote: Boolean = false

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bootMillis = nowMillis()

    private val plans: MutableList<Plan> = DemoData.plans.toMutableList()
    private val tenants = mutableListOf<TenantState>()
    private val accounts = mutableListOf<DemoAccount>()
    private val trackers = mutableMapOf<String, MutableStateFlow<Order>>()

    private val _session = MutableStateFlow<UserSession?>(null)
    override val session: StateFlow<UserSession?> = _session.asStateFlow()

    init {
        addDemoTenant("brasa-demo", daysAgo = 170, orders30d = 1284, gmv30dCents = 41_260_000L, dailyBaseCents = 520_000L,
            mpUserId = "demo-seller", connected = setOf(IntegrationProvider.LALAMOVE, IntegrationProvider.UBER_EATS, IntegrationProvider.RAPPI))
        addDemoTenant("nori-demo", daysAgo = 21, orders30d = 512, gmv30dCents = 14_830_000L, dailyBaseCents = 460_000L,
            mpUserId = null, connected = emptySet())
        addDemoTenant("miga-demo", daysAgo = 88, orders30d = 1930, gmv30dCents = 20_140_000L, dailyBaseCents = 210_000L,
            mpUserId = null, connected = emptySet())
        for (u in DemoData.users) {
            accounts += DemoAccount(
                id = "u-" + slugify(u.email), email = u.email.lowercase(), name = u.name, role = u.role,
                tenantId = u.tenantSlug?.let { DemoData.tenant(it)?.id }, password = DemoData.DEMO_PASSWORD,
            )
        }
        addFictitious("Fonda Azul Demo", "Cocina casera", "plan-esencial", TenantStatus.ACTIVE, 160, "Rosa Méndez", true, 642, 9_850_000L, "#2B5FA8")
        addFictitious("Trigo y Sal Demo", "Pizzas · Pastas", "plan-pro", TenantStatus.ACTIVE, 128, "Andrés Cota", true, 1105, 23_640_000L, "#8A5A2B")
        addFictitious("Mar Adentro Demo", "Mariscos", "plan-cadena", TenantStatus.ACTIVE, 102, "Ernesto Salas", true, 3420, 112_300_000L, "#0E7490")
        addFictitious("La Comal Demo", "Antojitos", "plan-pro", TenantStatus.PAST_DUE, 76, "Gloria Rentería", true, 410, 7_120_000L, "#C2410C")
        addFictitious("Humo Lento Demo", "Ahumados", "plan-esencial", TenantStatus.SUSPENDED, 143, "Óscar Beltrán", false, 0, 0L, "#57534E")
        addFictitious("Dulce Nube Demo", "Repostería", "plan-pro", TenantStatus.TRIAL, 41, "Itzel Carmona", true, 96, 1_480_000L, "#DB2777")
        addFictitious("Verde Vivo Demo", "Ensaladas · Bowls", "plan-esencial", TenantStatus.TRIAL, 9, "Paula Íñiguez", false, 38, 620_000L, "#3E8E5A")
    }

    // ─────────────────────────────── Siembra ───────────────────────────────

    private fun addDemoTenant(
        slug: String, daysAgo: Int, orders30d: Int, gmv30dCents: Long, dailyBaseCents: Long,
        mpUserId: String?, connected: Set<IntegrationProvider>,
    ) {
        val info = DemoData.tenant(slug) ?: return
        val state = TenantState(
            info = info, status = TenantStatus.ACTIVE, planId = DemoData.tenantPlan[slug] ?: DemoData.plans.first().id,
            createdAt = bootMillis - daysAgo * DAY_MS, menu = DemoData.menuFor(slug), mpUserId = mpUserId,
            baseOrders30d = orders30d, baseGmv30dCents = gmv30dCents, dailyBaseCents = dailyBaseCents,
            orderCounter = 45, seeded = false,
        )
        state.connected += connected
        tenants += state
    }

    private fun addFictitious(
        name: String, cuisine: String, planId: String, status: TenantStatus, daysAgo: Int,
        ownerName: String, mpConnected: Boolean, orders30d: Int, gmv30dCents: Long, primary: String,
    ) {
        val slug = slugify(name)
        val short = slug.removeSuffix("-demo")
        val info = TenantPublic(
            id = "t-$short", slug = slug, name = name, tagline = cuisine, cuisine = cuisine,
            description = "Restaurante ficticio de demostración.", theme = BrandTheme(primary = primary),
        )
        tenants += TenantState(
            info = info, status = status, planId = planId, createdAt = bootMillis - daysAgo * DAY_MS, menu = starterMenu(),
            mpUserId = if (mpConnected) "demo-seller-$short" else null,
            baseOrders30d = orders30d, baseGmv30dCents = gmv30dCents, listed = false,
        )
        accounts += DemoAccount(
            id = "u-dueno-$short", email = "dueno@" + short.replace("-", "") + ".demo", name = ownerName,
            role = Role.OWNER, tenantId = info.id, password = DemoData.DEMO_PASSWORD,
        )
    }

    private fun starterMenu(): Menu = Menu(
        categories = listOf(
            MenuCategory(newId("cat"), "Platillos", "", 0),
            MenuCategory(newId("cat"), "Bebidas", "", 1),
        ),
    )

    /** Pedidos de ejemplo (activos) + historial entregado, la primera vez que se usa el tenant. */
    private fun seedOrders(t: TenantState) {
        if (t.seeded) return
        t.seeded = true
        val now = nowMillis()
        val samples = DemoData.sampleOrders(t.info.slug, now).map { it.copy(tenantId = t.info.id, tenantSlug = t.info.slug) }
        val existing = t.orders.map { it.id }.toSet()
        t.orders += samples.filter { it.id !in existing }
        t.orders += historyOrders(t, now)
        syncSimulators(t)
    }

    private fun historyOrders(t: TenantState, now: Long): List<Order> {
        val items = t.menu.items
        if (items.isEmpty()) return emptyList()
        val feeBps = planOf(t).feeBps
        return HISTORY_MINUTES_AGO.mapIndexed { i, minutesAgo ->
            val lineCount = 1 + (i % 3)
            val lines = (0 until lineCount).map { j ->
                val item = items[(i * 3 + j * 2) % items.size]
                val mods = item.modifierGroups.filter { it.minSelect > 0 && it.options.isNotEmpty() }.map { g ->
                    val o = g.options[(i + j) % g.options.size]
                    OrderLineModifier(g.name, o.name, o.priceDeltaCents)
                }
                OrderLine(item.id, item.name, 1 + ((i + j) % 2), item.priceCents, mods, "", item.imageUrl)
            }
            val channel = when (i % 5) {
                2 -> OrderChannel.UBER_EATS
                4 -> OrderChannel.RAPPI
                else -> OrderChannel.DIRECT
            }
            val type = when {
                channel != OrderChannel.DIRECT -> OrderType.DELIVERY
                i % 3 == 0 -> OrderType.DINE_IN
                i % 3 == 1 -> OrderType.PICKUP
                else -> OrderType.DELIVERY
            }
            val subtotal = lines.sumOf { it.lineTotalCents }
            val tip = if (channel == OrderChannel.DIRECT && i % 2 == 0) subtotal / 10 else 0L
            val created = now - minutesAgo * 60_000L
            val paid = created + 25_000L
            val preparing = paid + 60_000L
            val ready = preparing + (9 + (i * 7) % 12) * 60_000L
            val n = 40 - i
            Order(
                id = "hist-${t.info.slug}-$n", tenantId = t.info.id, tenantSlug = t.info.slug,
                number = "A-" + n.toString().padStart(3, '0'),
                status = OrderStatus.COMPLETED, type = type, channel = channel, lines = lines,
                subtotalCents = subtotal, tipCents = tip, totalCents = subtotal + tip,
                customerName = HISTORY_NAMES[i % HISTORY_NAMES.size],
                tableLabel = if (type == OrderType.DINE_IN) "Mesa ${2 + (i * 3) % 11}" else "",
                address = if (type == OrderType.DELIVERY) "Calle Ejemplo ${10 + i * 7}, Col. Demostración" else "",
                createdAt = created, paidAt = paid, preparingAt = preparing, readyAt = ready, completedAt = ready + 4 * 60_000L,
                etaMinutes = t.info.etaMinutes,
                payment = PaymentInfo(
                    method = if (i % 4 == 1) PaymentMethod.MP_WALLET else PaymentMethod.CARD,
                    status = PaymentStatus.APPROVED, providerPaymentId = "demo-pay-h$n",
                    marketplaceFeeCents = subtotal * feeBps / 10_000,
                ),
            )
        }
    }

    // ─────────────────────────────── Utilidades internas ───────────────────────────────

    /** Latencia artificial + exclusión mutua: todo el estado se lee y escribe dentro del candado. */
    private suspend fun <T> op(block: () -> T): T {
        delay(Random.nextLong(150L, 351L))
        return lock.withLock { block() }
    }

    private fun planOf(t: TenantState): Plan = plans.firstOrNull { it.id == t.planId } ?: plans.first()

    private fun tenantBySlug(slug: String): TenantState =
        tenants.firstOrNull { it.info.slug == slug } ?: fail("No encontramos este restaurante", "not_found")

    /** Tenant visible para el comensal: existe y no está suspendido. */
    private fun publicTenant(slug: String): TenantState {
        val t = tenantBySlug(slug.trim().lowercase())
        if (t.status == TenantStatus.SUSPENDED) fail("Este restaurante no está disponible", "unavailable")
        return t
    }

    private fun denied(): Nothing = fail("No tienes permiso para esto", "forbidden")

    private fun currentSession(): UserSession = _session.value ?: fail("Inicia sesión para continuar", "unauthorized")

    /** Tenant de la sesión. OWNER/MANAGER: todo; CASHIER/KITCHEN: solo lo marcado como `ordersOnly`. */
    private fun staffTenant(ordersOnly: Boolean): TenantState {
        val s = currentSession()
        val allowed = when (s.role) {
            Role.OWNER, Role.MANAGER -> true
            Role.CASHIER, Role.KITCHEN -> ordersOnly
            Role.OPERATOR -> false
        }
        if (!allowed) denied()
        return tenants.firstOrNull { it.info.id == s.tenantId } ?: denied()
    }

    private fun requireOperator() {
        if (currentSession().role != Role.OPERATOR) denied()
    }

    private fun sessionFor(account: DemoAccount): UserSession {
        val t = account.tenantId?.let { id -> tenants.firstOrNull { it.info.id == id } }
        return UserSession(
            userId = account.id, name = account.name, email = account.email, role = account.role,
            tenantId = t?.info?.id, tenantSlug = t?.info?.slug, tenantName = t?.info?.name,
            token = "demo-" + account.id,
        )
    }

    private fun normalized(menu: Menu): Menu {
        val categories = menu.categories.sortedBy { it.sortOrder }
        val position = categories.withIndex().associate { it.value.id to it.index }
        val items = menu.items
            .filter { it.categoryId in position }
            .sortedWith(compareBy<MenuItem> { position[it.categoryId] ?: 0 }.thenBy { it.sortOrder })
        return Menu(categories, items)
    }

    private fun nextNumber(t: TenantState): String {
        t.orderCounter += 1
        return "A-" + t.orderCounter.toString().padStart(3, '0')
    }

    private fun digits(count: Int): String = (1..count).map { Random.nextInt(10) }.joinToString("")

    private fun referenceFor(method: PaymentMethod): String? = when (method) {
        PaymentMethod.OXXO -> digits(14)
        PaymentMethod.SPEI -> "646180" + digits(12)
        else -> null
    }

    /** Guarda el pedido (nuevo o actualizado) y avisa a quien lo esté siguiendo. */
    private fun store(t: TenantState, order: Order) {
        val index = t.orders.indexOfFirst { it.id == order.id }
        if (index >= 0) t.orders[index] = order else t.orders.add(0, order)
        trackers[order.id]?.value = order
    }

    private fun findOrder(orderId: String): Pair<TenantState, Order> {
        fun search(): Pair<TenantState, Order>? {
            for (t in tenants) {
                val o = t.orders.firstOrNull { it.id == orderId }
                if (o != null) return t to o
            }
            return null
        }
        search()?.let { return it }
        // Puede ser un pedido de ejemplo de un tenant que aún no se ha sembrado.
        tenants.filter { !it.seeded }.forEach { seedOrders(it) }
        return search() ?: fail("No encontramos este pedido", "not_found")
    }

    private fun staffOrder(t: TenantState, orderId: String): Order =
        t.orders.firstOrNull { it.id == orderId } ?: fail("No encontramos este pedido", "not_found")

    // ─────────────────────────────── Público (comensal) ───────────────────────────────

    override suspend fun listPublicTenants(): List<TenantPublic> = op {
        tenants.filter { it.listed && (it.status == TenantStatus.ACTIVE || it.status == TenantStatus.TRIAL) }.map { it.info }
    }

    override suspend fun getTenant(slug: String): TenantPublic = op { publicTenant(slug).info }

    override suspend fun getMenu(slug: String): Menu = op { normalized(publicTenant(slug).menu) }

    override suspend fun createOrder(slug: String, request: CreateOrderRequest): CreateOrderResponse = op {
        val t = publicTenant(slug)
        if (request.lines.isEmpty()) fail("Tu pedido está vacío", "validation")
        if (t.info.orderTypes.isNotEmpty() && request.type !in t.info.orderTypes) fail("Este restaurante no acepta ese tipo de pedido", "validation")
        val categoryIds = t.menu.categories.map { it.id }.toSet()
        val lines = request.lines.map { req ->
            val item = t.menu.items.firstOrNull { it.id == req.itemId && it.categoryId in categoryIds }
                ?: fail("Un platillo de tu pedido ya no está en el menú", "validation")
            if (!item.available) fail("${item.name} ya no está disponible", "unavailable")
            if (req.quantity < 1) fail("La cantidad de ${item.name} debe ser al menos 1", "validation")
            val modifiers = mutableListOf<OrderLineModifier>()
            for (group in item.modifierGroups) {
                val chosen = group.options.filter { it.id in req.optionIds }
                val max = maxOf(group.maxSelect, group.minSelect, 1)
                if (chosen.size < group.minSelect) fail("Elige ${group.name.lowercase()} para ${item.name}", "validation")
                if (chosen.size > max) fail("Puedes elegir máximo $max en ${group.name.lowercase()} para ${item.name}", "validation")
                chosen.forEach { modifiers += OrderLineModifier(group.name, it.name, it.priceDeltaCents) }
            }
            OrderLine(item.id, item.name, req.quantity.coerceAtMost(99), item.priceCents, modifiers, req.notes.trim(), item.imageUrl)
        }
        val subtotal = lines.sumOf { it.lineTotalCents }
        val tip = request.tipCents.coerceAtLeast(0L)
        val order = Order(
            id = newId("ord"), tenantId = t.info.id, tenantSlug = t.info.slug, number = nextNumber(t),
            status = OrderStatus.PENDING_PAYMENT, type = request.type, channel = OrderChannel.DIRECT, lines = lines,
            subtotalCents = subtotal, tipCents = tip, deliveryFeeCents = 0L, totalCents = subtotal + tip,
            customerName = request.customerName.trim(), customerPhone = request.customerPhone.trim(),
            tableLabel = request.tableLabel.trim(), address = request.address.trim(), notes = request.notes.trim(),
            createdAt = nowMillis(),
            etaMinutes = t.info.etaMinutes + if (request.type == OrderType.DELIVERY) 10 else 0,
            payment = PaymentInfo(
                method = request.paymentMethod, status = PaymentStatus.PENDING,
                marketplaceFeeCents = subtotal * planOf(t).feeBps / 10_000,
                reference = referenceFor(request.paymentMethod),
            ),
        )
        store(t, order)
        CreateOrderResponse(order = order, checkoutUrl = null, sandbox = true)
    }

    override suspend fun simulatePayment(orderId: String): Order = op {
        val (t, order) = findOrder(orderId)
        when (order.status) {
            OrderStatus.PENDING_PAYMENT -> {
                val paid = order.copy(
                    status = OrderStatus.PAID, paidAt = nowMillis(),
                    payment = (order.payment ?: PaymentInfo(PaymentMethod.CARD)).copy(
                        status = PaymentStatus.APPROVED, providerPaymentId = newId("demo-pay"),
                    ),
                )
                store(t, paid)
                t.events.tryEmit(RealtimeEvent.OrderCreated(paid))
                paid
            }
            OrderStatus.CANCELLED, OrderStatus.REFUNDED -> fail("Este pedido ya no se puede pagar", "conflict")
            else -> order
        }
    }

    override suspend fun getOrder(orderId: String): Order = op { findOrder(orderId).second }

    override fun trackOrder(orderId: String): Flow<Order> = flow {
        val tracker = lock.withLock {
            val current = findOrder(orderId).second
            trackers.getOrPut(orderId) { MutableStateFlow(current) }
        }
        emitAll(tracker)
    }

    override suspend fun listPlans(): List<Plan> = op { plans.toList() }

    override suspend fun signup(request: SignupRequest): SignupResponse = op {
        val (tenant, account) = registerTenant(
            name = request.restaurantName, slugInput = "", cuisine = "", ownerName = request.ownerName,
            ownerEmail = request.email, password = request.password, planId = request.planId, autoSlug = true,
        )
        val newSession = sessionFor(account)
        _session.value = newSession
        SignupResponse(tenant = summary(tenant), session = newSession, subscriptionCheckoutUrl = null)
    }

    private fun registerTenant(
        name: String, slugInput: String, cuisine: String, ownerName: String, ownerEmail: String,
        password: String, planId: String, autoSlug: Boolean,
    ): Pair<TenantState, DemoAccount> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) fail("Escribe el nombre del restaurante", "validation")
        if (ownerName.isBlank()) fail("Escribe el nombre de la persona responsable", "validation")
        val email = ownerEmail.trim().lowercase()
        if (email.length < 5 || !email.contains('@')) fail("Escribe un correo válido", "validation")
        if (password.length < 6) fail("La contraseña debe tener al menos 6 caracteres", "validation")
        if (plans.none { it.id == planId }) fail("El plan elegido no existe", "validation")
        if (accounts.any { it.email == email }) fail("Ya existe una cuenta con ese correo", "conflict")
        var slug = slugify(slugInput.ifBlank { cleanName })
        if (slug.isEmpty()) fail("El enlace del restaurante no es válido", "validation")
        if (tenants.any { it.info.slug == slug }) {
            if (!autoSlug) fail("Ya existe un restaurante con ese enlace", "conflict")
            var n = 2
            while (tenants.any { it.info.slug == "$slug-$n" }) n++
            slug = "$slug-$n"
        }
        val info = TenantPublic(id = newId("t"), slug = slug, name = cleanName, cuisine = cuisine.trim())
        val state = TenantState(
            info = info, status = TenantStatus.TRIAL, planId = planId, createdAt = nowMillis(),
            menu = starterMenu(), mpUserId = null,
        )
        tenants += state
        val account = DemoAccount(newId("u"), email, ownerName.trim(), Role.OWNER, info.id, password)
        accounts += account
        return state to account
    }

    // ─────────────────────────────── Sesión ───────────────────────────────

    override suspend fun login(email: String, password: String): UserSession = op {
        val clean = email.trim().lowercase()
        val account = accounts.firstOrNull { it.email == clean }
        if (account == null || account.password != password) fail("Correo o contraseña incorrectos", "unauthorized")
        val newSession = sessionFor(account)
        _session.value = newSession
        newSession
    }

    override fun logout() {
        _session.value = null
    }

    // ─────────────────────────────── Restaurante ───────────────────────────────

    // Lectura de la marca: se permite a cualquier rol del restaurante (es información pública del tenant).
    override suspend fun getBrand(): TenantPublic = op { staffTenant(ordersOnly = true).info }

    override suspend fun updateBrand(request: UpdateBrandRequest): TenantPublic = op {
        val t = staffTenant(ordersOnly = false)
        val name = request.name.trim()
        if (name.isEmpty()) fail("Escribe el nombre del restaurante", "validation")
        t.info = t.info.copy(
            name = name, tagline = request.tagline.trim(), description = request.description.trim(),
            address = request.address.trim(), phone = request.phone.trim(), theme = request.theme,
            customDomain = request.customDomain?.trim()?.takeIf { it.isNotEmpty() },
        )
        t.info
    }

    override suspend fun getAdminMenu(): Menu = op { normalized(staffTenant(ordersOnly = false).menu) }

    override suspend fun upsertCategory(category: MenuCategory): MenuCategory = op {
        val t = staffTenant(ordersOnly = false)
        val name = category.name.trim()
        if (name.isEmpty()) fail("Escribe el nombre de la categoría", "validation")
        val saved = category.copy(id = category.id.ifBlank { newId("cat") }, name = name)
        val exists = t.menu.categories.any { it.id == saved.id }
        val categories = if (exists) t.menu.categories.map { if (it.id == saved.id) saved else it } else t.menu.categories + saved
        t.menu = t.menu.copy(categories = categories.sortedBy { it.sortOrder })
        saved
    }

    override suspend fun deleteCategory(id: String) {
        op {
            val t = staffTenant(ordersOnly = false)
            if (t.menu.categories.none { it.id == id }) fail("La categoría ya no existe", "not_found")
            t.menu = Menu(
                categories = t.menu.categories.filter { it.id != id },
                items = t.menu.items.filter { it.categoryId != id },
            )
        }
    }

    override suspend fun upsertItem(item: MenuItem): MenuItem = op {
        val t = staffTenant(ordersOnly = false)
        val name = item.name.trim()
        if (name.isEmpty()) fail("Escribe el nombre del platillo", "validation")
        if (item.priceCents < 0) fail("El precio no puede ser negativo", "validation")
        if (t.menu.categories.none { it.id == item.categoryId }) fail("Elige una categoría para el platillo", "validation")
        val saved = item.copy(id = item.id.ifBlank { newId("itm") }, name = name)
        val exists = t.menu.items.any { it.id == saved.id }
        val items = if (exists) t.menu.items.map { if (it.id == saved.id) saved else it } else t.menu.items + saved
        t.menu = t.menu.copy(items = items)
        saved
    }

    override suspend fun deleteItem(id: String) {
        op {
            val t = staffTenant(ordersOnly = false)
            if (t.menu.items.none { it.id == id }) fail("El platillo ya no existe", "not_found")
            t.menu = t.menu.copy(items = t.menu.items.filter { it.id != id })
        }
    }

    override suspend fun setItemAvailability(id: String, available: Boolean): MenuItem = op {
        val t = staffTenant(ordersOnly = false)
        val current = t.menu.items.firstOrNull { it.id == id } ?: fail("El platillo ya no existe", "not_found")
        val saved = current.copy(available = available)
        t.menu = t.menu.copy(items = t.menu.items.map { if (it.id == id) saved else it })
        saved
    }

    override suspend fun listOrders(activeOnly: Boolean): List<Order> = op {
        val t = staffTenant(ordersOnly = true)
        seedOrders(t)
        if (activeOnly) {
            t.orders.filter { it.status in KITCHEN_STATUSES }.sortedBy { it.paidAt ?: it.createdAt }
        } else {
            t.orders.sortedByDescending { it.createdAt }.take(100)
        }
    }

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Order = op {
        val t = staffTenant(ordersOnly = true)
        seedOrders(t)
        val current = staffOrder(t, orderId)
        val now = nowMillis()
        val approved = current.payment?.let { if (it.status == PaymentStatus.PENDING) it.copy(status = PaymentStatus.APPROVED) else it }
        val updated = when (status) {
            OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED -> current.copy(status = status)
            OrderStatus.PAID -> current.copy(status = status, paidAt = current.paidAt ?: now, payment = approved)
            OrderStatus.PREPARING -> current.copy(status = status, paidAt = current.paidAt ?: now, preparingAt = now, payment = approved)
            OrderStatus.READY -> current.copy(
                status = status, paidAt = current.paidAt ?: now, preparingAt = current.preparingAt ?: now, readyAt = now, payment = approved,
            )
            OrderStatus.COMPLETED -> current.copy(
                status = status, paidAt = current.paidAt ?: now, preparingAt = current.preparingAt ?: now,
                readyAt = current.readyAt ?: now, completedAt = now, payment = approved,
            )
            OrderStatus.REFUNDED -> current.copy(status = status, payment = current.payment?.copy(status = PaymentStatus.REFUNDED))
        }
        store(t, updated)
        val entersKitchen = current.status == OrderStatus.PENDING_PAYMENT && status in KITCHEN_STATUSES
        t.events.tryEmit(if (entersKitchen) RealtimeEvent.OrderCreated(updated) else RealtimeEvent.OrderUpdated(updated))
        updated
    }

    override suspend fun refundOrder(orderId: String): Order = op {
        val t = staffTenant(ordersOnly = true)
        seedOrders(t)
        val current = staffOrder(t, orderId)
        when (current.status) {
            OrderStatus.REFUNDED -> current
            OrderStatus.PENDING_PAYMENT -> fail("Este pedido aún no tiene un pago que reembolsar", "conflict")
            else -> {
                val refunded = current.copy(
                    status = OrderStatus.REFUNDED,
                    payment = current.payment?.copy(status = PaymentStatus.REFUNDED),
                )
                store(t, refunded)
                t.events.tryEmit(RealtimeEvent.OrderUpdated(refunded))
                refunded
            }
        }
    }

    override fun kitchenEvents(): Flow<RealtimeEvent> = flow {
        val t = lock.withLock { staffTenant(ordersOnly = true).also { seedOrders(it) } }
        emitAll(t.events)
    }

    override suspend fun tenantStats(): TenantStats = op {
        val t = staffTenant(ordersOnly = false)
        seedOrders(t)
        val now = nowMillis()
        val sold = t.orders.filter { it.status in SOLD_STATUSES }
        val today = sold.filter { (it.paidAt ?: it.createdAt) >= now - DAY_MS }
        val salesToday = today.sumOf { it.totalCents }
        val prepTimes = sold.mapNotNull { o ->
            val paid = o.paidAt
            val ready = o.readyAt
            if (paid != null && ready != null && ready > paid) ready - paid else null
        }
        // Serie de 7 días: los 6 anteriores son deterministas por tenant y día; hoy = ventas reales en memoria. Valores en pesos.
        val dayIndex = (now - 6 * 3_600_000L) / DAY_MS          // día civil en horario del centro de México
        val seedOffset = t.info.slug.sumOf { it.code }
        val series = (6 downTo 0).map { daysBack ->
            val day = dayIndex - daysBack
            val weekday = (((day + 3) % 7 + 7) % 7).toInt()         // 0 = lunes
            val value = if (daysBack == 0) {
                salesToday / 100.0
            } else {
                val wobble = 0.9 + ((day * 37 + seedOffset) % 21).toInt() / 100.0
                ((t.dailyBaseCents * WEEKDAY_FACTORS[weekday] * wobble) / 100.0).roundToInt().toDouble()
            }
            MetricPoint(WEEKDAY_LABELS[weekday], value)
        }
        val top = sold.flatMap { it.lines }
            .groupBy { it.name }
            .map { (name, lines) -> TopItem(name, lines.sumOf { it.quantity }, lines.sumOf { it.lineTotalCents }) }
            .sortedWith(compareByDescending<TopItem> { it.quantity }.thenByDescending { it.revenueCents })
            .take(5)
        TenantStats(
            ordersToday = today.size,
            salesTodayCents = salesToday,
            avgTicketCents = if (today.isEmpty()) 0L else salesToday / today.size,
            avgPrepMinutes = if (prepTimes.isEmpty()) 0 else (prepTimes.average() / 60_000.0).roundToInt(),
            salesSeries = series,
            topItems = top,
        )
    }

    override suspend fun listStaff(): List<StaffMember> = op {
        val t = staffTenant(ordersOnly = false)
        accounts.filter { it.tenantId == t.info.id }.map { StaffMember(it.id, it.name, it.email, it.role) }
    }

    private fun mpStatus(t: TenantState): MpConnectionStatus = MpConnectionStatus(
        connected = t.mpUserId != null, mpUserId = t.mpUserId, liveMode = false, feeBps = planOf(t).feeBps, oauthUrl = null,
    )

    private fun demoSellerId(t: TenantState): String =
        if (t.info.slug == "brasa-demo") "demo-seller" else "demo-seller-" + t.info.slug.removeSuffix("-demo")

    override suspend fun mercadoPagoStatus(): MpConnectionStatus = op { mpStatus(staffTenant(ordersOnly = false)) }

    override suspend fun connectMercadoPago(): MpConnectionStatus = op {
        val t = staffTenant(ordersOnly = false)
        if (t.mpUserId == null) t.mpUserId = demoSellerId(t)
        mpStatus(t)
    }

    override suspend fun disconnectMercadoPago(): MpConnectionStatus = op {
        val t = staffTenant(ordersOnly = false)
        t.mpUserId = null
        mpStatus(t)
    }

    private fun integrationsOf(t: TenantState): List<IntegrationStatus> = listOf(
        IntegrationStatus(
            IntegrationProvider.MERCADO_PAGO, "Mercado Pago",
            "Cobra con tarjeta, efectivo en OXXO, SPEI o saldo de Mercado Pago. El dinero llega directo a tu cuenta.",
            "Pagos", t.mpUserId != null, "sandbox",
        ),
        IntegrationStatus(
            IntegrationProvider.LALAMOVE, "Lalamove",
            "Pide un repartidor al momento para tus pedidos a domicilio y comparte el seguimiento con tu cliente.",
            "Reparto", IntegrationProvider.LALAMOVE in t.connected, "mock",
        ),
        IntegrationStatus(
            IntegrationProvider.UBER_EATS, "Uber Eats",
            "Recibe los pedidos de Uber Eats en la misma pantalla de cocina, sin otra tableta.",
            "Marketplaces", IntegrationProvider.UBER_EATS in t.connected, "mock",
        ),
        IntegrationStatus(
            IntegrationProvider.RAPPI, "Rappi",
            "Los pedidos de Rappi entran directo a tu cocina junto con los de tu menú digital.",
            "Marketplaces", IntegrationProvider.RAPPI in t.connected, "mock",
        ),
        IntegrationStatus(
            IntegrationProvider.DIDI_FOOD, "DiDi Food",
            "Centraliza los pedidos de DiDi Food y lleva un solo control de ventas.",
            "Marketplaces", IntegrationProvider.DIDI_FOOD in t.connected, "mock",
        ),
    )

    override suspend fun listIntegrations(): List<IntegrationStatus> = op { integrationsOf(staffTenant(ordersOnly = false)) }

    override suspend fun toggleIntegration(provider: IntegrationProvider): List<IntegrationStatus> = op {
        val t = staffTenant(ordersOnly = false)
        if (provider == IntegrationProvider.MERCADO_PAGO) {
            t.mpUserId = if (t.mpUserId == null) demoSellerId(t) else null
        } else {
            if (!t.connected.remove(provider)) t.connected += provider
            syncSimulators(t)
        }
        integrationsOf(t)
    }

    // ─────────────────────────────── Tráfico simulado de marketplaces ───────────────────────────────

    /** Arranca o detiene el generador de pedidos de cada marketplace según esté conectado. */
    private fun syncSimulators(t: TenantState) {
        for (provider in MARKETPLACES) {
            val running = t.simulators[provider]?.isActive == true
            if (provider in t.connected && !running) {
                t.simulators[provider] = scope.launch {
                    while (isActive) {
                        delay(Random.nextLong(40_000L, 70_001L))
                        lock.withLock {
                            if (provider in t.connected) injectMarketplaceOrder(t, provider)
                        }
                    }
                }
            } else if (provider !in t.connected) {
                t.simulators.remove(provider)?.cancel()
            }
        }
    }

    private fun injectMarketplaceOrder(t: TenantState, provider: IntegrationProvider) {
        if (t.status == TenantStatus.SUSPENDED) return
        if (t.orders.count { it.status in KITCHEN_STATUSES } >= MAX_ACTIVE_FOR_SIMULATION) return
        val categoryIds = t.menu.categories.map { it.id }.toSet()
        val available = t.menu.items.filter { it.available && it.categoryId in categoryIds }
        if (available.isEmpty()) return
        val channel = when (provider) {
            IntegrationProvider.UBER_EATS -> OrderChannel.UBER_EATS
            IntegrationProvider.RAPPI -> OrderChannel.RAPPI
            IntegrationProvider.DIDI_FOOD -> OrderChannel.DIDI_FOOD
            else -> return
        }
        val lines = available.shuffled().take(Random.nextInt(1, 4)).map { item ->
            val modifiers = item.modifierGroups.filter { it.minSelect > 0 && it.options.isNotEmpty() }.flatMap { g ->
                g.options.shuffled().take(g.minSelect.coerceAtMost(g.options.size)).map { o -> OrderLineModifier(g.name, o.name, o.priceDeltaCents) }
            }
            OrderLine(item.id, item.name, if (Random.nextInt(4) == 0) 2 else 1, item.priceCents, modifiers, "", item.imageUrl)
        }
        val subtotal = lines.sumOf { it.lineTotalCents }
        val now = nowMillis()
        val order = Order(
            id = newId("ord"), tenantId = t.info.id, tenantSlug = t.info.slug, number = nextNumber(t),
            status = OrderStatus.PAID, type = OrderType.DELIVERY, channel = channel, lines = lines,
            subtotalCents = subtotal, totalCents = subtotal,
            customerName = MARKETPLACE_NAMES[Random.nextInt(MARKETPLACE_NAMES.size)],
            address = "Calle Ejemplo ${Random.nextInt(10, 400)}, Col. Demostración",
            createdAt = now, paidAt = now, etaMinutes = t.info.etaMinutes + 10,
            payment = PaymentInfo(
                method = PaymentMethod.CARD, status = PaymentStatus.APPROVED,
                provider = provider.name.lowercase(), providerPaymentId = newId("mkt"),
            ),
        )
        store(t, order)
        t.events.tryEmit(RealtimeEvent.OrderCreated(order))
    }

    // ─────────────────────────────── Operador ───────────────────────────────

    private fun summary(t: TenantState): TenantSummary {
        val plan = planOf(t)
        val owner = accounts.firstOrNull { it.tenantId == t.info.id && it.role == Role.OWNER }
        val sold = t.orders.filter { it.status in SOLD_STATUSES && !it.id.startsWith("hist-") && !it.id.startsWith("demo-") }
        return TenantSummary(
            id = t.info.id, slug = t.info.slug, name = t.info.name, status = t.status,
            planId = plan.id, planName = plan.name,
            ownerName = owner?.name.orEmpty(), ownerEmail = owner?.email.orEmpty(), createdAt = t.createdAt,
            mpConnected = t.mpUserId != null,
            orders30d = t.baseOrders30d + sold.size,
            gmv30dCents = t.baseGmv30dCents + sold.sumOf { it.totalCents },
            mrrCents = if (t.status == TenantStatus.ACTIVE) plan.priceCents else 0L,
            primary = t.info.theme.primary,
        )
    }

    private fun operatorTenant(tenantId: String): TenantState =
        tenants.firstOrNull { it.info.id == tenantId } ?: fail("No encontramos este restaurante", "not_found")

    override suspend fun operatorMetrics(): OperatorMetrics = op {
        requireOperator()
        val now = nowMillis()
        val summaries = tenants.map { summary(it) }
        val active = tenants.filter { it.status == TenantStatus.ACTIVE }
        fun mrrAt(monthsAgo: Int): Long {
            val cutoff = now - monthsAgo * 30 * DAY_MS
            return active.filter { it.createdAt <= cutoff }.sumOf { planOf(it).priceCents }
        }
        val last = MONTH_LABELS.size - 1
        val mrrSeries = MONTH_LABELS.mapIndexed { i, label -> MetricPoint(label, mrrAt(last - i) / 100.0) }
        val signupsSeries = MONTH_LABELS.mapIndexed { i, label ->
            val monthsAgo = last - i
            val from = now - (monthsAgo + 1) * 30 * DAY_MS
            val to = now - monthsAgo * 30 * DAY_MS
            MetricPoint(label, tenants.count { it.createdAt > from && it.createdAt <= to }.toDouble())
        }
        val mrr = mrrAt(0)
        val previous = mrrAt(1)
        val total = tenants.size.coerceAtLeast(1)
        val suspended = tenants.count { it.status == TenantStatus.SUSPENDED }
        OperatorMetrics(
            mrrCents = mrr,
            mrrDeltaPct = if (previous > 0) round1((mrr - previous) * 100.0 / previous) else 0.0,
            activeTenants = active.size,
            trialTenants = tenants.count { it.status == TenantStatus.TRIAL },
            suspendedTenants = suspended,
            churnPct = round1(suspended * 100.0 / total),
            activationPct = round1(tenants.count { it.mpUserId != null } * 100.0 / total),
            gmv30dCents = summaries.sumOf { it.gmv30dCents },
            platformFees30dCents = tenants.zip(summaries).sumOf { (t, s) -> s.gmv30dCents * planOf(t).feeBps / 10_000 },
            orders30d = summaries.sumOf { it.orders30d },
            mrrSeries = mrrSeries,
            signupsSeries = signupsSeries,
        )
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    override suspend fun listTenants(): List<TenantSummary> = op {
        requireOperator()
        tenants.sortedByDescending { it.createdAt }.map { summary(it) }
    }

    override suspend fun createTenant(request: CreateTenantRequest): TenantSummary = op {
        requireOperator()
        val (tenant, _) = registerTenant(
            name = request.name, slugInput = request.slug, cuisine = request.cuisine, ownerName = request.ownerName,
            ownerEmail = request.ownerEmail, password = request.ownerPassword, planId = request.planId, autoSlug = false,
        )
        summary(tenant)
    }

    override suspend fun setTenantStatus(tenantId: String, status: TenantStatus): TenantSummary = op {
        requireOperator()
        val t = operatorTenant(tenantId)
        t.status = status
        summary(t)
    }

    override suspend fun assignPlan(tenantId: String, planId: String): TenantSummary = op {
        requireOperator()
        val t = operatorTenant(tenantId)
        if (plans.none { it.id == planId }) fail("El plan elegido no existe", "validation")
        t.planId = planId
        summary(t)
    }

    override suspend fun upsertPlan(plan: Plan): Plan = op {
        requireOperator()
        val name = plan.name.trim()
        if (name.isEmpty()) fail("Escribe el nombre del plan", "validation")
        if (plan.priceCents < 0) fail("El precio no puede ser negativo", "validation")
        val saved = plan.copy(id = plan.id.ifBlank { newId("plan") }, name = name)
        val index = plans.indexOfFirst { it.id == saved.id }
        if (index >= 0) plans[index] = saved else plans += saved
        saved
    }
}
