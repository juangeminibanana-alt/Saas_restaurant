package server.data

import app.shared.api.AppJson
import app.shared.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import server.db.exec
import server.db.longOrNull
import server.db.query
import server.db.queryOne
import java.sql.Connection
import java.sql.ResultSet
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zona horaria de operación (México). */
val AppZone: ZoneId = ZoneId.of("America/Mexico_City")

private val stringList = ListSerializer(String.serializer())
private val hoursList = ListSerializer(OpeningHours.serializer())
private val orderTypeList = ListSerializer(OrderType.serializer())
private val groupList = ListSerializer(ModifierGroup.serializer())
private val lineList = ListSerializer(OrderLine.serializer())

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == value } ?: default

// ───────────────────────── Tenants ─────────────────────────

/** Fila de tenant con lo que no es público. */
data class TenantRow(val public: TenantPublic, val status: TenantStatus, val planId: String, val createdAt: Long)

const val TENANT_COLUMNS =
    "id::text AS id, slug, name, tagline, description, cuisine, theme::text AS theme, currency, address, phone, hours::text AS hours, " +
        "order_types::text AS order_types, eta_minutes, rating, custom_domain, status, plan_id, created_at"

fun isOpenNow(hours: List<OpeningHours>): Boolean {
    if (hours.isEmpty()) return true
    val now = ZonedDateTime.now(AppZone)
    val day = now.dayOfWeek.value
    val time = now.toLocalTime()
    fun parse(s: String): LocalTime? = try { LocalTime.parse(s) } catch (_: Exception) { null }
    return hours.any { h ->
        val open = parse(h.open) ?: return@any false
        val close = parse(h.close) ?: return@any false
        if (close > open) h.day == day && time >= open && time < close
        else (h.day == day && time >= open) || (h.day == DayOfWeek.of(day).minus(1).value && time < close)
    }
}

fun ResultSet.toTenantRow(): TenantRow {
    val hours = try { AppJson.decodeFromString(hoursList, getString("hours")) } catch (_: Exception) { emptyList() }
    val status = enumOr(getString("status"), TenantStatus.TRIAL)
    val public = TenantPublic(
        id = getString("id"),
        slug = getString("slug"),
        name = getString("name"),
        tagline = getString("tagline"),
        description = getString("description"),
        cuisine = getString("cuisine"),
        theme = try { AppJson.decodeFromString(BrandTheme.serializer(), getString("theme")) } catch (_: Exception) { BrandTheme() },
        currency = getString("currency"),
        address = getString("address"),
        phone = getString("phone"),
        hours = hours,
        isOpen = isOpenNow(hours),
        orderTypes = try { AppJson.decodeFromString(orderTypeList, getString("order_types")) } catch (_: Exception) { OrderType.entries },
        etaMinutes = getInt("eta_minutes"),
        rating = getDouble("rating"),
        customDomain = getString("custom_domain"),
        acceptsPayments = status == TenantStatus.ACTIVE || status == TenantStatus.TRIAL,
    )
    return TenantRow(public, status, getString("plan_id"), getLong("created_at"))
}

fun Connection.tenantBySlug(slug: String): TenantRow? =
    queryOne("SELECT $TENANT_COLUMNS FROM tenants WHERE slug = ?", slug) { it.toTenantRow() }

fun Connection.tenantById(id: String): TenantRow? =
    queryOne("SELECT $TENANT_COLUMNS FROM tenants WHERE id = ?::uuid", id) { it.toTenantRow() }

fun Connection.insertTenant(t: TenantPublic, status: TenantStatus, planId: String, createdAt: Long) {
    exec(
        "INSERT INTO tenants (id, slug, name, tagline, description, cuisine, theme, currency, address, phone, hours, order_types, eta_minutes, rating, custom_domain, status, plan_id, created_at) " +
            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)",
        t.id, t.slug, t.name, t.tagline, t.description, t.cuisine, AppJson.encodeToString(BrandTheme.serializer(), t.theme), t.currency,
        t.address, t.phone, AppJson.encodeToString(hoursList, t.hours), AppJson.encodeToString(orderTypeList, t.orderTypes),
        t.etaMinutes, t.rating, t.customDomain, status.name, planId, createdAt,
    )
}

// ───────────────────────── Planes ─────────────────────────

fun ResultSet.toPlan(): Plan = Plan(
    id = getString("id"),
    name = getString("name"),
    priceCents = getLong("price_cents"),
    description = getString("description"),
    features = try { AppJson.decodeFromString(stringList, getString("features")) } catch (_: Exception) { emptyList() },
    feeBps = getInt("fee_bps"),
    highlighted = getBoolean("highlighted"),
    maxLocations = getInt("max_locations"),
)

const val PLAN_COLUMNS = "id, name, price_cents, description, features::text AS features, fee_bps, highlighted, max_locations"

fun Connection.listPlans(): List<Plan> = query("SELECT $PLAN_COLUMNS FROM plans ORDER BY sort_order, price_cents") { it.toPlan() }

fun Connection.planById(id: String): Plan? = queryOne("SELECT $PLAN_COLUMNS FROM plans WHERE id = ?", id) { it.toPlan() }

fun Connection.upsertPlan(p: Plan, sortOrder: Int? = null) {
    exec(
        "INSERT INTO plans (id, name, price_cents, description, features, fee_bps, highlighted, max_locations, sort_order) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, COALESCE(?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM plans))) " +
            "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, price_cents = EXCLUDED.price_cents, description = EXCLUDED.description, " +
            "features = EXCLUDED.features, fee_bps = EXCLUDED.fee_bps, highlighted = EXCLUDED.highlighted, max_locations = EXCLUDED.max_locations",
        p.id, p.name, p.priceCents, p.description, AppJson.encodeToString(stringList, p.features), p.feeBps, p.highlighted, p.maxLocations, sortOrder,
    )
}

// ───────────────────────── Menú ─────────────────────────

fun ResultSet.toCategory(): MenuCategory = MenuCategory(getString("id"), getString("name"), getString("description"), getInt("sort_order"))

fun ResultSet.toMenuItem(): MenuItem = MenuItem(
    id = getString("id"),
    categoryId = getString("category_id"),
    name = getString("name"),
    description = getString("description"),
    priceCents = getLong("price_cents"),
    imageUrl = getString("image_url"),
    tags = try { AppJson.decodeFromString(stringList, getString("tags")) } catch (_: Exception) { emptyList() },
    available = getBoolean("available"),
    modifierGroups = try { AppJson.decodeFromString(groupList, getString("modifier_groups")) } catch (_: Exception) { emptyList() },
    prepMinutes = getInt("prep_minutes"),
    sortOrder = getInt("sort_order"),
)

const val ITEM_COLUMNS =
    "id, category_id, name, description, price_cents, image_url, tags::text AS tags, available, modifier_groups::text AS modifier_groups, prep_minutes, sort_order"

/** Menú del tenant. Las consultas filtran por tenant_id además de RLS (sirve igual con el rol admin). */
fun Connection.loadMenu(tenantId: String, onlyAvailable: Boolean): Menu {
    val categories = query(
        "SELECT id, name, description, sort_order FROM menu_categories WHERE tenant_id = ?::uuid ORDER BY sort_order, name", tenantId,
    ) { it.toCategory() }
    val ids = categories.map { it.id }.toSet()
    val items = query(
        "SELECT $ITEM_COLUMNS FROM menu_items WHERE tenant_id = ?::uuid" + (if (onlyAvailable) " AND available" else "") + " ORDER BY sort_order, name",
        tenantId,
    ) { it.toMenuItem() }.filter { it.categoryId in ids }
    return Menu(categories, items)
}

fun Connection.menuItem(tenantId: String, id: String): MenuItem? =
    queryOne("SELECT $ITEM_COLUMNS FROM menu_items WHERE tenant_id = ?::uuid AND id = ?", tenantId, id) { it.toMenuItem() }

fun Connection.upsertCategory(tenantId: String, c: MenuCategory) {
    exec(
        "INSERT INTO menu_categories (tenant_id, id, name, description, sort_order) VALUES (?::uuid, ?, ?, ?, ?) " +
            "ON CONFLICT (tenant_id, id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, sort_order = EXCLUDED.sort_order",
        tenantId, c.id, c.name, c.description, c.sortOrder,
    )
}

fun Connection.upsertItem(tenantId: String, i: MenuItem) {
    exec(
        "INSERT INTO menu_items (tenant_id, id, category_id, name, description, price_cents, image_url, tags, available, modifier_groups, prep_minutes, sort_order) " +
            "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?) " +
            "ON CONFLICT (tenant_id, id) DO UPDATE SET category_id = EXCLUDED.category_id, name = EXCLUDED.name, description = EXCLUDED.description, " +
            "price_cents = EXCLUDED.price_cents, image_url = EXCLUDED.image_url, tags = EXCLUDED.tags, available = EXCLUDED.available, " +
            "modifier_groups = EXCLUDED.modifier_groups, prep_minutes = EXCLUDED.prep_minutes, sort_order = EXCLUDED.sort_order",
        tenantId, i.id, i.categoryId, i.name, i.description, i.priceCents, i.imageUrl, AppJson.encodeToString(stringList, i.tags), i.available,
        AppJson.encodeToString(groupList, i.modifierGroups), i.prepMinutes, i.sortOrder,
    )
}

// ───────────────────────── Pedidos ─────────────────────────

const val ORDER_COLUMNS =
    "id, tenant_id::text AS tenant_id, tenant_slug, number, status, type, channel, lines::text AS lines, subtotal_cents, tip_cents, delivery_fee_cents, " +
        "total_cents, customer_name, customer_phone, table_label, address, notes, created_at, paid_at, preparing_at, ready_at, completed_at, " +
        "eta_minutes, payment::text AS payment"

fun ResultSet.toOrder(): Order = Order(
    id = getString("id"),
    tenantId = getString("tenant_id"),
    tenantSlug = getString("tenant_slug"),
    number = getString("number"),
    status = enumOr(getString("status"), OrderStatus.PENDING_PAYMENT),
    type = enumOr(getString("type"), OrderType.PICKUP),
    channel = enumOr(getString("channel"), OrderChannel.DIRECT),
    lines = try { AppJson.decodeFromString(lineList, getString("lines")) } catch (_: Exception) { emptyList() },
    subtotalCents = getLong("subtotal_cents"),
    tipCents = getLong("tip_cents"),
    deliveryFeeCents = getLong("delivery_fee_cents"),
    totalCents = getLong("total_cents"),
    customerName = getString("customer_name"),
    customerPhone = getString("customer_phone"),
    tableLabel = getString("table_label"),
    address = getString("address"),
    notes = getString("notes"),
    createdAt = getLong("created_at"),
    paidAt = longOrNull("paid_at"),
    preparingAt = longOrNull("preparing_at"),
    readyAt = longOrNull("ready_at"),
    completedAt = longOrNull("completed_at"),
    etaMinutes = getInt("eta_minutes"),
    payment = getString("payment")?.let { try { AppJson.decodeFromString(PaymentInfo.serializer(), it) } catch (_: Exception) { null } },
)

fun Connection.orderById(id: String, forUpdate: Boolean = false): Order? =
    queryOne("SELECT $ORDER_COLUMNS FROM orders WHERE id = ?" + (if (forUpdate) " FOR UPDATE" else ""), id) { it.toOrder() }

fun Connection.insertOrder(o: Order) {
    exec(
        "INSERT INTO orders (id, tenant_id, tenant_slug, number, status, type, channel, lines, subtotal_cents, tip_cents, delivery_fee_cents, total_cents, " +
            "customer_name, customer_phone, table_label, address, notes, created_at, paid_at, preparing_at, ready_at, completed_at, eta_minutes, payment) " +
            "VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
        o.id, o.tenantId, o.tenantSlug, o.number, o.status.name, o.type.name, o.channel.name, AppJson.encodeToString(lineList, o.lines),
        o.subtotalCents, o.tipCents, o.deliveryFeeCents, o.totalCents, o.customerName, o.customerPhone, o.tableLabel, o.address, o.notes,
        o.createdAt, o.paidAt, o.preparingAt, o.readyAt, o.completedAt, o.etaMinutes,
        o.payment?.let { AppJson.encodeToString(PaymentInfo.serializer(), it) },
    )
}

/** Persiste los campos mutables de un pedido (estado, tiempos y pago). */
fun Connection.saveOrderState(o: Order) {
    exec(
        "UPDATE orders SET status = ?, paid_at = ?, preparing_at = ?, ready_at = ?, completed_at = ?, payment = ?::jsonb WHERE id = ?",
        o.status.name, o.paidAt, o.preparingAt, o.readyAt, o.completedAt,
        o.payment?.let { AppJson.encodeToString(PaymentInfo.serializer(), it) }, o.id,
    )
}

/** Siguiente número de pedido del restaurante: "A-042". */
fun Connection.nextOrderNumber(tenantId: String): String {
    val n = queryOne(
        "INSERT INTO order_counters (tenant_id, last_number) VALUES (?::uuid, 1) " +
            "ON CONFLICT (tenant_id) DO UPDATE SET last_number = order_counters.last_number + 1 RETURNING last_number",
        tenantId,
    ) { it.getInt(1) } ?: 1
    return formatOrderNumber(n)
}

fun formatOrderNumber(n: Int): String {
    val letter = 'A' + ((n / 1000) % 26)
    return letter + "-" + (n % 1000).toString().padStart(3, '0')
}

// ───────────────────────── Usuarios ─────────────────────────

data class UserRow(val id: String, val tenantId: String?, val email: String, val name: String, val role: Role, val passwordHash: String)

fun ResultSet.toUserRow(): UserRow = UserRow(
    id = getString("id"),
    tenantId = getString("tenant_id"),
    email = getString("email"),
    name = getString("name"),
    role = enumOr(getString("role"), Role.KITCHEN),
    passwordHash = getString("password_hash"),
)

fun Connection.userByEmail(email: String): UserRow? =
    queryOne("SELECT id, tenant_id::text AS tenant_id, email, name, role, password_hash FROM users WHERE email = ?", email.trim().lowercase()) { it.toUserRow() }

fun Connection.insertUser(id: String, tenantId: String?, email: String, name: String, role: Role, passwordHash: String, createdAt: Long) {
    exec(
        "INSERT INTO users (id, tenant_id, email, name, role, password_hash, created_at) VALUES (?, ?::uuid, ?, ?, ?, ?, ?)",
        id, tenantId, email.trim().lowercase(), name, role.name, passwordHash, createdAt,
    )
}
