package server.routes

import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.api.RealtimeEvent
import app.shared.model.Role
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import server.auth.Tokens
import server.orders.OrderService
import server.realtime.Realtime

/**
 * Conecta una sesión WebSocket a un canal del bus: reenvía cada mensaje como frame de texto.
 * Los pings los manda el plugin (pingPeriod); al cerrar se da de baja el listener.
 */
private suspend fun DefaultWebSocketServerSession.pipe(channel: String, initial: String? = null) {
    val buffer = Channel<String>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val subscription = Realtime.subscribe(channel) { buffer.trySend(it) }
    try {
        if (initial != null) send(Frame.Text(initial))
        val writer = launch {
            for (message in buffer) send(Frame.Text(message))
        }
        try {
            for (frame in incoming) {
                // El cliente no manda nada útil; solo mantenemos viva la sesión hasta que cierre.
            }
        } finally {
            writer.cancel()
        }
    } finally {
        subscription.close()
        buffer.close()
    }
}

fun Route.realtimeRoutes() {
    // Cocina / panel: pedidos pagados que entran y sus cambios. Token por query (los navegadores no mandan headers en WS).
    webSocket(ApiRoutes.WS_TENANT_ORDERS) {
        val session = call.request.queryParameters["token"]?.let { Tokens.verify(it) }
        val tenantId = session?.tenantId
        if (session == null || tenantId == null || session.role == Role.OPERATOR) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No autorizado"))
            return@webSocket
        }
        pipe(Realtime.kitchenChannel(tenantId))
    }

    // Comensal: seguimiento de SU pedido (el id es el secreto compartido).
    webSocket("/ws/public/orders/{orderId}") {
        val orderId = call.parameters["orderId"].orEmpty()
        val order = OrderService.getOrder(orderId)
        if (order == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Pedido no encontrado"))
            return@webSocket
        }
        val initial = AppJson.encodeToString(RealtimeEvent.serializer(), RealtimeEvent.OrderUpdated(order))
        pipe(Realtime.orderChannel(orderId), initial)
    }
}
