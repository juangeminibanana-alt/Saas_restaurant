package server.integrations

import app.shared.api.ApiError
import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.model.IntegrationProvider
import app.shared.model.IntegrationStatus
import app.shared.model.Order
import app.shared.model.OrderType
import app.shared.model.Role
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import server.auth.authenticated
import server.auth.user
import server.db.Db
import server.orders.OrderService
import server.payments.Payments
import java.util.UUID
import kotlin.random.Random

private val STATUS_LIST = ListSerializer(IntegrationStatus.serializer())

private suspend fun ApplicationCall.fail(status: HttpStatusCode, message: String, code: String = "error") =
    respondText(AppJson.encodeToString(ApiError.serializer(), ApiError(message, code)), ContentType.Application.Json, status)

private suspend fun ApplicationCall.tenantOrForbid(): String? {
    val id = user.tenantId?.takeIf { it.isNotBlank() }
    if (id == null) fail(HttpStatusCode.Forbidden, "Esta sección es solo para restaurantes.", "forbidden")
    return id
}

private fun providerOf(raw: String?): IntegrationProvider? =
    IntegrationProvider.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

/** Estado de las integraciones del restaurante (lee con el rol RLS). */
internal suspend fun integrationStatuses(tenantId: String): List<IntegrationStatus> {
    data class Snapshot(val mpConnected: Boolean, val mpLive: Boolean, val connected: Set<String>)

    val snap = Db.tenantTx(tenantId) { c ->
        var mpConnected = false
        var mpLive = false
        c.prepareStatement("SELECT live_mode FROM mp_connections WHERE tenant_id = ?::uuid").use { ps ->
            ps.setString(1, tenantId)
            ps.executeQuery().use { rs -> if (rs.next()) { mpConnected = true; mpLive = rs.getBoolean(1) } }
        }
        val connected = HashSet<String>()
        c.prepareStatement("SELECT provider FROM integration_connections WHERE tenant_id = ?::uuid AND connected").use { ps ->
            ps.setString(1, tenantId)
            ps.executeQuery().use { rs -> while (rs.next()) connected.add(rs.getString(1)) }
        }
        Snapshot(mpConnected, mpLive, connected)
    }
    val delivery = Deliveries.provider
    return listOf(
        IntegrationStatus(
            provider = IntegrationProvider.MERCADO_PAGO,
            name = "Mercado Pago",
            description = "Cobra con tarjeta, OXXO, SPEI y saldo de Mercado Pago. El dinero cae directo en tu cuenta.",
            category = "Pagos",
            connected = snap.mpConnected,
            mode = when {
                Payments.demoMode -> "mock"
                snap.mpConnected && snap.mpLive -> "live"
                else -> "sandbox"
            },
        ),
        IntegrationStatus(
            provider = IntegrationProvider.LALAMOVE,
            name = "Lalamove",
            description = "Repartidores bajo demanda para tus pedidos a domicilio, con cotización y seguimiento.",
            category = "Reparto",
            connected = IntegrationProvider.LALAMOVE.name in snap.connected,
            mode = delivery.mode,
        ),
        marketplace(IntegrationProvider.UBER_EATS, "Uber Eats", "Recibe los pedidos de Uber Eats en la misma pantalla de cocina.", snap.connected),
        marketplace(IntegrationProvider.RAPPI, "Rappi", "Recibe los pedidos de Rappi en la misma pantalla de cocina.", snap.connected),
        marketplace(IntegrationProvider.DIDI_FOOD, "DiDi Food", "Recibe los pedidos de DiDi Food en la misma pantalla de cocina.", snap.connected),
    )
}

private fun marketplace(provider: IntegrationProvider, name: String, description: String, connected: Set<String>) =
    IntegrationStatus(
        provider = provider,
        name = name,
        description = description,
        category = "Marketplaces",
        connected = provider.name in connected,
        mode = if (Marketplaces.connector(provider) is MockMarketplaceConnector) "mock" else "sandbox",
    )

private suspend fun toggle(tenantId: String, provider: IntegrationProvider) {
    if (provider == IntegrationProvider.MERCADO_PAGO) {
        // Demo: conecta/desconecta directo. Con credenciales reales solo se puede desconectar aquí;
        // conectar exige el flujo OAuth (POST TENANT_MP_CONNECT).
        val connected = Db.tenantTx(tenantId) { c ->
            c.prepareStatement("SELECT 1 FROM mp_connections WHERE tenant_id = ?::uuid").use { ps ->
                ps.setString(1, tenantId)
                ps.executeQuery().use { it.next() }
            }
        }
        if (connected) {
            Db.tenantTx(tenantId) { c ->
                c.prepareStatement("DELETE FROM mp_connections WHERE tenant_id = ?::uuid").use { ps ->
                    ps.setString(1, tenantId)
                    ps.executeUpdate()
                }
            }
        } else if (Payments.demoMode) {
            Payments.gateway.startConnect(tenantId)
        }
        return
    }
    Db.tenantTx(tenantId) { c ->
        c.prepareStatement(
            "INSERT INTO integration_connections (tenant_id, provider, connected) VALUES (?::uuid, ?, true) " +
                "ON CONFLICT (tenant_id, provider) DO UPDATE SET connected = NOT integration_connections.connected, updated_at = now()",
        ).use { ps ->
            ps.setString(1, tenantId)
            ps.setString(2, provider.name)
            ps.executeUpdate()
        }
    }
}

private data class SimItem(val id: String, val name: String, val priceCents: Long, val imageUrl: String?)

private val SIM_CUSTOMERS = listOf("Mariana G.", "Carlos R.", "Fernanda L.", "Diego M.", "Valeria T.", "Andrés P.", "Sofía H.", "Luis C.")

/** Simula un pedido entrante del marketplace con 1-3 platillos disponibles del menú del restaurante. */
private suspend fun simulateOrder(tenantId: String, provider: IntegrationProvider): Order? {
    val connector = Marketplaces.connector(provider) ?: return null
    val mock = connector as? MockMarketplaceConnector ?: MockMarketplaceConnector(provider, connector.channel)
    val items = Db.tenantTx(tenantId) { c ->
        c.prepareStatement(
            "SELECT id, name, price_cents, image_url FROM menu_items WHERE tenant_id = ?::uuid AND available ORDER BY random() LIMIT ?",
        ).use { ps ->
            ps.setString(1, tenantId)
            ps.setInt(2, Random.nextInt(1, 4))
            ps.executeQuery().use { rs ->
                val out = ArrayList<SimItem>()
                while (rs.next()) out.add(SimItem(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)))
                out
            }
        }
    }
    if (items.isEmpty()) return null
    // Mismo camino que un webhook real: payload → conector → pedido normalizado → cocina.
    val payload = buildJsonObject {
        put("externalId", provider.name.lowercase() + "-" + UUID.randomUUID().toString().take(8))
        put("customerName", SIM_CUSTOMERS.random())
        putJsonArray("items") {
            for (i in items) add(buildJsonObject {
                put("itemId", i.id)
                put("name", i.name)
                put("quantity", Random.nextInt(1, 3))
                put("unitPriceCents", i.priceCents)
                put("imageUrl", i.imageUrl)
            })
        }
    }.toString()
    val incoming = mock.parseIncomingOrder(payload) ?: return null
    val order = OrderService.createExternalOrder(tenantId, incoming.channel, OrderType.DELIVERY, incoming.customerName, incoming.lines)
    connector.acceptOrder(incoming.externalId)
    return order
}

internal fun Route.integrationRoutes() {
    authenticated(Role.OWNER, Role.MANAGER) {
        get(ApiRoutes.TENANT_INTEGRATIONS) {
            val tenantId = call.tenantOrForbid() ?: return@get
            call.respondText(AppJson.encodeToString(STATUS_LIST, integrationStatuses(tenantId)), ContentType.Application.Json)
        }

        post(ApiRoutes.TENANT_INTEGRATIONS + "/{provider}/toggle") {
            val tenantId = call.tenantOrForbid() ?: return@post
            val provider = providerOf(call.parameters["provider"])
                ?: return@post call.fail(HttpStatusCode.NotFound, "Integración desconocida.", "not_found")
            toggle(tenantId, provider)
            call.respondText(AppJson.encodeToString(STATUS_LIST, integrationStatuses(tenantId)), ContentType.Application.Json)
        }

        post(ApiRoutes.TENANT_INTEGRATIONS + "/{provider}/simulate-order") {
            val tenantId = call.tenantOrForbid() ?: return@post
            val provider = providerOf(call.parameters["provider"])
            if (provider == null || Marketplaces.channelOf(provider) == null) {
                return@post call.fail(HttpStatusCode.BadRequest, "Solo se pueden simular pedidos de Uber Eats, Rappi o DiDi Food.", "bad_request")
            }
            val order = simulateOrder(tenantId, provider)
                ?: return@post call.fail(HttpStatusCode.Conflict, "Agrega al menos un platillo disponible al menú para simular un pedido.", "empty_menu")
            call.respondText(AppJson.encodeToString(Order.serializer(), order), ContentType.Application.Json, HttpStatusCode.Created)
        }
    }
}
