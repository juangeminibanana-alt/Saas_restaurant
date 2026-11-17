package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.OrderType
import app.shared.util.relativeTime
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

private class TimelineStep(val title: String, val subtitle: String, val at: Long?)

private val NodeSize = 28.dp

/**
 * Línea de tiempo vertical del pedido: Pagado → En preparación → Listo → Entregado.
 * Pasos terminados con palomita, paso actual con halo que pulsa, conector que se llena con animación.
 * PENDING_PAYMENT, CANCELLED y REFUNDED muestran un aviso claro arriba.
 */
@Composable
fun OrderStatusTimeline(order: Order, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val readySubtitle = when (order.type) {
        OrderType.DINE_IN -> if (order.tableLabel.isNotBlank()) "Va en camino a tu mesa (${order.tableLabel})" else "Va en camino a tu mesa"
        OrderType.PICKUP -> "Ya puedes pasar a recogerlo"
        OrderType.DELIVERY -> "Tu pedido va en camino a tu domicilio"
    }
    val steps = listOf(
        TimelineStep("Pagado", "Recibimos tu pago; la cocina ya tiene tu pedido", order.paidAt),
        TimelineStep("En preparación", "Estamos preparando tu pedido", order.preparingAt),
        TimelineStep("Listo", readySubtitle, order.readyAt),
        TimelineStep("Entregado", "¡Buen provecho! Gracias por tu pedido", order.completedAt),
    )
    // Índice del paso actual; -1 = aún no entra al flujo; steps.size = todo terminado.
    val current = when (order.status) {
        OrderStatus.PENDING_PAYMENT -> -1
        OrderStatus.PAID -> 0
        OrderStatus.PREPARING -> 1
        OrderStatus.READY -> 2
        OrderStatus.COMPLETED -> steps.size
        OrderStatus.CANCELLED, OrderStatus.REFUNDED -> -1
    }

    Column(modifier.fillMaxWidth()) {
        when (order.status) {
            OrderStatus.PENDING_PAYMENT -> TimelineNotice(
                title = "Esperando tu pago",
                message = "En cuanto se confirme, tu pedido entra a cocina.",
                tone = colors.warning, soft = colors.warningSoft, pulsing = true,
            )
            OrderStatus.CANCELLED -> TimelineNotice(
                title = "Pedido cancelado",
                message = "Este pedido se canceló y no se preparará.",
                tone = colors.danger, soft = colors.dangerSoft, pulsing = false,
            )
            OrderStatus.REFUNDED -> TimelineNotice(
                title = "Pedido reembolsado",
                message = "Devolvimos el pago a tu método original. Puede tardar unos días en reflejarse.",
                tone = colors.info, soft = colors.infoSoft, pulsing = false,
            )
            else -> Unit
        }
        val closed = order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REFUNDED
        if (!closed) {
            if (order.status == OrderStatus.PENDING_PAYMENT) Spacer(Modifier.height(Space.x5))
            steps.forEachIndexed { index, step ->
                TimelineRow(
                    step = step,
                    done = index < current,
                    active = index == current,
                    isLast = index == steps.lastIndex,
                    connectorFilled = index < current,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(step: TimelineStep, done: Boolean, active: Boolean, isLast: Boolean, connectorFilled: Boolean) {
    val colors = AppTheme.colors
    val reached = done || active
    val fill by animateFloatAsState(if (connectorFilled) 1f else 0f, tween(Motion.HERO, easing = Motion.standard), label = "connector")
    val nodeColor by animateColorAsState(if (reached) colors.primary else colors.surface, tween(Motion.SLOW), label = "node")
    val ringColor by animateColorAsState(if (reached) colors.primary else colors.lineStrong, tween(Motion.SLOW), label = "ring")
    val titleColor by animateColorAsState(if (reached) colors.ink else colors.inkSubtle, tween(Motion.SLOW), label = "title")
    val checkScale by animateFloatAsState(if (done) 1f else 0f, Motion.bouncy(), label = "check")
    val trackColor = colors.line
    val fillColor = colors.primary

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (isLast) return@drawBehind
                val x = NodeSize.toPx() / 2f
                val top = NodeSize.toPx() + 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                if (bottom <= top) return@drawBehind
                val w = 2.dp.toPx()
                drawLine(trackColor, Offset(x, top), Offset(x, bottom), strokeWidth = w, cap = StrokeCap.Round)
                if (fill > 0.01f) {
                    drawLine(fillColor, Offset(x, top), Offset(x, top + (bottom - top) * fill), strokeWidth = w, cap = StrokeCap.Round)
                }
            },
    ) {
        Box(Modifier.size(NodeSize), contentAlignment = Alignment.Center) {
            if (active) PulseHalo(colors.primary)
            Box(
                Modifier.size(NodeSize).background(nodeColor, CircleShape).border(1.5.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> GlyphIcon(
                        AppIcons.Check,
                        tint = colors.onPrimary,
                        modifier = Modifier.graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                        size = 16.dp,
                    )
                    active -> Box(Modifier.size(8.dp).background(colors.onPrimary, CircleShape))
                    else -> Box(Modifier.size(6.dp).background(colors.lineStrong, CircleShape))
                }
            }
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f).padding(top = 3.dp, bottom = if (isLast) 0.dp else Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    step.title,
                    modifier = Modifier.weight(1f),
                    style = AppTheme.type.title.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    color = titleColor,
                    maxLines = 1,
                )
                if (reached && step.at != null) {
                    Spacer(Modifier.width(Space.x2))
                    AppText(relativeTime(step.at), style = AppTheme.type.label, color = colors.inkSubtle, maxLines = 1)
                }
            }
            if (reached) {
                Spacer(Modifier.height(2.dp))
                AppText(step.subtitle, style = AppTheme.type.bodySm, color = if (active) colors.inkMuted else colors.inkSubtle)
            }
        }
    }
}

/** Halo que crece y se desvanece alrededor del paso actual. */
@Composable
private fun PulseHalo(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "halo",
    )
    Box(
        Modifier
            .size(NodeSize)
            .graphicsLayer {
                val eased = 1f - (1f - t) * (1f - t)
                val s = 1f + 0.95f * eased
                scaleX = s
                scaleY = s
                alpha = 0.34f * (1f - eased)
            }
            .background(color, CircleShape),
    )
}

@Composable
private fun TimelineNotice(title: String, message: String, tone: Color, soft: Color, pulsing: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(soft, AppTheme.shapes.md).padding(Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(NodeSize), contentAlignment = Alignment.Center) {
            if (pulsing) PulseHalo(tone)
            Box(Modifier.size(12.dp).background(tone, CircleShape))
        }
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f)) {
            AppText(title, style = AppTheme.type.title, color = tone)
            Spacer(Modifier.height(2.dp))
            AppText(message, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
        }
    }
}
