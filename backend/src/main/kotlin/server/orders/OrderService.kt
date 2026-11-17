package server.orders

import app.shared.api.RealtimeEvent
import app.shared.model.*
import app.shared.util.newId
import server.data.insertOrder
import server.data.nextOrderNumber
import server.data.orderById
import server.data.saveOrderState
import server.data.tenantById
import server.db.Db
import server.db.exec
import server.realtime.Realtime

/** Ciclo de vida de los pedidos + publicación de eventos en tiempo real. */
object OrderService {

    suspend fun getOrder(orderId: String): Order? = Db.adminTx { c -> c.orderById(orderId) }

    /** El pedido entra a cocina como "nuevo". */
    fun publishCreated(order: Order) {
        Realtime.publishEvent(Realtime.kitchenChannel(order.tenantId), RealtimeEvent.OrderCreated(order))
        Realtime.publishEvent(Realtime.orderChannel(order.id), RealtimeEvent.OrderUpdated(order))
    }

    /** Cambio de estado: avisa a cocina/panel (si ya fue pagado) y al comensal que sigue su pedido. */
    fun publishUpdated(order: Order) {
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            Realtime.publishEvent(Realtime.kitchenChannel(order.tenantId), RealtimeEvent.OrderUpdated(order))
        }
        Realtime.publishEvent(Realtime.orderChannel(order.id), RealtimeEvent.OrderUpdated(order))
    }

    /**
     * PENDING_PAYMENT → PAID. Fija paidAt y el pago como APPROVED, y publica el pedido a cocina.
     * Idempotente: si ya estaba pagado (o más adelante) devuelve el pedido sin tocarlo.
     */
    suspend fun markPaid(orderId: String, providerPaymentId: String?): Order? {
        var changed = false
        val order = Db.adminTx { c ->
            val current = c.orderById(orderId, forUpdate = true) ?: return@adminTx null
            if (current.status != OrderStatus.PENDING_PAYMENT) return@adminTx current
            val now = System.currentTimeMillis()
            val payment = (current.payment ?: PaymentInfo(PaymentMethod.CARD)).let {
                it.copy(status = PaymentStatus.APPROVED, providerPaymentId = providerPaymentId ?: it.providerPaymentId)
            }
            val paid = current.copy(status = OrderStatus.PAID, paidAt = now, payment = payment)
            c.saveOrderState(paid)
            if (paid.customerPhone.isNotBlank()) {
                c.exec(
                    "INSERT INTO customers (tenant_id, id, name, phone, orders_count, total_spent_cents, last_order_at) VALUES (?::uuid, ?, ?, ?, 1, ?, ?) " +
                        "ON CONFLICT (tenant_id, phone) DO UPDATE SET name = EXCLUDED.name, orders_count = customers.orders_count + 1, " +
                        "total_spent_cents = customers.total_spent_cents + EXCLUDED.total_spent_cents, last_order_at = EXCLUDED.last_order_at",
                    paid.tenantId, newId("cus"), paid.customerName, paid.customerPhone.trim(), paid.totalCents, now,
                )
            }
            changed = true
            paid
        }
        if (order != null && changed) publishCreated(order)
        return order
    }

    /** Marca el pedido como REFUNDED (pago REFUNDED). Idempotente. */
    suspend fun markRefunded(orderId: String): Order? {
        var changed = false
        val order = Db.adminTx { c ->
            val current = c.orderById(orderId, forUpdate = true) ?: return@adminTx null
            if (current.status == OrderStatus.REFUNDED) return@adminTx current
            val refunded = current.copy(
                status = OrderStatus.REFUNDED,
                payment = current.payment?.copy(status = PaymentStatus.REFUNDED),
            )
            c.saveOrderState(refunded)
            changed = true
            refunded
        }
        if (order != null && changed) {
            Realtime.publishEvent(Realtime.kitchenChannel(order.tenantId), RealtimeEvent.OrderUpdated(order))
            Realtime.publishEvent(Realtime.orderChannel(order.id), RealtimeEvent.OrderUpdated(order))
        }
        return order
    }

    /** Pedido que llega ya cobrado por un marketplace (Uber Eats, Rappi, DiDi Food): entra directo a cocina. */
    suspend fun createExternalOrder(
        tenantId: String,
        channel: OrderChannel,
        type: OrderType,
        customerName: String,
        lines: List<OrderLine>,
    ): Order {
        val order = Db.adminTx { c ->
            val tenant = c.tenantById(tenantId) ?: throw IllegalArgumentException("Restaurante no encontrado")
            val now = System.currentTimeMillis()
            val subtotal = lines.sumOf { it.lineTotalCents }
            val created = Order(
                id = newId("ord"),
                tenantId = tenant.public.id,
                tenantSlug = tenant.public.slug,
                number = c.nextOrderNumber(tenant.public.id),
                status = OrderStatus.PAID,
                type = type,
                channel = channel,
                lines = lines,
                subtotalCents = subtotal,
                totalCents = subtotal,
                customerName = customerName,
                createdAt = now,
                paidAt = now,
                etaMinutes = tenant.public.etaMinutes,
                payment = PaymentInfo(
                    method = PaymentMethod.CARD,
                    status = PaymentStatus.APPROVED,
                    provider = channel.name.lowercase(),
                    providerPaymentId = newId("ext"),
                ),
            )
            c.insertOrder(created)
            created
        }
        publishCreated(order)
        return order
    }
}
