package app.feature.diner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.label
import app.shared.util.relativeTime
import app.shell.LocalRepo
import app.ui.components.AppButton
import app.ui.components.AppCard
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.LabelText
import app.ui.components.OrderStatusTimeline
import app.ui.components.StatusPill
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.components.edgeTop
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.CancellationException

/** Ancho de la columna de cantidad ("2×") en el resumen del pedido. */
private val QuantityColumn = Space.x8

/** Seguimiento del pedido en vivo: el número en grande y en mono, la línea de estado, la cuenta y los datos del restaurante. */
@Composable
fun TrackingScreen(state: DinerState, tenant: TenantPublic) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val order = state.activeOrder
    val gutter = dinerGutter(LocalWindowSize.current == WindowSize.COMPACT)

    LaunchedEffect(order?.id) {
        val id = order?.id ?: return@LaunchedEffect
        try {
            repo.trackOrder(id).collect { updated -> state.recordOrder(updated) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión momentánea: se conserva el último estado conocido.
        }
    }

    Column(Modifier.fillMaxSize()) {
        DinerTopBar(title = "Tu pedido", onBack = { state.screen = DinerScreen.MENU }, subtitle = tenant.name)
        if (order == null) {
            EmptyState(
                title = "Aún no tienes un pedido en curso",
                message = "Cuando pagues un pedido lo verás aquí en tiempo real.",
                icon = AppIcons.Receipt,
                actionText = "Ver el menú",
                onAction = { state.screen = DinerScreen.MENU },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = DinerPageWidth).fillMaxWidth().padding(start = gutter, end = gutter, top = Space.x6, bottom = Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x6),
            ) {
                // Cabecera: el número del pedido es el dato de la pantalla.
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LabelText("Pedido", modifier = Modifier.weight(1f))
                        StatusPill(order.status.dinerLabel(), tone = order.status.tone())
                    }
                    Spacer(Modifier.height(Space.x1))
                    AppText(order.number, style = orderNumberStyle, maxLines = 1)
                    Spacer(Modifier.height(Space.x2))
                    AppText(etaText(order), style = AppTheme.type.body, color = colors.inkMuted)
                }

                AppCard(padding = Space.x4) {
                    OrderStatusTimeline(order, Modifier.fillMaxWidth())
                }

                AppCard(padding = Space.x4) {
                    GroupHead(
                        "Resumen",
                        label = listOfNotNull(
                            order.type.label(),
                            order.tableLabel.takeIf { it.isNotBlank() }?.let { if (it.lowercase().startsWith("mesa")) it else "Mesa $it" },
                            order.customerName.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    )
                    order.lines.forEachIndexed { index, line ->
                        Row(
                            Modifier.fillMaxWidth().then(if (index == 0) Modifier else Modifier.edgeTop(colors.line)).padding(vertical = Space.x3),
                            verticalAlignment = Alignment.Top,
                        ) {
                            AppText("${line.quantity}×", Modifier.width(QuantityColumn), style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
                            Column(Modifier.weight(1f)) {
                                AppText(line.name, style = AppTheme.type.title)
                                val detail = (line.modifiers.map { it.optionName } + listOfNotNull(line.notes.takeIf { it.isNotBlank() }))
                                    .joinToString(" · ")
                                if (detail.isNotBlank()) AppText(detail, style = AppTheme.type.bodySm, color = colors.inkMuted)
                            }
                            Spacer(Modifier.width(Space.x3))
                            AppText(formatMoney(line.lineTotalCents), style = AppTheme.type.mono, maxLines = 1)
                        }
                    }
                    Column(Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x3)) {
                        TotalsRow("Subtotal", order.subtotalCents)
                        if (order.deliveryFeeCents > 0) TotalsRow("Envío", order.deliveryFeeCents)
                        if (order.tipCents > 0) TotalsRow("Propina", order.tipCents)
                        TotalsRow("Total", order.totalCents, strong = true)
                        val method = order.payment?.method
                        if (method != null) {
                            Spacer(Modifier.height(Space.x2))
                            InfoLine(paymentIcon(method), "Pagado con ${method.label()} · Mercado Pago")
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x4)) {
                    LabelText(tenant.name)
                    if (tenant.address.isNotBlank()) {
                        Spacer(Modifier.height(Space.x1))
                        AppText(tenant.address, style = AppTheme.type.bodySm, color = colors.inkMuted)
                    }
                    if (tenant.phone.isNotBlank()) AppText(tenant.phone, style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
                }

                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    AppButton("Hacer otro pedido", onClick = { state.startNewOrder() }, size = ButtonSize.LG, fullWidth = true)
                    AppButton("Mis pedidos", onClick = { state.screen = DinerScreen.HISTORY }, variant = ButtonVariant.SECONDARY, fullWidth = true)
                }
            }
        }
    }
}

/** Historial de la visita con "Pedir de nuevo". */
@Composable
fun HistoryScreen(state: DinerState, tenant: TenantPublic) {
    val windowSize = LocalWindowSize.current
    val gutter = dinerGutter(windowSize == WindowSize.COMPACT)
    val expanded = windowSize == WindowSize.EXPANDED
    Column(Modifier.fillMaxSize()) {
        DinerTopBar(title = "Mis pedidos", onBack = { state.screen = DinerScreen.MENU }, subtitle = tenant.name)
        if (state.history.isEmpty()) {
            EmptyState(
                title = "Todavía no has pedido",
                message = "Tus pedidos de esta visita aparecerán aquí para que puedas repetirlos con un toque.",
                icon = AppIcons.Receipt,
                actionText = "Ver el menú",
                onAction = { state.screen = DinerScreen.MENU },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = DinerPageWidth).fillMaxWidth().padding(start = gutter, end = gutter, top = Space.x6, bottom = Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                state.history.forEach { order -> HistoryCard(order, state, expanded) }
            }
        }
    }
}

/** Un pedido de la visita: número y total en mono, estado, lo que llevaba y las dos acciones. */
@Composable
private fun HistoryCard(order: Order, state: DinerState, expanded: Boolean) {
    val colors = AppTheme.colors
    AppCard(padding = Space.x4) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LabelText(relativeTime(order.createdAt), modifier = Modifier.weight(1f))
            StatusPill(order.status.dinerLabel(), tone = order.status.tone())
        }
        Spacer(Modifier.height(Space.x1))
        Row(Modifier.fillMaxWidth()) {
            AppText(order.number, modifier = Modifier.weight(1f).alignByBaseline(), style = AppTheme.type.monoLg, maxLines = 1)
            AppText(formatMoney(order.totalCents), modifier = Modifier.alignByBaseline(), style = AppTheme.type.monoMd, maxLines = 1)
        }
        Spacer(Modifier.height(Space.x2))
        AppText(
            order.lines.joinToString(" · ") { "${it.quantity}× ${it.name}" },
            style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 2,
        )
        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            AppButton(
                "Ver pedido",
                onClick = {
                    state.activeOrder = order
                    state.screen = if (order.status == OrderStatus.PENDING_PAYMENT && state.checkoutUrl != null) DinerScreen.WAITING else DinerScreen.TRACKING
                },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.SECONDARY, size = ButtonSize.SM,
            )
            AppButton(
                "Pedir de nuevo",
                onClick = {
                    val added = state.reorder(order)
                    if (added > 0) {
                        Toasts.show("Agregamos $added ${if (added == 1) "platillo" else "platillos"} a tu pedido.", Tone.SUCCESS)
                        state.screen = if (expanded) DinerScreen.MENU else DinerScreen.CART
                    } else {
                        Toasts.show("Esos platillos ya no están disponibles.", Tone.WARNING)
                    }
                },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.SECONDARY, size = ButtonSize.SM,
            )
        }
    }
}
