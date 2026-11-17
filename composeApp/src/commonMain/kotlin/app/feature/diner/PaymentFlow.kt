package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

/** Pago de prueba (sandbox): resumen → procesando → aprobado. */
@Composable
fun SandboxPaymentSheet(state: DinerState, viewportHeight: Dp, simulate: () -> Unit) {
    val visible = state.payPhase == PayPhase.SANDBOX || state.payPhase == PayPhase.PROCESSING || state.payPhase == PayPhase.SUCCESS
    AppBottomSheet(
        visible = visible,
        onDismiss = { if (state.payPhase == PayPhase.SANDBOX) state.payPhase = PayPhase.IDLE },
    ) {
        val order = state.pendingOrder
        if (order != null) {
            AnimatedContent(
                targetState = state.payPhase,
                transitionSpec = { fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.FAST)) },
                label = "pay-phase",
            ) { phase ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Space.x6, vertical = Space.x6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (phase) {
                        PayPhase.PROCESSING -> {
                            Spacer(Modifier.height(Space.x6))
                            DinerSpinner()
                            Spacer(Modifier.height(Space.x5))
                            AppText("Procesando tu pago…", style = AppTheme.type.h3)
                            Spacer(Modifier.height(Space.x1))
                            AppText("No cierres esta pantalla.", color = AppTheme.colors.inkMuted)
                            Spacer(Modifier.height(Space.x8))
                        }
                        PayPhase.SUCCESS -> {
                            Spacer(Modifier.height(Space.x4))
                            SuccessCheck()
                            Spacer(Modifier.height(Space.x5))
                            AppText("¡Pago aprobado!", style = AppTheme.type.h2)
                            Spacer(Modifier.height(Space.x1))
                            AppText("Tu pedido ${order.number} ya está en cocina.", color = AppTheme.colors.inkMuted, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(Space.x8))
                        }
                        else -> SandboxSummary(order, simulate) { state.payPhase = PayPhase.IDLE }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxSummary(order: Order, simulate: () -> Unit, cancel: () -> Unit) {
    val colors = AppTheme.colors
    val method = order.payment?.method
    StatusPill("Modo de pruebas (sandbox)", tone = Tone.WARNING)
    Spacer(Modifier.height(Space.x3))
    AppText("Pago de prueba · Mercado Pago", style = AppTheme.type.h2, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Space.x2))
    AppText(
        "No se hace ningún cargo real. Al aprobar, el pedido entra a cocina en tiempo real.",
        color = colors.inkMuted, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Space.x5))
    Column(
        Modifier.fillMaxWidth().clip(AppTheme.shapes.md).background(colors.surfaceSunken)
            .border(1.dp, colors.line, AppTheme.shapes.md).padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (method != null) {
                DinerIcon(paymentIcon(method), tint = colors.ink, size = 22.dp)
                Spacer(Modifier.width(Space.x3))
            }
            Column(Modifier.weight(1f)) {
                AppText(method?.label() ?: "Mercado Pago", style = AppTheme.type.title)
                AppText("Pedido ${order.number}", style = AppTheme.type.bodySm, color = colors.inkMuted)
            }
            AppText(formatMoney(order.totalCents), style = AppTheme.type.h3)
        }
        val reference = order.payment?.reference
        if (!reference.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("Referencia", Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.inkMuted)
                AppText(reference, style = AppTheme.type.title)
            }
        }
    }
    Spacer(Modifier.height(Space.x5))
    AppButton("Simular pago aprobado", onClick = simulate, size = ButtonSize.LG, fullWidth = true)
    Spacer(Modifier.height(Space.x2))
    AppButton("Cancelar", onClick = cancel, variant = ButtonVariant.GHOST, fullWidth = true)
}

/** Espera del pago en Mercado Pago (checkout externo): sigue el pedido hasta que quede pagado. */
@Composable
fun WaitingPaymentScreen(state: DinerState, tenant: TenantPublic) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val order = state.activeOrder
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
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(Space.x6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DinerSpinner()
                Spacer(Modifier.height(Space.x6))
                AppText("Completa tu pago en Mercado Pago", style = AppTheme.type.h2, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.x2))
                AppText(
                    "Abrimos Mercado Pago en otra ventana. En cuanto se confirme tu pago, tu pedido entra a cocina y esta pantalla avanza sola.",
                    color = colors.inkMuted, textAlign = TextAlign.Center,
                )
                if (order != null) {
                    Spacer(Modifier.height(Space.x5))
                    AppText("Pedido ${order.number} · ${formatMoney(order.totalCents)}", style = AppTheme.type.title)
                }
                Spacer(Modifier.height(Space.x6))
                val url = state.checkoutUrl
                if (url != null) {
                    AppButton("Abrir Mercado Pago de nuevo", onClick = { openExternalUrl(url) }, size = ButtonSize.LG, fullWidth = true)
                    Spacer(Modifier.height(Space.x2))
                }
                AppButton("Volver al menú", onClick = { state.screen = DinerScreen.MENU }, variant = ButtonVariant.GHOST, fullWidth = true)
                Spacer(Modifier.size(Space.x6))
            }
        }
    }
}
