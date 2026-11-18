package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.Dp
import app.platform.openExternalUrl
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.label
import app.shell.LocalRepo
import app.ui.components.AppBottomSheet
import app.ui.components.AppButton
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.LabelText
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

/** Pago de prueba (sandbox): resumen → procesando → aprobado. */
@Composable
fun SandboxPaymentSheet(state: DinerState, viewportHeight: Dp, simulate: () -> Unit) {
    val visible = state.payPhase == PayPhase.SANDBOX || state.payPhase == PayPhase.PROCESSING || state.payPhase == PayPhase.SUCCESS
    val gutter = dinerGutter(LocalWindowSize.current == WindowSize.COMPACT)
    AppBottomSheet(
        visible = visible,
        onDismiss = { if (state.payPhase == PayPhase.SANDBOX) state.payPhase = PayPhase.IDLE },
    ) {
        val order = state.pendingOrder
        if (order != null) {
            AnimatedContent(
                targetState = state.payPhase == PayPhase.SUCCESS,
                transitionSpec = { fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.FAST)) },
                label = "pay-phase",
            ) { approved ->
                Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = Space.x2, bottom = Space.x6)) {
                    if (approved) {
                        PaymentApproved(order)
                    } else {
                        SandboxSummary(order, processing = state.payPhase == PayPhase.PROCESSING, simulate = simulate) { state.payPhase = PayPhase.IDLE }
                    }
                }
            }
        }
    }
}

/**
 * El recibo del pedido: bloque plano con filete — número y total en mono 20/24, el método de pago y, si la
 * hay, la referencia en mono.
 */
@Composable
private fun OrderReceipt(order: Order, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val method = order.payment?.method
    val reference = order.payment?.reference
    Column(modifier.fillMaxWidth().flatSurface(AppTheme.controlFill, colors.line, AppTheme.shapes.md)) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                LabelText("Pedido")
                AppText(order.number, style = AppTheme.type.monoLg, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                LabelText("Total")
                AppText(formatMoney(order.totalCents), style = AppTheme.type.monoLg, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().edgeTop(colors.line).height(Sizes.controlLg).padding(horizontal = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (method != null) {
                DinerIcon(paymentIcon(method), tint = colors.inkSubtle)
                Spacer(Modifier.width(Space.x3))
            }
            AppText(method?.label() ?: "Mercado Pago", modifier = Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
        }
        if (!reference.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().edgeTop(colors.line).height(Sizes.controlLg).padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelText("Referencia", modifier = Modifier.weight(1f))
                AppText(reference, style = AppTheme.type.mono, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SandboxSummary(order: Order, processing: Boolean, simulate: () -> Unit, cancel: () -> Unit) {
    val colors = AppTheme.colors
    LabelText("Modo de pruebas", color = colors.ink, dot = colors.warning)
    Spacer(Modifier.height(Space.x2))
    AppText("Pago de prueba", style = AppTheme.type.h2)
    Spacer(Modifier.height(Space.x1))
    AppText(
        "No se hace ningún cargo real. Al aprobar, el pedido entra a cocina en tiempo real.",
        style = AppTheme.type.bodySm,
        color = colors.inkMuted,
    )
    Spacer(Modifier.height(Space.x5))
    OrderReceipt(order)
    Spacer(Modifier.height(Space.x5))
    AppButton("Simular pago aprobado", onClick = simulate, size = ButtonSize.LG, loading = processing, fullWidth = true)
    Spacer(Modifier.height(Space.x2))
    Box(Modifier.fillMaxWidth().height(Sizes.controlLg), contentAlignment = Alignment.Center) {
        if (processing) {
            AppText("Procesando tu pago. No cierres esta pantalla.", style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
        } else {
            AppButton("Cancelar", onClick = cancel, variant = ButtonVariant.GHOST, fullWidth = true)
        }
    }
}

/** Pago aprobado: la ficha de tinta con la palomita y el número del pedido en grande, en mono. */
@Composable
private fun PaymentApproved(order: Order) {
    val colors = AppTheme.colors
    Spacer(Modifier.height(Space.x2))
    SuccessCheck()
    Spacer(Modifier.height(Space.x5))
    LabelText("Pago aprobado · pedido", color = colors.ink, dot = colors.positive)
    AppText(order.number, style = orderNumberStyle, maxLines = 1)
    Spacer(Modifier.height(Space.x2))
    AppText("Tu pedido ya está en cocina.", style = AppTheme.type.body, color = colors.inkMuted)
    Spacer(Modifier.height(Space.x8))
}

/** Espera del pago en Mercado Pago (checkout externo): sigue el pedido hasta que quede pagado. */
@Composable
fun WaitingPaymentScreen(state: DinerState, tenant: TenantPublic) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val order = state.activeOrder
    val gutter = dinerGutter(LocalWindowSize.current == WindowSize.COMPACT)
    LaunchedEffect(order?.id) {
        val id = order?.id ?: return@LaunchedEffect
        try {
            repo.trackOrder(id).collect { updated ->
                state.recordOrder(updated)
                if (updated.status != OrderStatus.PENDING_PAYMENT) {
                    state.clearCart()
                    state.screen = DinerScreen.TRACKING
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // El seguimiento se reintenta al volver a entrar; no interrumpimos la pantalla.
        }
    }
    Column(Modifier.fillMaxSize()) {
        DinerTopBar(title = "Pago", onBack = { state.screen = DinerScreen.MENU }, subtitle = tenant.name)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = DinerPageWidth).fillMaxWidth().padding(start = gutter, end = gutter, top = Space.x6, bottom = Space.x8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DinerSpinner(diameter = Space.x4)
                    Spacer(Modifier.width(Space.x2))
                    LabelText("Esperando la confirmación", color = colors.ink)
                }
                Spacer(Modifier.height(Space.x2))
                AppText("Completa tu pago en Mercado Pago", style = AppTheme.type.h1)
                Spacer(Modifier.height(Space.x2))
                AppText(
                    "Abrimos Mercado Pago en otra ventana. En cuanto se confirme tu pago, tu pedido entra a cocina y esta pantalla avanza sola.",
                    style = AppTheme.type.body,
                    color = colors.inkMuted,
                )
                if (order != null) {
                    Spacer(Modifier.height(Space.x6))
                    OrderReceipt(order)
                }
                Spacer(Modifier.height(Space.x6))
                val url = state.checkoutUrl
                if (url != null) {
                    AppButton("Abrir Mercado Pago de nuevo", onClick = { openExternalUrl(url) }, size = ButtonSize.LG, fullWidth = true)
                    Spacer(Modifier.height(Space.x2))
                }
                AppButton("Volver al menú", onClick = { state.screen = DinerScreen.MENU }, variant = ButtonVariant.GHOST, fullWidth = true)
            }
        }
    }
}
