package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.OrderType
import app.shared.model.PaymentMethod
import app.shared.util.formatMoney
import app.shared.util.minutesSince
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.LabelText
import app.ui.components.StatusDot
import app.ui.components.Tone
import app.ui.components.edgeBottom
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space

/** Ancho máximo del contenido de las pantallas secundarias (pedido, seguimiento, historial). */
val DinerPageWidth: Dp = 640.dp

/** Margen lateral del menú: 16 en teléfono, 24 en pantallas anchas. */
internal fun dinerGutter(compact: Boolean): Dp = if (compact) Space.gutter else Space.x6

@Composable
fun DinerIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.ink, size: Dp = Sizes.icon) {
    Image(imageVector = icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/**
 * Barra superior de las pantallas secundarias (pedido, pago, seguimiento, mis pedidos): la misma fila de 72
 * que la navegación pegada del menú — botón de 44 para volver, antetítulo en mono y título 20/24 — con un
 * filete abajo. El contenido se centra al mismo ancho que la página.
 */
@Composable
fun DinerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxWidth: Dp = DinerPageWidth,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().background(colors.canvas).edgeBottom(colors.line), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .height(Sizes.bar)
                .padding(start = Space.gutter, end = Space.gutter, top = Space.x4, bottom = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcons.Back, onClick = onBack, contentDescription = "Volver")
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                if (!subtitle.isNullOrBlank()) LabelText(subtitle)
                AppText(title, style = AppTheme.type.h2, maxLines = 1)
            }
            actions()
        }
    }
}

/** Importe en mono que cambia con un desplazamiento corto, sin rebote. */
@Composable
fun AnimatedMoney(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.type.mono,
    color: Color = AppTheme.colors.ink,
) {
    AnimatedContent(
        targetState = cents,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(Motion.FAST, easing = Motion.standard)) { h -> dir * h / 3 } + fadeIn(tween(Motion.FAST)))
                .togetherWith(slideOutVertically(tween(Motion.FAST, easing = Motion.exit)) { h -> -dir * h / 3 } + fadeOut(tween(Motion.FAST)))
                .using(SizeTransform(clip = false))
        },
        label = "money",
    ) { value ->
        AppText(formatMoney(value), style = style, color = color, maxLines = 1)
    }
}

/**
 * Sube el contenido [by] y le resta esa altura (el `margin-top` negativo de la maqueta): la altura de
 * mayúsculas del nombre coincide con el borde superior de la miniatura sin empujar lo que sigue.
 */
fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/**
 * Encabezado de sección del menú: título 20/24 y, a la derecha, una etiqueta en mono sobre la misma línea base.
 * Con las compensaciones ópticas de la maqueta: el título entra 1 px en el margen y el tracking final de la
 * etiqueta no cuenta, para que la tinta de ambos llegue exacta a los márgenes.
 */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.x3)) {
        AppText(title, modifier = Modifier.weight(1f).alignByBaseline().offset(x = -Sizes.hairline), style = AppTheme.type.h2, maxLines = 2)
        if (!label.isNullOrBlank()) {
            LabelText(label, modifier = Modifier.padding(start = Space.x4).alignByBaseline().offset(x = Sizes.hairline))
        }
    }
}

/**
 * Cabecera de grupo: título 16/20 seminegrita y, a la derecha, su regla en mono ("ELIGE 1", "OPCIONAL").
 * [pending]: falta una elección obligatoria — la etiqueta va en tinta con un punto sólido de brasa.
 */
@Composable
fun GroupHead(title: String, modifier: Modifier = Modifier, label: String? = null, pending: Boolean = false) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(bottom = Space.x3)) {
        AppText(title, modifier = Modifier.weight(1f).alignByBaseline(), style = AppTheme.type.h3, maxLines = 2)
        if (!label.isNullOrBlank()) {
            LabelText(
                label,
                modifier = Modifier.padding(start = Space.x4).alignByBaseline().offset(x = Sizes.hairline),
                color = if (pending) colors.ink else colors.inkSubtle,
                dot = if (pending) colors.primary else null,
            )
        }
    }
}

/** Renglón de totales: concepto en texto chico e importe en mono; [strong] es el total (16/20 + mono 20/24). */
@Composable
fun TotalsRow(label: String, cents: Long, modifier: Modifier = Modifier, strong: Boolean = false) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        AppText(
            label,
            modifier = Modifier.weight(1f),
            style = if (strong) AppTheme.type.h3 else AppTheme.type.bodySm,
            color = if (strong) colors.ink else colors.inkMuted,
            maxLines = 1,
        )
        AnimatedMoney(cents, style = if (strong) AppTheme.type.monoLg else AppTheme.type.mono)
    }
}

@Composable
fun InfoLine(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.inkSubtle) {
    Row(modifier.padding(vertical = Space.x1), verticalAlignment = Alignment.Top) {
        DinerIcon(icon, tint = tint)
        Spacer(Modifier.width(Space.x3))
        AppText(text, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
    }
}

/** Aviso plano: superficie + filete, punto sólido del tono y texto chico. Sin tinte de fondo. */
@Composable
fun DinerNotice(text: String, tone: Color, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().flatSurface(AppTheme.controlFill, colors.line, AppTheme.shapes.md).padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.height(Space.x5), contentAlignment = Alignment.Center) { StatusDot(tone) }
        Spacer(Modifier.width(Space.x3))
        AppText(text, modifier = Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.ink)
    }
}

/** Número de pedido en grande: la escala del nombre del restaurante (40/44) en Geist Mono. */
val orderNumberStyle: TextStyle
    @Composable @ReadOnlyComposable get() {
        val type = AppTheme.type
        return type.display.copy(fontFamily = type.monoLg.fontFamily, fontWeight = FontWeight.Medium, letterSpacing = type.monoLg.letterSpacing)
    }

/** Espera: un arco fino de tinta que gira. Sin halo. */
@Composable
fun DinerSpinner(modifier: Modifier = Modifier, diameter: Dp = Sizes.icon, color: Color = AppTheme.colors.ink) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(820, easing = LinearEasing)),
        label = "spinner-angle",
    )
    val track = AppTheme.colors.lineStrong
    Canvas(modifier.size(diameter)) {
        val stroke = 1.5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        drawArc(color, angle - 90f, 96f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Confirmación: ficha de tinta sólida (como un paso hecho de la línea de tiempo) con la palomita que se traza. */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier, size: Dp = Sizes.control) {
    val colors = AppTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(Motion.SLOW, easing = Motion.standard)) }
    val ink = colors.onSelect
    Box(modifier.size(size).background(colors.select, AppTheme.shapes.md), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Sizes.icon)) {
            val p = progress.value
            val a = Offset(this.size.width * 0.20f, this.size.height * 0.52f)
            val b = Offset(this.size.width * 0.42f, this.size.height * 0.74f)
            val c = Offset(this.size.width * 0.82f, this.size.height * 0.28f)
            val stroke = 1.5.dp.toPx()
            val first = (p / 0.4f).coerceIn(0f, 1f)
            val second = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (first > 0f) drawLine(ink, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first), stroke, StrokeCap.Round)
            if (second > 0f) drawLine(ink, b, Offset(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second), stroke, StrokeCap.Round)
        }
    }
}

// ───────────────────────── Textos y tonos ─────────────────────────

fun tagLabel(tag: String): String = when (tag.lowercase()) {
    "popular" -> "Popular"
    "nuevo" -> "Nuevo"
    "picante" -> "Picante"
    "vegetariano" -> "Vegetariano"
    "vegano" -> "Vegano"
    "sin gluten" -> "Sin gluten"
    else -> tag.replaceFirstChar { it.uppercase() }
}

fun OrderStatus.dinerLabel(): String = when (this) {
    OrderStatus.PENDING_PAYMENT -> "Pago pendiente"
    OrderStatus.PAID -> "Recibido"
    OrderStatus.PREPARING -> "En preparación"
    OrderStatus.READY -> "Listo"
    OrderStatus.COMPLETED -> "Entregado"
    OrderStatus.CANCELLED -> "Cancelado"
    OrderStatus.REFUNDED -> "Reembolsado"
}

fun OrderStatus.tone(): Tone = when (this) {
    OrderStatus.PENDING_PAYMENT -> Tone.WARNING
    OrderStatus.PAID -> Tone.INFO
    OrderStatus.PREPARING -> Tone.PRIMARY
    OrderStatus.READY -> Tone.SUCCESS
    OrderStatus.COMPLETED -> Tone.NEUTRAL
    OrderStatus.CANCELLED, OrderStatus.REFUNDED -> Tone.DANGER
}

fun etaText(order: Order): String = when (order.status) {
    OrderStatus.PENDING_PAYMENT -> "Estamos esperando la confirmación de tu pago."
    OrderStatus.PAID -> "Recibimos tu pedido. Estará listo en aprox. ${order.etaMinutes} min."
    OrderStatus.PREPARING -> {
        val elapsed = minutesSince(order.paidAt ?: order.createdAt)
        val remaining = (order.etaMinutes - elapsed).coerceAtLeast(2)
        "Lo estamos preparando. Listo en aprox. $remaining min."
    }
    OrderStatus.READY -> when (order.type) {
        OrderType.DINE_IN -> "Ya va en camino a tu mesa."
        OrderType.PICKUP -> "Pasa a recogerlo al mostrador."
        OrderType.DELIVERY -> "Tu repartidor ya va en camino."
    }
    OrderStatus.COMPLETED -> "Pedido entregado. ¡Buen provecho!"
    OrderStatus.CANCELLED -> "Este pedido fue cancelado."
    OrderStatus.REFUNDED -> "Este pedido fue reembolsado."
}

fun paymentIcon(method: PaymentMethod): ImageVector = when (method) {
    PaymentMethod.CARD -> AppIcons.CreditCard
    PaymentMethod.OXXO -> AppIcons.Cash
    PaymentMethod.SPEI -> AppIcons.Bank
    PaymentMethod.MP_WALLET -> AppIcons.Wallet
    PaymentMethod.PAYMENT_LINK -> AppIcons.Link
}

fun paymentHint(method: PaymentMethod): String = when (method) {
    PaymentMethod.CARD -> "Visa, Mastercard o Amex. Aprobación inmediata."
    PaymentMethod.OXXO -> "Paga en efectivo en cualquier tienda con tu referencia."
    PaymentMethod.SPEI -> "Transfiere desde tu banca en línea con la CLABE."
    PaymentMethod.MP_WALLET -> "Usa el dinero disponible en tu cuenta."
    PaymentMethod.PAYMENT_LINK -> "Te enviamos un link para pagar."
}

/** "3 platillos" / "1 platillo". */
fun dishCount(count: Int): String = if (count == 1) "1 platillo" else "$count platillos"
