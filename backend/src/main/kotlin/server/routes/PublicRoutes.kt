package server.routes

import app.shared.api.*
import app.shared.model.*
import app.shared.util.newId
import app.shared.util.slugify
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory
import server.ApiException
import server.auth.Passwords
import server.auth.Tokens
import server.auth.authenticated
import server.auth.user
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
import java.util.UUID

private val log = LoggerFactory.getLogger("PublicRoutes")

private const val DELIVERY_FEE_CENTS = 3_500L
private const val UNAVAILABLE = "Este restaurante no está disponible"

/** Tenant visible al público o 404/403. */
private suspend fun publicTenant(slug: String?): TenantRow {
    val row = Db.adminTx { c -> c.tenantBySlug(slug.orEmpty().trim().lowercase()) } ?: notFound("No encontramos este restaurante.")
    if (row.status == TenantStatus.SUSPENDED) throw ApiException(HttpStatusCode.Forbidden, UNAVAILABLE, "tenant_unavailable")
    return row
}

/** Recalcula una línea del carrito contra el menú real: nunca se confía en precios del cliente. */
private fun priceLine(req: CartLineRequest, items: Map<String, MenuItem>): OrderLine {
    val item = items[req.itemId] ?: badRequest("Un platillo de tu pedido ya no está en el menú.")
    if (!item.available) badRequest("\"${item.name}\" no está disponible por ahora.")
    if (req.quantity !in 1..99) badRequest("La cantidad de \"${item.name}\" no es válida.")
    val chosen = req.optionIds.toSet()
    val known = item.modifierGroups.flatMap { g -> g.options.map { it.id } }.toSet()
    if (!known.containsAll(chosen)) badRequest("Una opción de \"${item.name}\" ya no está disponible.")
    val modifiers = ArrayList<OrderLineModifier>()
    for (group in item.modifierGroups) {
        val selected = group.options.filter { it.id in chosen }
        if (selected.size < group.minSelect) badRequest("Elige ${group.name.lowercase()} para \"${item.name}\".")
        if (group.maxSelect > 0 && selected.size > group.maxSelect) badRequest("Elige máximo ${group.maxSelect} en ${group.name.lowercase()} para \"${item.name}\".")
        selected.forEach { modifiers.add(OrderLineModifier(group.name, it.name, it.priceDeltaCents)) }
    }
    return OrderLine(
        itemId = item.id,
        name = item.name,
        quantity = req.quantity,
        unitPriceCents = item.priceCents,
        modifiers = modifiers,
        notes = req.notes.trim().take(280),
        imageUrl = item.imageUrl,
    )
}

fun sessionFor(user: UserRow, tenant: TenantPublic?): UserSession {
    val base = UserSession(
        userId = user.id, name = user.name, email = user.email, role = user.role,
        tenantId = tenant?.id, tenantSlug = tenant?.slug, tenantName = tenant?.name,
    )
    return base.copy(token = Tokens.issue(base))
}

fun Route.publicRoutes() {
    get(ApiRoutes.PUBLIC_TENANTS) {
        val tenants = Db.adminTx { c ->
            c.query("SELECT $TENANT_COLUMNS FROM tenants WHERE status IN ('ACTIVE', 'TRIAL') ORDER BY created_at, name") { it.toTenantRow().public }
        }
        call.respond(tenants)
    }

    get("/api/public/tenants/{slug}") {
        call.respond(publicTenant(call.parameters["slug"]).public)
    }

    get("/api/public/tenants/{slug}/menu") {
        val tenant = publicTenant(call.parameters["slug"])
        val menu = Db.tenantTx(tenant.public.id) { c -> c.loadMenu(tenant.public.id, onlyAvailable = true) }
        call.respond(menu)
    }

    post("/api/public/tenants/{slug}/orders") {
        val tenant = publicTenant(call.parameters["slug"])
        val t = tenant.public
        val req = call.receive<CreateOrderRequest>()
        if (req.lines.isEmpty()) badRequest("Tu pedido está vacío.")
        if (req.lines.size > 60) badRequest("Tu pedido tiene demasiados platillos.")
        if (req.type !in t.orderTypes) badRequest("Este restaurante no ofrece ese tipo de pedido.")
        if (req.type == OrderType.DELIVERY && req.address.isBlank()) badRequest("Escribe la dirección de entrega.")
        if (req.tipCents < 0 || req.tipCents > 1_000_000) badRequest("La propina no es válida.")

        val feeBps = Db.adminTx { c -> c.planById(tenant.planId)?.feeBps } ?: 150
        val order = Db.tenantTx(t.id) { c ->
            val items = c.loadMenu(t.id, onlyAvailable = false).items.associateBy { it.id }
            val lines = req.lines.map { priceLine(it, items) }
            val subtotal = lines.sumOf { it.lineTotalCents }
            val deliveryFee = if (req.type == OrderType.DELIVERY) DELIVERY_FEE_CENTS else 0L
            val prep = lines.maxOfOrNull { l -> items[l.itemId]?.prepMinutes ?: 15 } ?: 15
            val created = Order(
                id = newId("ord"),
                tenantId = t.id,
                tenantSlug = t.slug,
                number = c.nextOrderNumber(t.id),
                status = OrderStatus.PENDING_PAYMENT,
                type = req.type,
                channel = OrderChannel.DIRECT,
                lines = lines,
                subtotalCents = subtotal,
                tipCents = req.tipCents,
                deliveryFeeCents = deliveryFee,
                totalCents = subtotal + req.tipCents + deliveryFee,
                customerName = req.customerName.trim().take(80),
                customerPhone = req.customerPhone.trim().take(30),
                tableLabel = req.tableLabel.trim().take(40),
                address = req.address.trim().take(240),
                notes = req.notes.trim().take(280),
                createdAt = System.currentTimeMillis(),
                etaMinutes = if (req.type == OrderType.DELIVERY) maxOf(t.etaMinutes, prep + 15) else maxOf(prep, minOf(t.etaMinutes, prep + 10)),
                payment = PaymentInfo(method = req.paymentMethod, status = PaymentStatus.PENDING),
            )
            c.insertOrder(created)
            created
        }

        // Checkout de Mercado Pago (split con marketplace_fee) o modo demostración.
        val payment = try {
            Payments.createCheckout(t.id, t.slug, order, feeBps)
        } catch (e: Exception) {
            log.warn("No se pudo crear el checkout del pedido {}: {}", order.id, e.message)
            order.payment ?: PaymentInfo(method = req.paymentMethod)
        }
        val saved = order.copy(payment = payment)
        Db.tenantTx(t.id) { c -> c.saveOrderState(saved) }
        call.respond(CreateOrderResponse(order = saved, checkoutUrl = payment.checkoutUrl, sandbox = Payments.demoMode))
    }

    get("/api/public/orders/{orderId}") {
        val order = OrderService.getOrder(call.parameters["orderId"].orEmpty()) ?: notFound("No encontramos este pedido.")
        call.respond(order)
    }

    post("/api/public/orders/{orderId}/simulate-payment") {
        val id = call.parameters["orderId"].orEmpty()
        val order = OrderService.getOrder(id) ?: notFound("No encontramos este pedido.")
        if (!Payments.demoMode && order.payment?.checkoutUrl != null) {
            throw ApiException(HttpStatusCode.Forbidden, "Este pedido se paga en Mercado Pago.", "not_sandbox")
        }
        if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REFUNDED) badRequest("Este pedido ya no se puede pagar.")
        val paid = OrderService.markPaid(id, "sim-" + newId("pay")) ?: notFound("No encontramos este pedido.")
        call.respond(paid)
    }

    get(ApiRoutes.PUBLIC_PLANS) {
        call.respond(Db.adminTx { c -> c.listPlans() })
    }

    post(ApiRoutes.PUBLIC_SIGNUP) {
        val req = call.receive<SignupRequest>()
        val email = req.email.trim().lowercase()
        if (req.restaurantName.isBlank()) badRequest("Escribe el nombre de tu restaurante.")
        if (req.ownerName.isBlank()) badRequest("Escribe tu nombre.")
        if (!email.contains('@') || email.length < 5) badRequest("Escribe un correo válido.")
        if (req.password.length < 8) badRequest("La contraseña debe tener al menos 8 caracteres.")
        val hash = Passwords.hash(req.password)
        val now = System.currentTimeMillis()
        val (summary, session, plan) = Db.adminTx { c ->
            val plan = c.planById(req.planId) ?: badRequest("Elige un plan válido.")
            if (c.userByEmail(email) != null) conflict("Ya existe una cuenta con este correo.")
            val slug = c.freeSlug(req.restaurantName)
            val tenant = TenantPublic(id = UUID.randomUUID().toString(), slug = slug, name = req.restaurantName.trim().take(80))
            c.insertTenant(tenant, TenantStatus.TRIAL, plan.id, now)
            val user = UserRow(newId("usr"), tenant.id, email, req.ownerName.trim().take(80), Role.OWNER, hash)
            c.insertUser(user.id, user.tenantId, user.email, user.name, user.role, user.passwordHash, now)
            c.exec("INSERT INTO menu_categories (tenant_id, id, name, description, sort_order) VALUES (?::uuid, ?, 'Platillos', '', 0)", tenant.id, newId("cat"))
            Triple(c.tenantSummaries(tenant.id).first(), sessionFor(user, tenant), plan)
        }
        val checkoutUrl = try {
            Payments.createSubscriptionCheckout(summary.id, plan, email)
        } catch (e: Exception) {
            log.warn("No se pudo crear la suscripción de {}: {}", summary.slug, e.message)
            null
        }
        call.respond(SignupResponse(tenant = summary, session = session, subscriptionCheckoutUrl = checkoutUrl))
    }
}

fun Route.authRoutes() {
    post(ApiRoutes.LOGIN) {
        val req = call.receive<LoginRequest>()
        val invalid = ApiException(HttpStatusCode.Unauthorized, "Correo o contraseña incorrectos.", "invalid_credentials")
        val (user, tenant) = Db.adminTx { c ->
            val u = c.userByEmail(req.email) ?: return@adminTx null
            u to u.tenantId?.let { c.tenantById(it) }
        } ?: throw invalid
        if (!Passwords.verify(req.password, user.passwordHash)) throw invalid
        if (user.role != Role.OPERATOR && tenant == null) throw invalid
        call.respond(sessionFor(user, tenant?.public))
    }

    authenticated {
        get(ApiRoutes.ME) {
            val token = call.request.headers["Authorization"].orEmpty().removePrefix("Bearer ").trim()
            call.respond(call.user.copy(token = token))
        }
    }
}

/** Slug libre a partir del nombre ("mi-fonda", "mi-fonda-2", …). */
fun java.sql.Connection.freeSlug(name: String): String {
    val base = slugify(name).take(40).trim('-').ifBlank { "restaurante" }
    var candidate = base
    var n = 2
    while (queryOne("SELECT 1 FROM tenants WHERE slug = ?", candidate) { true } != null) {
        candidate = "$base-$n"
        n++
    }
    return candidate
}
