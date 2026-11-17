package app.feature.kitchen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderChannel
import app.shared.model.OrderLine
import app.shared.model.OrderLineModifier
import app.shared.model.OrderType
import app.shared.util.label
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

/** Medidas propias del ticket (lo demás sale de los tokens del sistema de diseño). */
private object TicketDims {
    val accent = 5.dp
    val actionMinHeight = 60.dp
    val ring = 22.dp
    val ringStroke = 3.dp
    val quantityMinWidth = 44.dp
    val metaIcon = 18.dp
    val noteIcon = 16.dp
    val enterSlide = 28.dp
    val exitSlide = 96.dp
    val glowElevation = 22.dp
}

internal val KitchenStage.actionIcon: ImageVector
    get() = when (this) {
        KitchenStage.NEW -> AppIcons.Flame
        KitchenStage.PREPARING -> AppIcons.Check
        KitchenStage.READY -> AppIcons.Bag
    }

internal fun OrderChannel.tone(): Tone = when (this) {
    OrderChannel.DIRECT -> Tone.PRIMARY
    OrderChannel.UBER_EATS -> Tone.SUCCESS
    OrderChannel.RAPPI -> Tone.WARNING
    OrderChannel.DIDI_FOOD -> Tone.INFO
}

private fun OrderType.icon(): ImageVector = when (this) {
    OrderType.DINE_IN -> AppIcons.Table
    OrderType.PICKUP -> AppIcons.Bag
    OrderType.DELIVERY -> AppIcons.Bike
}

/** Color del cronómetro según el nivel: a tiempo → atención (≥ 8 min) → retrasado (≥ 15 min). */
@Composable
internal fun elapsedColor(level: Int): Color = when (level) {
    2 -> AppTheme.colors.danger
    1 -> AppTheme.colors.warning
    else -> AppTheme.colors.success
}

@Composable
internal fun elapsedSoftColor(level: Int): Color = when (level) {
    2 -> AppTheme.colors.dangerSoft
    1 -> AppTheme.colors.warningSoft
    else -> AppTheme.colors.successSoft
}

/**
 * Ticket de cocina: número grande, canal, tipo y destinatario, líneas con cantidad en negritas,
 * modificadores y notas resaltados, cronómetro y UNA acción principal.
 */
@Composable
internal fun KitchenTicket(
    order: Order,
    stage: KitchenStage,
    board: KitchenBoardState,
    now: () -> Long,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.lg
    val leaving = board.isLeaving(order.id)
    val fresh = board.isFresh(order.id)
    val busy = board.isBusy(order.id)

    // Entrada con resorte (una sola vez por columna; no se repite al hacer scroll).
    val entranceKey = order.id + ":" + stage.name
    val appear = remember(entranceKey) { Animatable(if (board.firstAppearance(entranceKey)) 0f else 1f) }
    LaunchedEffect(entranceKey) {
        if (appear.value < 1f) appear.animateTo(1f, Motion.bouncy<Float>())
    }
    // Salida al entregar: se desliza y se desvanece antes de quitarse de la lista.
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(KitchenTiming.EXIT_MS, easing = Motion.exit),
    )
    // Brillo breve cuando el pedido acaba de entrar a cocina.
    val glow by animateFloatAsState(
        targetValue = if (fresh) 1f else 0f,
        animationSpec = tween(if (fresh) Motion.BASE else Motion.HERO, easing = Motion.standard),
    )

    val success = colors.success
    val warning = colors.warning
    val danger = colors.danger
    val since = order.kitchenSince

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val v = appear.value
                val out = exit
                alpha = v.coerceIn(0f, 1f) * (1f - out)
                val scale = (0.9f + 0.1f * v) * (1f - 0.06f * out)
                scaleX = scale
                scaleY = scale
                translationY = (1f - v) * TicketDims.enterSlide.toPx()
                translationX = out * TicketDims.exitSlide.toPx()
            }
            .shadow(
                elevation = TicketDims.glowElevation * glow,
                shape = shape,
                clip = false,
                ambientColor = colors.primary,
                spotColor = colors.primary,
            )
            .clip(shape)
            .background(lerp(colors.surface, colors.primarySoft, glow * 0.7f))
            .border(1.dp + 1.dp * glow, lerp(colors.line, colors.primary, glow), shape)
            .drawBehind {
                // Franja lateral con el color del tiempo de espera (se redibuja sin recomponer).
                val tint = when (KitchenTiming.level(((now() - since).coerceAtLeast(0L)) / 1000L)) {
                    2 -> danger
                    1 -> warning
                    else -> success
                }
                drawRect(color = tint, size = Size(TicketDims.accent.toPx(), size.height))
            }
            .padding(
                start = Space.x4 + TicketDims.accent,
                top = Space.x4,
                end = Space.x4,
                bottom = Space.x4,
            ),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            AppText(
                text = order.number,
                modifier = Modifier.weight(1f),
                style = AppTheme.type.h1.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            ElapsedPill(since = since, now = now)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            TagBadge(text = order.channel.label(), tone = order.channel.tone())
            Icon(
                imageVector = order.type.icon(),
                contentDescription = null,
                modifier = Modifier.size(TicketDims.metaIcon),
                tint = colors.inkMuted,
            )
            AppText(
                text = orderDestination(order),
                modifier = Modifier.weight(1f),
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.inkMuted,
                maxLines = 1,
            )
        }

        AppDivider()

        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            order.lines.forEach { line -> TicketLine(line) }
        }

        if (order.notes.isNotBlank()) {
            KitchenNote(text = "Nota del pedido: " + order.notes.trim(), modifier = Modifier.fillMaxWidth())
        }

        AppButton(
            text = stage.action,
            onClick = { board.advance(order) },
            modifier = Modifier.fillMaxWidth().heightIn(min = TicketDims.actionMinHeight),
            variant = ButtonVariant.PRIMARY,
            size = ButtonSize.LG,
            icon = stage.actionIcon,
            enabled = !leaving,
            loading = busy,
            fullWidth = true,
        )
    }
}

/** "En mesa · Mesa 4", "Para llevar · Fernanda", "A domicilio · Carlos M." */
private fun orderDestination(order: Order): String {
    val who = when (order.type) {
        OrderType.DINE_IN -> order.tableLabel.ifBlank { order.customerName }
        else -> order.customerName.ifBlank { order.tableLabel }
    }.trim()
    return if (who.isEmpty()) order.type.label() else order.type.label() + " · " + who
}

@Composable
private fun TicketLine(line: OrderLine) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = TicketDims.quantityMinWidth)
                .clip(AppTheme.shapes.sm)
                .background(if (line.quantity > 1) colors.primarySoft else colors.surfaceSunken)
                .padding(horizontal = Space.x2, vertical = Space.x1),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = "${line.quantity}×",
                style = AppTheme.type.h3.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(top = Space.x1),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            AppText(text = line.name, style = AppTheme.type.title)
            line.modifiers.forEach { ModifierLine(it) }
            if (line.notes.isNotBlank()) KitchenNote(text = line.notes.trim())
        }
    }
}

@Composable
private fun ModifierLine(choice: OrderLineModifier) {
    val subtle = AppTheme.colors.inkSubtle
    val strong = AppTheme.colors.ink
    val text = remember(choice, subtle, strong) {
        buildAnnotatedString {
            if (choice.groupName.isNotBlank()) {
                withStyle(SpanStyle(color = subtle)) { append(choice.groupName.trim() + ": ") }
            }
            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(choice.optionName) }
        }
    }
    BasicText(text = text, style = AppTheme.type.body)
}

/** Nota de cocina resaltada en tono de advertencia. */
@Composable
private fun KitchenNote(text: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.sm)
            .background(colors.warningSoft)
            .padding(horizontal = Space.x2, vertical = Space.x2),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            imageVector = AppIcons.Warning,
            contentDescription = null,
            modifier = Modifier.size(TicketDims.noteIcon),
            tint = colors.warning,
        )
        AppText(
            text = text,
            style = AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = colors.warning,
        )
    }
}

/**
 * Cronómetro del ticket: anillo que se llena hacia los 15 min + "mm:ss" que avanza cada segundo.
 * Verde → ámbar (≥ 8 min) → rojo (≥ 15 min, con pulso).
 */
@Composable
internal fun ElapsedPill(since: Long, now: () -> Long, modifier: Modifier = Modifier) {
    val seconds = ((now() - since).coerceAtLeast(0L)) / 1000L
    val level = KitchenTiming.level(seconds)
    val tone by animateColorAsState(elapsedColor(level), tween(Motion.SLOW))
    val soft by animateColorAsState(elapsedSoftColor(level), tween(Motion.SLOW))
    val progress by animateFloatAsState(
        targetValue = (seconds.toFloat() / KitchenTiming.DANGER_SECONDS.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(900, easing = LinearEasing),
    )
    val pulse: State<Float>? = if (level == 2) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(Motion.HERO + Motion.BASE), RepeatMode.Reverse),
        )
    } else {
        null
    }

    Row(
        modifier = modifier
            .clip(AppTheme.shapes.pill)
            .background(soft)
            .padding(start = Space.x2, top = Space.x1 + 2.dp, end = Space.x3, bottom = Space.x1 + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Canvas(Modifier.size(TicketDims.ring).graphicsLayer { alpha = pulse?.value ?: 1f }) {
            val stroke = TicketDims.ringStroke.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = tone.copy(alpha = 0.24f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = tone,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        AppText(
            text = formatKitchenElapsed(seconds),
            style = AppTheme.type.title.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = tone,
            maxLines = 1,
        )
    }
}
