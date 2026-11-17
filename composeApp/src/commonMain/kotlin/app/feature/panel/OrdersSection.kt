package app.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shared.api.RealtimeEvent
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.OrderType
import app.shared.util.formatMoney
import app.shared.util.label
import app.shared.util.nowMillis
import app.shared.util.relativeTime
import app.shell.LocalRepo
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private fun Order.typeText(): String =
    if (type == OrderType.DINE_IN && tableLabel.isNotBlank()) "${type.label()} · $tableLabel" else type.label()

private fun Order.customerText(): String = customerName.ifBlank { "Cliente sin nombre" }

@Composable
internal fun OrdersSection() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val size = LocalWindowSize.current

    var orders by remember { mutableStateOf<List<Order>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) } // 0 = activos, 1 = todos
    var selectedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val now by produceState(nowMillis()) {
        while (true) {
            delay(30_000)
            value = nowMillis()
        }
    }

    fun merge(order: Order) {
        val list = orders.orEmpty()
        orders = if (list.any { it.id == order.id }) list.map { if (it.id == order.id) order else it } else listOf(order) + list
    }

    LaunchedEffect(reloadTick) {
        refreshing = true
        try {
            orders = repo.listOrders(false)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = errorText(e, "No pudimos cargar los pedidos")
            if (orders == null) error = message else Toasts.show(message, Tone.DANGER)
        } finally {
            refreshing = false
        }
    }
    LaunchedEffect(Unit) {
        try {
            repo.kitchenEvents().collect { event ->
                when (event) {
                    is RealtimeEvent.OrderCreated -> merge(event.order)
                    is RealtimeEvent.OrderUpdated -> merge(event.order)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Sin tiempo real seguimos con recarga manual.
        }
    }

    fun act(fallback: String, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toasts.show(errorText(e, fallback), Tone.DANGER)
            } finally {
                busy = false
            }
        }
    }

    val all = orders
    val activeCount = all?.count { it.status.isActive() } ?: 0

    PanelPage {
        val subtitle = if (all == null) "Todos tus canales en una sola lista, en tiempo real"
        else "$activeCount ${if (activeCount == 1) "pedido activo" else "pedidos activos"} · ${all.size} recientes"
        if (size == WindowSize.COMPACT) {
            PageHeader("Pedidos", subtitle = subtitle)
        } else {
            PageHeader("Pedidos", subtitle = subtitle) {
                AppButton("Actualizar", onClick = { reloadTick++ }, variant = ButtonVariant.SECONDARY, icon = AppIcons.Refresh, loading = refreshing && all != null)
            }
        }
        SegmentedControl(
            options = listOf("Activos", "Todos"), selectedIndex = filter, onSelect = { filter = it },
            modifier = if (size == WindowSize.COMPACT) Modifier.fillMaxWidth() else Modifier.width(280.dp),
        )

        val err = error
        when {
            all == null && err != null -> ErrorState(err, onRetry = { reloadTick++ })
            all == null -> SkeletonRows(rows = 6)
            else -> {
                val visible = (if (filter == 0) all.filter { it.status.isActive() } else all).sortedByDescending { it.createdAt }
                if (visible.isEmpty()) {
                    AppCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            if (filter == 0) "Sin pedidos activos" else "Aún no hay pedidos",
                            message = if (filter == 0) "Cuando entre un pedido pagado aparecerá aquí al instante." else "Comparte tu enlace o tu QR para recibir el primero.",
                            icon = AppIcons.Receipt,
                        )
                    }
                } else if (size == WindowSize.EXPANDED) {
                    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                            val head = AppTheme.type.label
                            val muted = AppTheme.colors.inkSubtle
                            AppText("PEDIDO", Modifier.width(84.dp), style = head, color = muted)
                            AppText("ESTADO", Modifier.width(150.dp), style = head, color = muted)
                            AppText("CANAL", Modifier.weight(1f), style = head, color = muted)
                            AppText("TIPO", Modifier.weight(1.2f), style = head, color = muted)
                            AppText("CLIENTE", Modifier.weight(1.4f), style = head, color = muted)
                            AppText("TOTAL", Modifier.width(96.dp), style = head, color = muted, textAlign = TextAlign.End)
                            AppText("HORA", Modifier.width(96.dp), style = head, color = muted, textAlign = TextAlign.End)
                            Spacer(Modifier.width(18.dp))
                        }
                        visible.forEach { order ->
                            key(order.id) {
                                AppDivider()
                                OrderTableRow(order = order, now = now, onClick = { selectedId = order.id })
                            }
                        }
                    }
                } else {
                    SimpleGrid(items = visible, columns = if (size == WindowSize.MEDIUM) 2 else 1, spacing = Space.x3) { order ->
                        OrderCard(order = order, now = now, onClick = { selectedId = order.id })
                    }
                }
            }
        }
    }

    val selected = all?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        OrderDetailDialog(
            order = selected, busy = busy,
            onDismiss = { selectedId = null },
            onStatus = { status ->
                act("No pudimos actualizar el pedido") {
                    merge(repo.updateOrderStatus(selected.id, status))
                    Toasts.show("Pedido ${selected.number}: ${status.label().lowercase()}", Tone.SUCCESS)
                }
            },
            onRefund = {
                act("No pudimos reembolsar el pedido") {
                    merge(repo.refundOrder(selected.id))
                    Toasts.show("Pedido ${selected.number} reembolsado", Tone.SUCCESS)
                }
            },
        )
    }
}

@Composable
private fun OrderTableRow(order: Order, now: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(scale = 0.995f, onClick = onClick).padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        AppText(order.number, Modifier.width(84.dp), style = AppTheme.type.title, maxLines = 1)
        Box(Modifier.width(150.dp)) { StatusPill(order.status.label(), tone = order.status.tone()) }
        AppText(order.channel.label(), Modifier.weight(1f), style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
        AppText(order.typeText(), Modifier.weight(1.2f), style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
        AppText(order.customerText(), Modifier.weight(1.4f), maxLines = 1)
        AppText(formatMoney(order.totalCents), Modifier.width(96.dp), style = AppTheme.type.title, textAlign = TextAlign.End, maxLines = 1)
        AppText(relativeTime(order.createdAt, now), Modifier.width(96.dp), style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle, textAlign = TextAlign.End, maxLines = 1)
        PanelIcon(AppIcons.ChevronRight, tint = AppTheme.colors.inkSubtle)
    }
}

@Composable
private fun OrderCard(order: Order, now: Long, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppText(order.number, Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
                StatusPill(order.status.label(), tone = order.status.tone())
            }
            AppText(order.customerText(), maxLines = 1)
            AppText("${order.typeText()} · ${order.channel.label()}", style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(formatMoney(order.totalCents), Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
                AppText(relativeTime(order.createdAt, now), style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle, maxLines = 1)
            }
        }
    }
}

@Composable
private fun OrderDetailDialog(order: Order, busy: Boolean, onDismiss: () -> Unit, onStatus: (OrderStatus) -> Unit, onRefund: () -> Unit) {
    val c = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    var confirmRefund by remember(order.id, order.status) { mutableStateOf(false) }
    val next: Pair<OrderStatus, String>? = when (order.status) {
        OrderStatus.PAID -> OrderStatus.PREPARING to "Empezar preparación"
        OrderStatus.PREPARING -> OrderStatus.READY to "Marcar como listo"
        OrderStatus.READY -> OrderStatus.COMPLETED to "Marcar como entregado"
        else -> null
    }
    val canRefund = order.status == OrderStatus.PAID || order.status == OrderStatus.PREPARING || order.status == OrderStatus.READY || order.status == OrderStatus.COMPLETED
    val canCancel = order.status == OrderStatus.PENDING_PAYMENT

    AppDialog(onDismiss = onDismiss, title = "Pedido ${order.number}", maxWidth = 600.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = if (compact) 440.dp else 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                StatusPill(order.status.label(), tone = order.status.tone())
                TagBadge(order.channel.label())
                TagBadge(order.typeText())
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(order.customerText(), style = AppTheme.type.title)
                if (order.customerPhone.isNotBlank()) AppText(order.customerPhone, style = AppTheme.type.bodySm, color = c.inkMuted)
                if (order.address.isNotBlank()) AppText(order.address, style = AppTheme.type.bodySm, color = c.inkMuted)
                AppText("Recibido ${relativeTime(order.createdAt)}", style = AppTheme.type.bodySm, color = c.inkSubtle)
            }

            if (order.status != OrderStatus.CANCELLED && order.status != OrderStatus.REFUNDED && order.status != OrderStatus.PENDING_PAYMENT) {
                OrderStatusTimeline(order = order, modifier = Modifier.fillMaxWidth())
            }

            AppDivider()
            order.lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    AppText("${line.quantity}×", style = AppTheme.type.title, color = c.inkMuted)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AppText(line.name, style = AppTheme.type.title)
                        line.modifiers.forEach { m ->
                            AppText(
                                "${m.groupName}: ${m.optionName}" + if (m.priceDeltaCents != 0L) " (+${formatMoney(m.priceDeltaCents)})" else "",
                                style = AppTheme.type.bodySm, color = c.inkMuted,
                            )
                        }
                        if (line.notes.isNotBlank()) AppText("Nota: ${line.notes}", style = AppTheme.type.bodySm, color = c.warning)
                    }
                    AppText(formatMoney(line.lineTotalCents), maxLines = 1)
                }
            }
            if (order.notes.isNotBlank()) {
                Row(Modifier.fillMaxWidth().clip(AppTheme.shapes.sm).background(c.warningSoft).padding(Space.x3), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    PanelIcon(AppIcons.Info, tint = c.warning)
                    AppText(order.notes, Modifier.weight(1f), style = AppTheme.type.bodySm)
                }
            }
            AppDivider()
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                KeyValueRow("Subtotal", formatMoney(order.subtotalCents))
                if (order.deliveryFeeCents != 0L) KeyValueRow("Envío", formatMoney(order.deliveryFeeCents))
                if (order.tipCents != 0L) KeyValueRow("Propina", formatMoney(order.tipCents))
                KeyValueRow("Total", formatMoney(order.totalCents), strong = true)
                order.payment?.let { p ->
                    AppText("Pago: ${p.method.label()}", style = AppTheme.type.bodySm, color = c.inkSubtle)
                }
            }

            if (confirmRefund) {
                Column(Modifier.fillMaxWidth().clip(AppTheme.shapes.md).background(c.dangerSoft).padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    AppText("¿Reembolsar ${formatMoney(order.totalCents)} a ${order.customerText()}?", style = AppTheme.type.title)
                    AppText("El dinero regresa al método de pago original y el pedido queda como reembolsado.", style = AppTheme.type.bodySm, color = c.inkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        AppButton("Sí, reembolsar", onClick = onRefund, variant = ButtonVariant.DANGER, loading = busy)
                        AppButton("No, volver", onClick = { confirmRefund = false }, variant = ButtonVariant.GHOST)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x4))
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                if (next != null) AppButton(next.second, onClick = { onStatus(next.first) }, loading = busy && !confirmRefund, fullWidth = true)
                if (canCancel) AppButton("Cancelar pedido", onClick = { onStatus(OrderStatus.CANCELLED) }, variant = ButtonVariant.DANGER, loading = busy, fullWidth = true)
                if (canRefund && !confirmRefund) AppButton("Reembolsar", onClick = { confirmRefund = true }, variant = ButtonVariant.SECONDARY, fullWidth = true)
                AppButton("Cerrar", onClick = onDismiss, variant = ButtonVariant.GHOST, fullWidth = true)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                if (canRefund && !confirmRefund) AppButton("Reembolsar", onClick = { confirmRefund = true }, variant = ButtonVariant.DANGER)
                if (canCancel) AppButton("Cancelar pedido", onClick = { onStatus(OrderStatus.CANCELLED) }, variant = ButtonVariant.DANGER, loading = busy)
                Spacer(Modifier.weight(1f))
                AppButton("Cerrar", onClick = onDismiss, variant = ButtonVariant.GHOST)
                if (next != null) AppButton(next.second, onClick = { onStatus(next.first) }, loading = busy && !confirmRefund)
            }
        }
    }
}
