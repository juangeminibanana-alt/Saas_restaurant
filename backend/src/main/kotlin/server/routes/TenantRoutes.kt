package server.routes

import app.shared.api.*
import app.shared.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import server.ApiException
import server.auth.authenticated
import server.auth.tenantId
import server.badRequest
import server.conflict
import server.data.*
import server.db.Db
import server.db.exec
import server.db.query
import server.db.queryOne
import server.notFound
import server.orders.OrderService
import server.payments.Payments
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")
private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
private val DAY_LABELS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Estados que cuentan como venta. */
private const val SOLD = "('PAID', 'PREPARING', 'READY', 'COMPLETED')"

fun Route.tenantRoutes() {
    // ── Administración del restaurante: dueño y gerente.
    authenticated(Role.OWNER, Role.MANAGER) {
        put(ApiRoutes.TENANT_BRAND) {
            val id = call.tenantId
            val req = call.receive<UpdateBrandRequest>()
            if (req.name.isBlank()) badRequest("El nombre del restaurante no puede quedar vacío.")
            req.theme.primary?.let { if (!HEX_COLOR.matches(it)) badRequest("El color debe tener formato #RRGGBB.") }
            val domain = req.customDomain?.trim()?.lowercase()?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            if (domain != null && !DOMAIN.matches(domain)) badRequest("El dominio no es válido. Ejemplo: pedidos.mirestaurante.mx")
            if (domain != null) {
                val taken = Db.adminTx { c ->
                    c.queryOne("SELECT 1 FROM tenants WHERE lower(custom_domain) = ? AND id <> ?::uuid", domain, id) { true } != null
                }
                if (taken) conflict("Ese dominio ya está en uso por otro restaurante.")
            }
            val tenant = Db.tenantTx(id) { c ->
                c.exec(
                    "UPDATE tenants SET name = ?, tagline = ?, description = ?, address = ?, phone = ?, theme = ?::jsonb, custom_domain = ? WHERE id = ?::uuid",
                    req.name.trim().take(80), req.tagline.trim().take(140), req.description.trim().take(600), req.address.trim().take(240),
                    req.phone.trim().take(30), AppJson.encodeToString(BrandTheme.serializer(), req.theme), domain, id,
                )
                c.tenantById(id)
            } ?: notFound("No encontramos tu restaurante.")
            call.respond(tenant.public)
        }

        get(ApiRoutes.TENANT_MENU) {
            val id = call.tenantId
            call.respond(Db.tenantTx(id) { c -> c.loadMenu(id, onlyAvailable = false) })
        }

        put("/api/tenant/menu/categories/{id}") {
            val id = call.tenantId
            val catId = call.parameters["id"].orEmpty()
            val body = call.receive<MenuCategory>().copy(id = catId)
            if (catId.isBlank() || body.name.isBlank()) badRequest("Escribe el nombre de la categoría.")
            Db.tenantTx(id) { c -> c.upsertCategory(id, body.copy(name = body.name.trim().take(80))) }
            call.respond(body.copy(name = body.name.trim().take(80)))
        }

        delete("/api/tenant/menu/categories/{id}") {
            val id = call.tenantId
            val catId = call.parameters["id"].orEmpty()
            Db.tenantTx(id) { c -> c.exec("DELETE FROM menu_categories WHERE tenant_id = ?::uuid AND id = ?", id, catId) }
            call.respond(HttpStatusCode.NoContent)
        }

        put("/api/tenant/menu/items/{id}") {
            val id = call.tenantId
            val itemId = call.parameters["id"].orEmpty()
            val body = call.receive<MenuItem>().let { it.copy(id = itemId, name = it.name.trim().take(120)) }
            if (itemId.isBlank() || body.name.isBlank()) badRequest("Escribe el nombre del platillo.")
            if (body.priceCents < 0) badRequest("El precio no puede ser negativo.")
            if (body.modifierGroups.any { g -> g.minSelect < 0 || g.maxSelect < g.minSelect || g.options.any { it.priceDeltaCents < -body.priceCents } }) {
                badRequest("Revisa las opciones del platillo.")
            }
            Db.tenantTx(id) { c ->
                val exists = c.queryOne("SELECT 1 FROM menu_categories WHERE tenant_id = ?::uuid AND id = ?", id, body.categoryId) { true } != null
                if (!exists) badRequest("Elige una categoría para el platillo.")
                c.upsertItem(id, body)
            }
            call.respond(body)
        }

        delete("/api/tenant/menu/items/{id}") {
            val id = call.tenantId
            val itemId = call.parameters["id"].orEmpty()
            Db.tenantTx(id) { c -> c.exec("DELETE FROM menu_items WHERE tenant_id = ?::uuid AND id = ?", id, itemId) }
            call.respond(HttpStatusCode.NoContent)
        }

        put("/api/tenant/menu/items/{id}/availability") {
            val id = call.tenantId
            val itemId = call.parameters["id"].orEmpty()
            val req = call.receive<SetAvailabilityRequest>()
            val item = Db.tenantTx(id) { c ->
                c.exec("UPDATE menu_items SET available = ? WHERE tenant_id = ?::uuid AND id = ?", req.available, id, itemId)
                c.menuItem(id, itemId)
            } ?: notFound("No encontramos ese platillo.")
            call.respond(item)
        }

        post("/api/tenant/orders/{id}/refund") {
            val id = call.tenantId
            val orderId = call.parameters["id"].orEmpty()
            val order = Db.tenantTx(id) { c -> c.orderById(orderId) } ?: notFound("No encontramos ese pedido.")
            if (order.status == OrderStatus.REFUNDED) {
                call.respond(order)
                return@post
            }
            if (order.status == OrderStatus.PENDING_PAYMENT || order.payment?.status != PaymentStatus.APPROVED) {
                badRequest("Solo se pueden reembolsar pedidos pagados.")
            }
            val ok = Payments.refund(id, order)
            if (!ok) throw ApiException(HttpStatusCode.BadGateway, "Mercado Pago no pudo procesar el reembolso. Intenta de nuevo.", "refund_failed")
            val refunded = OrderService.markRefunded(orderId) ?: notFound("No encontramos ese pedido.")
            call.respond(refunded)
        }

        get(ApiRoutes.TENANT_STATS) {
            val id = call.tenantId
            call.respond(Db.tenantTx(id) { c -> tenantStats(c, id) })
        }

        get(ApiRoutes.TENANT_STAFF) {
            val id = call.tenantId
            val staff = Db.tenantTx(id) { c ->
                c.query("SELECT id, tenant_id::text AS tenant_id, email, name, role, password_hash FROM users WHERE tenant_id = ?::uuid ORDER BY created_at, name", id) {
                    val u = it.toUserRow()
                    StaffMember(u.id, u.name, u.email, u.role)
                }
            }
            call.respond(staff.sortedBy { it.role.ordinal })
        }
    }

    // ── Operación de pedidos: también caja y cocina.
    authenticated(Role.OWNER, Role.MANAGER, Role.CASHIER, Role.KITCHEN) {
        // Leer la marca es de solo lectura: caja y cocina la necesitan para pintar su pantalla.
        get(ApiRoutes.TENANT_BRAND) {
            val id = call.tenantId
            val tenant = Db.tenantTx(id) { c -> c.tenantById(id) } ?: notFound("No encontramos tu restaurante.")
            call.respond(tenant.public)
        }

        get(ApiRoutes.TENANT_ORDERS) {
            val id = call.tenantId
            val active = call.request.queryParameters["active"]?.lowercase() == "true"
            val orders = Db.tenantTx(id) { c ->
                if (active) {
                    c.query(
                        "SELECT $ORDER_COLUMNS FROM orders WHERE tenant_id = ?::uuid AND status IN ('PAID', 'PREPARING', 'READY') ORDER BY COALESCE(paid_at, created_at) ASC LIMIT 300",
                        id,
                    ) { it.toOrder() }
                } else {
                    c.query("SELECT $ORDER_COLUMNS FROM orders WHERE tenant_id = ?::uuid ORDER BY created_at DESC LIMIT 100", id) { it.toOrder() }
                }
            }
            call.respond(orders)
        }

        put("/api/tenant/orders/{id}/status") {
            val id = call.tenantId
            val orderId = call.parameters["id"].orEmpty()
            val target = call.receive<UpdateOrderStatusRequest>().status
            if (target == OrderStatus.PENDING_PAYMENT) badRequest("Ese cambio de estado no es válido.")
            if (target == OrderStatus.REFUNDED) badRequest("Para reembolsar usa la opción de reembolso.")
            val updated = Db.tenantTx(id) { c ->
                val current = c.orderById(orderId, forUpdate = true) ?: notFound("No encontramos ese pedido.")
                if (current.status == target) return@tenantTx current
                if (current.status == OrderStatus.REFUNDED) badRequest("Este pedido ya fue reembolsado.")
                if (current.status == OrderStatus.PENDING_PAYMENT && target != OrderStatus.CANCELLED) badRequest("Este pedido aún no ha sido pagado.")
                val now = System.currentTimeMillis()
                val next = when (target) {
                    OrderStatus.PAID -> current.copy(status = target, preparingAt = null, readyAt = null, completedAt = null)
                    OrderStatus.PREPARING -> current.copy(status = target, preparingAt = current.preparingAt ?: now, readyAt = null, completedAt = null)
                    OrderStatus.READY -> current.copy(status = target, preparingAt = current.preparingAt ?: now, readyAt = now, completedAt = null)
                    OrderStatus.COMPLETED -> current.copy(status = target, readyAt = current.readyAt ?: now, completedAt = now)
                    else -> current.copy(status = target)
                }
                c.saveOrderState(next)
                next
            }
            OrderService.publishUpdated(updated)
            call.respond(updated)
        }
    }
}

/** Estadísticas del restaurante calculadas de sus pedidos (día operativo en hora de México). */
private fun tenantStats(c: java.sql.Connection, tenantId: String): TenantStats {
    val now = ZonedDateTime.now(AppZone)
    val startOfToday = now.truncatedTo(ChronoUnit.DAYS)
    val todayMs = startOfToday.toInstant().toEpochMilli()
    val weekStartMs = startOfToday.minusDays(6).toInstant().toEpochMilli()
    val monthStartMs = startOfToday.minusDays(29).toInstant().toEpochMilli()

    val recent = c.query(
        "SELECT $ORDER_COLUMNS FROM orders WHERE tenant_id = ?::uuid AND status IN $SOLD AND created_at >= ? ORDER BY created_at",
        tenantId, monthStartMs,
    ) { it.toOrder() }

    val today = recent.filter { it.createdAt >= todayMs }
    val salesToday = today.sumOf { it.totalCents }
    val ticketBase = today.ifEmpty { recent }
    val avgTicket = if (ticketBase.isEmpty()) 0L else ticketBase.sumOf { it.totalCents } / ticketBase.size

    val prepTimes = recent.mapNotNull { o ->
        val start = o.paidAt ?: o.createdAt
        o.readyAt?.let { ready -> (ready - start).coerceAtLeast(0) }
    }
    val avgPrep = if (prepTimes.isEmpty()) 0 else (prepTimes.average() / 60_000.0).toInt().coerceAtLeast(1)

    val series = (0..6).map { i ->
        val day = startOfToday.minusDays((6 - i).toLong())
        val from = day.toInstant().toEpochMilli()
        val to = day.plusDays(1).toInstant().toEpochMilli()
        val cents = recent.filter { it.createdAt in from until to }.sumOf { it.totalCents }
        MetricPoint(DAY_LABELS[day.dayOfWeek.value - 1], cents / 100.0)
    }

    val top = recent.filter { it.createdAt >= weekStartMs }.ifEmpty { recent }
        .flatMap { it.lines }
        .groupBy { it.name }
        .map { (name, lines) -> TopItem(name, lines.sumOf { it.quantity }, lines.sumOf { it.lineTotalCents }) }
        .sortedByDescending { it.quantity }
        .take(5)

    return TenantStats(
        ordersToday = today.size,
        salesTodayCents = salesToday,
        avgTicketCents = avgTicket,
        avgPrepMinutes = avgPrep,
        salesSeries = series,
        topItems = top,
    )
}

@Suppress("unused")
private fun epochDay(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(AppZone).toLocalDate().toEpochDay()
