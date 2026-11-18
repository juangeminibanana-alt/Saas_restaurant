package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.OrderType
import app.shared.util.relativeTime
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

private class TimelineStep(val title: String, val subtitle: String, val at: Long?)

private val NodeSize = 24.dp

/**
 * Línea de tiempo vertical del pedido: Pagado → En preparación → Listo → Entregado.
 * Lo ya ocurrido va en tinta sólida (nodo con palomita y conector lleno); el paso actual es el único nodo
 * en brasa sólida ("tu pedido"), sin halo ni pulso; lo que falta es sólo un filete.
 * PENDING_PAYMENT, CANCELLED y REFUNDED muestran un aviso plano arriba.
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
                tone = colors.warning,
            )
            OrderStatus.CANCELLED -> TimelineNotice(
                title = "Pedido cancelado",
                message = "Este pedido se canceló y no se preparará.",
                tone = colors.danger,
            )
            OrderStatus.REFUNDED -> TimelineNotice(
                title = "Pedido reembolsado",
                message = "Devolvimos el pago a tu método original. Puede tardar unos días en reflejarse.",
                tone = colors.info,
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
    val fill by animateFloatAsState(if (connectorFilled) 1f else 0f, tween(Motion.SLOW, easing = Motion.standard), label = "connector")
    val nodeColor by animateColorAsState(
        when {
            active -> colors.primary
            done -> colors.select
            else -> colors.select.copy(alpha = 0f)
        },
        tween(Motion.BASE),
        label = "node",
    )
    val ringColor by animateColorAsState(if (reached) colors.lineStrong.copy(alpha = 0f) else colors.lineStrong, tween(Motion.BASE), label = "ring")
    val titleColor by animateColorAsState(if (reached) colors.ink else colors.inkSubtle, tween(Motion.BASE), label = "title")
    val trackColor = colors.line
    val fillColor = colors.select

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (isLast) return@drawBehind
                val x = NodeSize.toPx() / 2f
                val top = NodeSize.toPx() + 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                if (bottom <= top) return@drawBehind
                val w = 1.dp.toPx()
                drawLine(trackColor, Offset(x, top), Offset(x, bottom), strokeWidth = w)
                if (fill > 0.01f) {
                    drawLine(fillColor, Offset(x, top), Offset(x, top + (bottom - top) * fill), strokeWidth = w)
                }
            },
    ) {
        Box(
            Modifier.size(NodeSize).background(nodeColor, CircleShape).border(1.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> GlyphIcon(AppIcons.Check, tint = colors.onSelect, size = 16.dp)
                active -> Box(Modifier.size(8.dp).background(colors.onPrimary, CircleShape))
                else -> Unit
            }
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = if (isLast) 0.dp else Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    step.title,
                    modifier = Modifier.weight(1f),
                    style = if (active) AppTheme.type.h3 else AppTheme.type.title,
                    color = titleColor,
                    maxLines = 1,
                )
                if (reached && step.at != null) {
                    Spacer(Modifier.width(Space.x2))
                    AppText(relativeTime(step.at), style = AppTheme.type.mono, color = colors.inkSubtle, maxLines = 1)
                }
            }
            if (reached) {
                AppText(step.subtitle, style = AppTheme.type.bodySm, color = colors.inkMuted)
            }
        }
    }
}

/** Aviso plano: superficie + filete, punto sólido del tono, título en tinta. */
@Composable
private fun TimelineNotice(title: String, message: String, tone: Color) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    Row(
        Modifier.fillMaxWidth().background(AppTheme.controlFill, shape).border(1.dp, colors.line, shape).padding(Space.x4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = 6.dp, height = 20.dp), contentAlignment = Alignment.Center) { StatusDot(tone) }
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f)) {
            AppText(title, style = AppTheme.type.h3)
            AppText(message, style = AppTheme.type.bodySm, color = colors.inkMuted)
        }
    }
}
