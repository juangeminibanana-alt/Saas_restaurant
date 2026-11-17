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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.label
import app.shared.util.relativeTime
import app.shell.LocalRepo
import app.ui.components.AppButton
import app.ui.components.AppCard
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.OrderStatusTimeline
import app.ui.components.StatusPill
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.CancellationException

/** Rastreo del pedido en vivo: número grande, línea de estado animada, resumen y datos del restaurante. */
@Composable
fun TrackingScreen(state: DinerState, tenant: TenantPublic) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val order = state.activeOrder
    val compact = LocalWindowSize.current == WindowSize.COMPACT

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
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = if (compact) Space.x4 else Space.x6, vertical = Space.x6),
                verticalArrangement = Arrangement.spacedBy(Space.x5),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("PEDIDO", style = AppTheme.type.label, color = colors.inkSubtle)
                    Spacer(Modifier.height(Space.x1))
                    AppText(order.number, style = AppTheme.type.display)
                    Spacer(Modifier.height(Space.x3))
                    StatusPill(order.status.dinerLabel(), tone = order.status.tone())
                    Spacer(Modifier.height(Space.x3))
                    AppText(etaText(order), color = colors.inkMuted, textAlign = TextAlign.Center)
                }

                AppCard(elevated = true, padding = Space.x5) {
                    OrderStatusTimeline(order, Modifier.fillMaxWidth())
                }

                AppCard(padding = Space.x5) {
                    AppText("Resumen", style = AppTheme.type.title)
                    Spacer(Modifier.height(Space.x1))
                    AppText(
                        listOfNotNull(
                            order.type.label(),
                            order.tableLabel.takeIf { it.isNotBlank() }?.let { if (it.lowercase().startsWith("mesa")) it else "Mesa $it" },
                            order.customerName.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = AppTheme.type.bodySm, color = colors.inkMuted,
                    )
                    Spacer(Modifier.height(Space.x4))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                        order.lines.forEach { line ->
                            Row(verticalAlignment = Alignment.Top) {
                                AppText("${line.quantity}×", Modifier.width(34.dp), style = AppTheme.type.title, color = colors.primary)
                                Column(Modifier.weight(1f)) {
                                    AppText(line.name)
                                    val detail = (line.modifiers.map { it.optionName } + listOfNotNull(line.notes.takeIf { it.isNotBlank() }))
                                        .joinToString(" · ")
                                    if (detail.isNotBlank()) AppText(detail, style = AppTheme.type.bodySm, color = colors.inkMuted)
                                }
                                Spacer(Modifier.width(Space.x3))
                                AppText(formatMoney(line.lineTotalCents))
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.x4))
                    AppDivider()
                    Spacer(Modifier.height(Space.x4))
                    TotalsRow("Subtotal", order.subtotalCents)
                    if (order.deliveryFeeCents > 0) TotalsRow("Envío", order.deliveryFeeCents)
                    if (order.tipCents > 0) TotalsRow("Propina", order.tipCents)
                    TotalsRow("Total", order.totalCents, strong = true)
                    val method = order.payment?.method
                    if (method != null) {
                        Spacer(Modifier.height(Space.x3))
                        InfoLine(paymentIcon(method), "Pagado con ${method.label()} · Mercado Pago")
                    }
                }

                AppCard(padding = Space.x5) {
                    AppText(tenant.name, style = AppTheme.type.title)
                    Spacer(Modifier.height(Space.x3))
                    if (tenant.address.isNotBlank()) InfoLine(AppIcons.Location, tenant.address)
                    if (tenant.phone.isNotBlank()) {
                        Spacer(Modifier.height(Space.x2))
                        InfoLine(AppIcons.Phone, tenant.phone)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    AppButton("Hacer otro pedido", onClick = { state.startNewOrder() }, size = ButtonSize.LG, fullWidth = true)
                    AppButton("Mis pedidos", onClick = { state.screen = DinerScreen.HISTORY }, variant = ButtonVariant.SECONDARY, fullWidth = true)
                }
                Spacer(Modifier.height(Space.x6))
            }
        }
    }
}

/** Historial de la sesión con "Pedir de nuevo". */
@Composable
fun HistoryScreen(state: DinerState, tenant: TenantPublic) {
    val colors = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED
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
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = if (compact) Space.x4 else Space.x6, vertical = Space.x5),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                state.history.forEach { order -> HistoryCard(order, state, expanded) }
                Spacer(Modifier.height(Space.x6))
            }
        }
    }
}

@Composable
private fun HistoryCard(order: Order, state: DinerState, expanded: Boolean) {
    val colors = AppTheme.colors
    AppCard(padding = Space.x5) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(order.number, style = AppTheme.type.h3)
                AppText(relativeTime(order.createdAt), style = AppTheme.type.bodySm, color = colors.inkSubtle)
            }
            StatusPill(order.status.dinerLabel(), tone = order.status.tone())
        }
        Spacer(Modifier.height(Space.x3))
        AppText(
            order.lines.joinToString(" · ") { "${it.quantity}× ${it.name}" },
            style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 2,
        )
        Spacer(Modifier.height(Space.x3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(formatMoney(order.totalCents), Modifier.weight(1f), style = AppTheme.type.title)
            AppButton(
                "Ver",
                onClick = {
                    state.activeOrder = order
                    state.screen = if (order.status == OrderStatus.PENDING_PAYMENT && state.checkoutUrl != null) DinerScreen.WAITING else DinerScreen.TRACKING
                },
                variant = ButtonVariant.GHOST, size = ButtonSize.SM,
            )
            Spacer(Modifier.width(Space.x2))
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
                variant = ButtonVariant.SECONDARY, size = ButtonSize.SM,
            )
        }
    }
}
