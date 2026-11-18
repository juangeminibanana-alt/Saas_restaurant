package app.feature.kitchen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
import app.ui.components.GlyphIcon
import app.ui.components.LabelText
import app.ui.components.StatusDot
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.Motion
import app.ui.theme.Space

/** Medidas propias del ticket (lo demás sale de los tokens del sistema de diseño). */
private object TicketDims {
    val quantity = Space.x8
    val quantityHeight = Space.x6
    val chip = Space.x6
    val metaIcon = 16.dp
    val enterSlide = Space.x2
    val exitSlide = Space.x18
    /** Ancho de contenido desde el que la ficha "NUEVO" cabe junto al número (teléfono / pared). */
    val roomy = 260.dp
    val roomyLarge = 330.dp
}

internal val KitchenStage.actionIcon: ImageVector
    get() = when (this) {
        KitchenStage.NEW -> AppIcons.Flame
        KitchenStage.PREPARING -> AppIcons.Check
        KitchenStage.READY -> AppIcons.Bag
    }

/** El canal es un dato, no una alerta: venta directa en tinta sólida, marketplaces en ficha neutra. */
internal fun OrderChannel.tone(): Tone = when (this) {
    OrderChannel.DIRECT -> Tone.PRIMARY
    else -> Tone.NEUTRAL
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
    else -> AppTheme.colors.positive
}

/**
 * Ticket de cocina en NOCHE: mosaico plano (superficie + filete). Arriba, el número de pedido en mono enorme y,
 * a la derecha, el cronómetro en mono: es lo único que cambia de color con la urgencia. Los pedidos que nadie
 * ha aceptado llevan la ficha sólida "NUEVO". Abajo, UNA acción en brasa.
 * [large] = pantalla de pared: número 40/44, cronómetro 32/36 y platillos a 20/24.
 */
@Composable
internal fun KitchenTicket(
    order: Order,
    stage: KitchenStage,
    board: KitchenBoardState,
    now: () -> Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val leaving = board.isLeaving(order.id)
    val busy = board.isBusy(order.id)

    // Entrada corta y sin rebote (una sola vez por columna; no se repite al hacer scroll).
    val entranceKey = order.id + ":" + stage.name
    val appear = remember(entranceKey) { Animatable(if (board.firstAppearance(entranceKey)) 0f else 1f) }
    LaunchedEffect(entranceKey) {
        if (appear.value < 1f) appear.animateTo(1f, tween(Motion.BASE, easing = Motion.standard))
    }
    // Salida al entregar: se desliza y se desvanece antes de quitarse de la lista.
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(KitchenTiming.EXIT_MS, easing = Motion.exit),
    )

    val numberStyle = (if (large) type.display else type.h1).copy(
        fontFamily = type.mono.fontFamily, fontWeight = FontWeight.SemiBold, letterSpacing = type.monoLg.letterSpacing,
    )

    CompositionLocalProvider(LocalSurfaceLevel provides 1) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val v = appear.value
                    alpha = v.coerceIn(0f, 1f) * (1f - exit)
                    translationY = (1f - v) * TicketDims.enterSlide.toPx()
                    translationX = exit * TicketDims.exitSlide.toPx()
                }
                .flatSurface(colors.surface, colors.line, AppTheme.shapes.lg)
                .padding(Space.x4),
        ) {
          BoxWithConstraints(Modifier.fillMaxWidth()) {
            // En columnas angostas (tableta) la ficha "NUEVO" baja al renglón del canal: el número y el
            // cronómetro nunca se recortan.
            val chipBesideNumber = maxWidth >= if (large) TicketDims.roomyLarge else TicketDims.roomy
            val isNew = stage == KitchenStage.NEW
          Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                AppText(text = order.number, style = numberStyle, maxLines = 1)
                if (isNew && chipBesideNumber) NewChip()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    ElapsedClock(since = order.kitchenSince, now = now, style = boardNumeral(compact = !large))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                if (isNew && !chipBesideNumber) NewChip()
                TagBadge(text = order.channel.label(), tone = order.channel.tone())
                GlyphIcon(order.type.icon(), tint = colors.inkSubtle, size = TicketDims.metaIcon)
                AppText(
                    text = orderDestination(order),
                    modifier = Modifier.weight(1f),
                    style = if (large) type.titleSm else type.bodySm,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
            }

            AppDivider()

            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                order.lines.forEach { line -> TicketLine(line, large) }
            }

            if (order.notes.isNotBlank()) {
                KitchenNote(text = "Nota del pedido: " + order.notes.trim(), modifier = Modifier.fillMaxWidth())
            }

            AppButton(
                text = stage.action,
                onClick = { board.advance(order) },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.LG,
                icon = stage.actionIcon,
                enabled = !leaving,
                loading = busy,
                fullWidth = true,
            )
          }
          }
        }
    }
}

/** Ficha sólida de brasa: el pedido es nuevo y espera a que alguien lo acepte. */
@Composable
private fun NewChip() {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier.height(TicketDims.chip).background(colors.primary, AppTheme.shapes.xs).padding(horizontal = Space.x2),
        contentAlignment = Alignment.Center,
    ) {
        LabelText(text = "Nuevo", color = colors.onPrimary)
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
private fun TicketLine(line: OrderLine, large: Boolean) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val several = line.quantity > 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        // La cantidad en mono; más de una pieza se marca con tinta sólida para que no se escape.
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = TicketDims.quantity)
                .height(TicketDims.quantityHeight)
                .background(if (several) colors.select else colors.surfaceRaised, AppTheme.shapes.xs)
                .padding(horizontal = Space.x1),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = line.quantity.toString(),
                style = type.monoMd.copy(fontWeight = FontWeight.SemiBold),
                color = if (several) colors.onSelect else colors.inkMuted,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            AppText(
                text = line.name,
                modifier = Modifier.padding(top = if (large) 0.dp else 2.dp),
                style = if (large) type.h2.copy(fontWeight = FontWeight.Medium) else type.title,
            )
            line.modifiers.forEach { ModifierLine(it, if (large) type.body else type.bodySm) }
            if (line.notes.isNotBlank()) KitchenNote(text = line.notes.trim())
        }
    }
}

@Composable
private fun ModifierLine(choice: OrderLineModifier, style: TextStyle) {
    val muted = AppTheme.colors.inkMuted
    val strong = AppTheme.colors.ink
    val text = remember(choice, muted, strong) {
        buildAnnotatedString {
            if (choice.groupName.isNotBlank()) {
                withStyle(SpanStyle(color = muted)) { append(choice.groupName.trim() + ": ") }
            }
            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Medium)) { append(choice.optionName) }
        }
    }
    BasicText(text = text, style = style)
}

/** Nota de cocina: ficha plana con un punto ámbar; el texto va en tinta para leerse de lejos. */
@Composable
private fun KitchenNote(text: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .flatSurface(colors.surfaceRaised, colors.line, AppTheme.shapes.sm)
            .padding(horizontal = Space.x2, vertical = Space.x1 + 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        StatusDot(colors.warning, Modifier.padding(top = Space.x2 - 1.dp))
        AppText(text = text, style = AppTheme.type.bodySm.copy(fontWeight = FontWeight.Medium))
    }
}

/**
 * Cronómetro del ticket: "mm:ss" en mono que avanza cada segundo. Es lo único que toma color con la urgencia:
 * verde → ámbar (≥ 8 min) → rojo (≥ 15 min). Sin anillo, sin fondo, sin pulso.
 */
@Composable
internal fun ElapsedClock(since: Long, now: () -> Long, style: TextStyle, modifier: Modifier = Modifier) {
    val seconds = ((now() - since).coerceAtLeast(0L)) / 1000L
    val tone by animateColorAsState(elapsedColor(KitchenTiming.level(seconds)), tween(Motion.SLOW))
    AppText(text = formatKitchenClock(seconds), modifier = modifier, style = style, color = tone, maxLines = 1)
}
