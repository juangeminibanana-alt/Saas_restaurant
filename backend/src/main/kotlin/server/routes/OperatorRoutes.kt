package server.routes

import app.shared.api.*
import app.shared.model.*
import app.shared.util.newId
import app.shared.util.slugify
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import server.auth.Passwords
import server.auth.authenticated
import server.badRequest
import server.conflict
import server.data.*
import server.db.Db
import server.db.exec
import server.db.query
import server.db.queryOne
import server.notFound
import java.sql.Connection
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID

private const val DAY_MS = 24L * 60 * 60 * 1000
private val MONTH_LABELS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

/** Vista del operador de uno o todos los tenants (rol admin: cruza restaurantes). */
fun Connection.tenantSummaries(onlyId: String? = null): List<TenantSummary> {
    val since = System.currentTimeMillis() - 30 * DAY_MS
    val hasMp = queryOne("SELECT to_regclass('public.mp_connections') IS NOT NULL") { it.getBoolean(1) } == true
    val mpExpr = if (hasMp) "EXISTS (SELECT 1 FROM mp_connections m WHERE m.tenant_id = t.id)" else "false"
    val sql =
        "SELECT t.id::text AS id, t.slug, t.name, t.status, t.plan_id, t.created_at, t.theme->>'primary' AS primary_color, " +
            "COALESCE(p.name, t.plan_id) AS plan_name, COALESCE(p.price_cents, 0) AS price_cents, " +
            "COALESCE(u.name, '') AS owner_name, COALESCE(u.email, '') AS owner_email, $mpExpr AS mp_connected, " +
            "COALESCE(o.orders, 0) AS orders_30d, COALESCE(o.gmv, 0) AS gmv_30d " +
            "FROM tenants t " +
            "LEFT JOIN plans p ON p.id = t.plan_id " +
            "LEFT JOIN LATERAL (SELECT name, email FROM users WHERE tenant_id = t.id AND role = 'OWNER' ORDER BY created_at LIMIT 1) u ON true " +
            "LEFT JOIN LATERAL (SELECT count(*) AS orders, COALESCE(sum(total_cents), 0) AS gmv FROM orders " +
            "  WHERE tenant_id = t.id AND status IN ('PAID', 'PREPARING', 'READY', 'COMPLETED') AND created_at >= ?) o ON true " +
            (if (onlyId != null) "WHERE t.id = ?::uuid " else "") +
            "ORDER BY t.created_at DESC, t.name"
    val params: Array<Any?> = if (onlyId != null) arrayOf(since, onlyId) else arrayOf(since)
    return query(sql, *params) { rs ->
        val status = TenantStatus.entries.firstOrNull { it.name == rs.getString("status") } ?: TenantStatus.TRIAL
        TenantSummary(
            id = rs.getString("id"),
            slug = rs.getString("slug"),
            name = rs.getString("name"),
            status = status,
            planId = rs.getString("plan_id"),
            planName = rs.getString("plan_name"),
            ownerName = rs.getString("owner_name"),
            ownerEmail = rs.getString("owner_email"),
            createdAt = rs.getLong("created_at"),
            mpConnected = rs.getBoolean("mp_connected"),
            orders30d = rs.getInt("orders_30d"),
            gmv30dCents = rs.getLong("gmv_30d"),
            mrrCents = if (status == TenantStatus.ACTIVE) rs.getLong("price_cents") else 0L,
            primary = rs.getString("primary_color"),
        )
    }
}

private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

private fun operatorMetrics(c: Connection): OperatorMetrics {
    data class T(val status: TenantStatus, val priceCents: Long, val createdAt: Long)

    val tenants = c.query("SELECT t.status, t.created_at, COALESCE(p.price_cents, 0) AS price_cents FROM tenants t LEFT JOIN plans p ON p.id = t.plan_id") { rs ->
        T(TenantStatus.entries.firstOrNull { it.name == rs.getString("status") } ?: TenantStatus.TRIAL, rs.getLong("price_cents"), rs.getLong("created_at"))
    }
    val active = tenants.filter { it.status == TenantStatus.ACTIVE }
    val trial = tenants.count { it.status == TenantStatus.TRIAL }
    val suspended = tenants.count { it.status == TenantStatus.SUSPENDED }
    val pastDue = tenants.count { it.status == TenantStatus.PAST_DUE }
    val mrr = active.sumOf { it.priceCents }

    // Serie de los últimos 6 meses: MRR de los tenants hoy activos que ya existían al cierre de cada mes.
    val now = ZonedDateTime.now(AppZone)
    val currentMonth = YearMonth.from(now)
    val months = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    fun endOf(m: YearMonth): Long = m.plusMonths(1).atDay(1).atStartOfDay(AppZone).toInstant().toEpochMilli()
    fun startOf(m: YearMonth): Long = m.atDay(1).atStartOfDay(AppZone).toInstant().toEpochMilli()
    val mrrSeries = months.map { m ->
        MetricPoint(MONTH_LABELS[m.monthValue - 1], active.filter { it.createdAt < endOf(m) }.sumOf { it.priceCents } / 100.0)
    }
    val signupsSeries = months.map { m ->
        MetricPoint(MONTH_LABELS[m.monthValue - 1], tenants.count { it.createdAt >= startOf(m) && it.createdAt < endOf(m) }.toDouble())
    }
    val prevMrr = mrrSeries.getOrNull(mrrSeries.size - 2)?.value ?: 0.0
    val lastMrr = mrrSeries.lastOrNull()?.value ?: 0.0
    val mrrDelta = if (prevMrr > 0) (lastMrr - prevMrr) / prevMrr * 100.0 else if (lastMrr > 0) 100.0 else 0.0

    // Churn: suspendidos sobre los que alguna vez pagaron. Activación: los que pasaron de prueba a pagar.
    val everPaying = active.size + suspended + pastDue
    val churn = if (everPaying > 0) suspended * 100.0 / everPaying else 0.0
    val activation = if (tenants.isNotEmpty()) (active.size + pastDue) * 100.0 / tenants.size else 0.0

    val since = System.currentTimeMillis() - 30 * DAY_MS
    val totals = c.queryOne(
        "SELECT count(*) AS orders, COALESCE(sum(o.total_cents), 0) AS gmv, " +
            "COALESCE(sum(COALESCE(NULLIF((o.payment->>'marketplaceFeeCents')::bigint, 0), o.subtotal_cents * COALESCE(p.fee_bps, 150) / 10000)), 0) AS fees " +
            "FROM orders o JOIN tenants t ON t.id = o.tenant_id LEFT JOIN plans p ON p.id = t.plan_id " +
            "WHERE o.status IN ('PAID', 'PREPARING', 'READY', 'COMPLETED') AND o.created_at >= ?",
        since,
    ) { rs -> Triple(rs.getInt("orders"), rs.getLong("gmv"), rs.getLong("fees")) } ?: Triple(0, 0L, 0L)

    return OperatorMetrics(
        mrrCents = mrr,
        mrrDeltaPct = round1(mrrDelta),
        activeTenants = active.size,
        trialTenants = trial,
        suspendedTenants = suspended,
        churnPct = round1(churn),
        activationPct = round1(activation),
        gmv30dCents = totals.second,
        platformFees30dCents = totals.third,
        orders30d = totals.first,
        mrrSeries = mrrSeries,
        signupsSeries = signupsSeries,
    )
}

fun Route.operatorRoutes() {
    authenticated(Role.OPERATOR) {
        get(ApiRoutes.OPERATOR_METRICS) {
            call.respond(Db.adminTx { c -> operatorMetrics(c) })
        }

        get(ApiRoutes.OPERATOR_TENANTS) {
            call.respond(Db.adminTx { c -> c.tenantSummaries() })
        }

        post(ApiRoutes.OPERATOR_TENANTS) {
            val req = call.receive<CreateTenantRequest>()
            val email = req.ownerEmail.trim().lowercase()
            val slug = req.slug.trim().lowercase().ifBlank { slugify(req.name) }
            if (req.name.isBlank()) badRequest("Escribe el nombre del restaurante.")
            if (!SLUG.matches(slug) || slug.length > 48) badRequest("El identificador solo puede llevar minúsculas, números y guiones.")
            if (req.ownerName.isBlank()) badRequest("Escribe el nombre del dueño.")
            if (!email.contains('@')) badRequest("Escribe un correo válido para el dueño.")
            if (req.ownerPassword.length < 8) badRequest("La contraseña debe tener al menos 8 caracteres.")
            val hash = Passwords.hash(req.ownerPassword)
            val now = System.currentTimeMillis()
            val summary = Db.adminTx { c ->
                val plan = c.planById(req.planId) ?: badRequest("Elige un plan válido.")
                if (c.tenantBySlug(slug) != null) conflict("Ya existe un restaurante con ese identificador.")
                if (c.userByEmail(email) != null) conflict("Ya existe una cuenta con ese correo.")
                val tenant = TenantPublic(id = UUID.randomUUID().toString(), slug = slug, name = req.name.trim().take(80), cuisine = req.cuisine.trim().take(80))
                c.insertTenant(tenant, TenantStatus.TRIAL, plan.id, now)
                c.insertUser(newId("usr"), tenant.id, email, req.ownerName.trim().take(80), Role.OWNER, hash, now)
                c.exec("INSERT INTO menu_categories (tenant_id, id, name, description, sort_order) VALUES (?::uuid, ?, 'Platillos', '', 0)", tenant.id, newId("cat"))
                c.tenantSummaries(tenant.id).first()
            }
            call.respond(summary)
        }

        put("/api/operator/tenants/{id}/status") {
            val id = call.parameters["id"].orEmpty()
            val req = call.receive<SetTenantStatusRequest>()
            val summary = Db.adminTx { c ->
                val n = c.exec("UPDATE tenants SET status = ? WHERE id::text = ?", req.status.name, id)
                if (n == 0) notFound("No encontramos ese restaurante.")
                c.tenantSummaries(id).first()
            }
            call.respond(summary)
        }

        put("/api/operator/tenants/{id}/plan") {
            val id = call.parameters["id"].orEmpty()
            val req = call.receive<AssignPlanRequest>()
            val summary = Db.adminTx { c ->
                c.planById(req.planId) ?: badRequest("Elige un plan válido.")
                val n = c.exec("UPDATE tenants SET plan_id = ? WHERE id::text = ?", req.planId, id)
                if (n == 0) notFound("No encontramos ese restaurante.")
                c.tenantSummaries(id).first()
            }
            call.respond(summary)
        }

        get(ApiRoutes.OPERATOR_PLANS) {
            call.respond(Db.adminTx { c -> c.listPlans() })
        }

        put("/api/operator/plans/{id}") {
            val id = call.parameters["id"].orEmpty()
            val body = call.receive<Plan>().copy(id = id)
            if (id.isBlank() || body.name.isBlank()) badRequest("Escribe el nombre del plan.")
            if (body.priceCents < 0) badRequest("El precio no puede ser negativo.")
            if (body.feeBps !in 0..5000) badRequest("La comisión debe estar entre 0% y 50%.")
            val saved = Db.adminTx { c ->
                c.upsertPlan(body)
                c.planById(id)
            } ?: body
            call.respond(saved)
        }
    }
}
