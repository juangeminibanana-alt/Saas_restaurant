package server.seed

import app.shared.demo.DemoData
import app.shared.model.*
import org.slf4j.LoggerFactory
import server.auth.Passwords
import server.data.insertOrder
import server.data.insertTenant
import server.data.insertUser
import server.data.upsertCategory
import server.data.upsertItem
import server.data.upsertPlan
import server.data.AppZone
import server.data.formatOrderNumber
import server.db.Db
import server.db.exec
import server.db.queryOne
import java.sql.Connection
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/** Datos de demostración: restaurantes ficticios, usuarios, menús y pedidos para que todo se vea vivo. */
object Seed {
    private val log = LoggerFactory.getLogger("Seed")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** uuid estable por slug (los ids "t-brasa" de DemoData no son uuid). */
    fun tenantUuid(slug: String): String = UUID.nameUUIDFromBytes("tenant:$slug".toByteArray()).toString()

    private data class Extra(
        val slug: String, val name: String, val cuisine: String, val tagline: String, val primary: String?,
        val status: TenantStatus, val planId: String, val daysAgo: Int, val owner: String,
    )

    private val extras = listOf(
        Extra("comal-demo", "Comal Demo", "Antojitos · Mexicana", "Antojitos de comal y aguas frescas", "#C2571A", TenantStatus.ACTIVE, "plan-esencial", 168, "Rosa Méndez"),
        Extra("marea-demo", "Marea Demo", "Mariscos", "Mariscos frescos estilo Pacífico", "#1F6F8B", TenantStatus.ACTIVE, "plan-cadena", 131, "Iván Castro"),
        Extra("verde-demo", "Verde Demo", "Ensaladas · Bowls", "Bowls y ensaladas hechas al momento", "#3C7A3E", TenantStatus.ACTIVE, "plan-pro", 74, "Elena Solís"),
        Extra("lumbre-demo", "Lumbre Demo", "Pizza a la leña", "Pizza napolitana a la leña", "#A8322D", TenantStatus.PAST_DUE, "plan-pro", 102, "Marco Peña"),
        Extra("dulce-demo", "Dulce Demo", "Postres · Helados", "Helados artesanales y postres", "#B0477A", TenantStatus.SUSPENDED, "plan-esencial", 146, "Carla Ibarra"),
        Extra("fogon-demo", "Fogón Demo", "Cocina de autor", "Cocina de temporada", null, TenantStatus.TRIAL, "plan-pro", 9, "Andrés Lara"),
        Extra("cafe-norte-demo", "Café Norte Demo", "Café · Brunch", "Café de especialidad y brunch", "#6B4A2F", TenantStatus.TRIAL, "plan-esencial", 3, "Paula Rey"),
    )

    fun seedIfEmpty() {
        val empty = Db.adminTxBlocking { c -> c.queryOne("SELECT count(*) FROM tenants") { it.getLong(1) } == 0L }
        if (!empty) return
        log.info("Base vacía: sembrando datos de demostración…")
        val now = System.currentTimeMillis()
        val hash = Passwords.hash(DemoData.DEMO_PASSWORD)
        Db.adminTxBlocking { c ->
            DemoData.plans.forEachIndexed { i, p -> c.upsertPlan(p, i) }

            // Operador de la plataforma (sin tenant).
            DemoData.users.filter { it.tenantSlug == null }.forEachIndexed { i, u ->
                c.insertUser("usr-operator-$i", null, u.email, u.name, u.role, hash, now - 200 * DAY_MS)
            }

            // Restaurantes de demostración con menú, personal y pedidos.
            val demoAges = mapOf("brasa-demo" to 158, "nori-demo" to 117, "miga-demo" to 61)
            for (t in DemoData.tenants) {
                val id = tenantUuid(t.slug)
                val createdAt = now - (demoAges[t.slug] ?: 90) * DAY_MS
                c.insertTenant(t.copy(id = id), TenantStatus.ACTIVE, DemoData.tenantPlan[t.slug] ?: DemoData.plans.first().id, createdAt)
                DemoData.users.filter { it.tenantSlug == t.slug }.forEachIndexed { i, u ->
                    c.insertUser("usr-${t.slug}-$i", id, u.email, u.name, u.role, hash, createdAt + i)
                }
                val menu = DemoData.menuFor(t.slug)
                menu.categories.forEach { c.upsertCategory(id, it) }
                menu.items.forEach { c.upsertItem(id, it) }

                val live = DemoData.sampleOrders(t.slug, now).map { it.copy(tenantId = id, tenantSlug = t.slug) }
                live.forEach { c.insertOrder(it) }
                val history = history(t.copy(id = id), menu, now, if (t.slug == "brasa-demo") 16 else 7)
                history.forEach { c.insertOrder(it) }
                // El consecutivo continúa después de los pedidos de ejemplo ("A-041" … "A-045").
                c.exec("INSERT INTO order_counters (tenant_id, last_number) VALUES (?::uuid, ?)", id, 45)
                log.info("  {}: {} platillos, {} pedidos", t.slug, menu.items.size, live.size + history.size)
            }

            // Restaurantes ficticios extra para que el panel del operador tenga variedad de estados y planes.
            for (e in extras) {
                val id = tenantUuid(e.slug)
                val createdAt = now - e.daysAgo * DAY_MS
                val tenant = TenantPublic(
                    id = id, slug = e.slug, name = e.name, tagline = e.tagline, cuisine = e.cuisine,
                    description = "${e.tagline}. Restaurante ficticio de demostración.",
                    theme = BrandTheme(primary = e.primary),
                    address = "Calle Ficticia ${10 + e.daysAgo % 80}, CDMX", phone = "55 0000 01" + (10 + e.daysAgo % 89),
                    hours = (1..7).map { OpeningHours(it, "12:00", "22:00") },
                )
                c.insertTenant(tenant, e.status, e.planId, createdAt)
                c.insertUser("usr-${e.slug}-0", id, "dueno@" + e.slug.removeSuffix("-demo") + ".demo", e.owner, Role.OWNER, hash, createdAt)
                c.upsertCategory(id, MenuCategory("cat-platillos", "Platillos", "", 0))
            }
        }
        log.info("Datos de demostración listos. Usuarios: {} (contraseña: {})", DemoData.users.joinToString { it.email }, DemoData.DEMO_PASSWORD)
    }

    /** ~30 días de pedidos completados con curva de comida/cena y fines de semana más fuertes. */
    private fun history(t: TenantPublic, menu: Menu, now: Long, basePerDay: Int): List<Order> {
        val items = menu.items.filter { it.available }
        if (items.isEmpty()) return emptyList()
        val rnd = Random(t.slug.hashCode())
        val names = listOf("Ana", "Luis", "María", "Jorge", "Sofía", "Pedro", "Valeria", "Diego", "Camila", "Raúl", "Fer", "Pau", "Emilio", "Regina", "Toño", "Dani")
        val methods = listOf(PaymentMethod.CARD, PaymentMethod.CARD, PaymentMethod.CARD, PaymentMethod.MP_WALLET, PaymentMethod.SPEI, PaymentMethod.OXXO)
        val channels = listOf(OrderChannel.DIRECT, OrderChannel.DIRECT, OrderChannel.DIRECT, OrderChannel.DIRECT, OrderChannel.UBER_EATS, OrderChannel.RAPPI, OrderChannel.DIDI_FOOD)
        val types = listOf(OrderType.DINE_IN, OrderType.DINE_IN, OrderType.PICKUP, OrderType.DELIVERY)
        val today = ZonedDateTime.now(AppZone).truncatedTo(ChronoUnit.DAYS)
        val feeBps = DemoData.plans.firstOrNull { it.id == DemoData.tenantPlan[t.slug] }?.feeBps ?: 150
        val out = ArrayList<Order>()
        var seq = 0
        for (daysAgo in 30 downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            val weekend = day.dayOfWeek.value >= 5
            val growth = 1.0 + (30 - daysAgo) / 60.0
            val count = ((basePerDay + rnd.nextInt(-3, 5)) * (if (weekend) 1.45 else 1.0) * growth).toInt().coerceAtLeast(2)
            repeat(count) {
                val hour = if (rnd.nextInt(100) < 55) 13 + rnd.nextInt(4) else 19 + rnd.nextInt(4)
                val createdAt = day.plusHours(hour.toLong()).plusMinutes(rnd.nextLong(60)).toInstant().toEpochMilli()
                if (createdAt > now - 45 * 60_000L) return@repeat
                val lines = (1..(1 + rnd.nextInt(3))).map {
                    val item = items[rnd.nextInt(items.size)]
                    val mods = item.modifierGroups.filter { g -> g.minSelect > 0 && g.options.isNotEmpty() }.map { g ->
                        val o = g.options[rnd.nextInt(g.options.size)]
                        OrderLineModifier(g.name, o.name, o.priceDeltaCents)
                    }
                    OrderLine(item.id, item.name, 1 + rnd.nextInt(2), item.priceCents, mods, "", item.imageUrl)
                }
                val type = types[rnd.nextInt(types.size)]
                val channel = if (type == OrderType.DELIVERY) channels[rnd.nextInt(channels.size)] else OrderChannel.DIRECT
                val subtotal = lines.sumOf { it.lineTotalCents }
                val tip = if (channel == OrderChannel.DIRECT && rnd.nextInt(100) < 40) subtotal / 10 else 0L
                val delivery = if (type == OrderType.DELIVERY && channel == OrderChannel.DIRECT) 3_500L else 0L
                val prepMs = (9 + rnd.nextInt(16)) * 60_000L
                val refunded = rnd.nextInt(100) < 2
                seq++
                out.add(
                    Order(
                        id = "hist-${t.slug}-$seq",
                        tenantId = t.id,
                        tenantSlug = t.slug,
                        number = formatOrderNumber(1000 + seq),
                        status = if (refunded) OrderStatus.REFUNDED else OrderStatus.COMPLETED,
                        type = type,
                        channel = channel,
                        lines = lines,
                        subtotalCents = subtotal,
                        tipCents = tip,
                        deliveryFeeCents = delivery,
                        totalCents = subtotal + tip + delivery,
                        customerName = names[rnd.nextInt(names.size)],
                        tableLabel = if (type == OrderType.DINE_IN) "Mesa ${1 + rnd.nextInt(14)}" else "",
                        address = if (type == OrderType.DELIVERY) "Calle Ficticia ${1 + rnd.nextInt(200)}, CDMX" else "",
                        createdAt = createdAt,
                        paidAt = createdAt + 25_000,
                        preparingAt = createdAt + 120_000,
                        readyAt = createdAt + 120_000 + prepMs,
                        completedAt = createdAt + 120_000 + prepMs + (4 + rnd.nextInt(12)) * 60_000L,
                        etaMinutes = t.etaMinutes,
                        payment = PaymentInfo(
                            method = methods[rnd.nextInt(methods.size)],
                            status = if (refunded) PaymentStatus.REFUNDED else PaymentStatus.APPROVED,
                            providerPaymentId = "demo-hist-$seq",
                            marketplaceFeeCents = subtotal * feeBps / 10_000,
                        ),
                    ),
                )
            }
        }
        return out
    }
}
