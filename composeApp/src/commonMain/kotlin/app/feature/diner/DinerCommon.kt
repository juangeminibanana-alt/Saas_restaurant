package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.model.OrderType
import app.shared.model.PaymentMethod
import app.shared.util.formatMoney
import app.shared.util.minutesSince
import app.shared.util.nowMillis
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Velo sobre fotografías: independiente del tema para que el texto blanco siempre contraste. */
val PhotoScrim: Color = Color.Black
val OnPhoto: Color = Color.White
val TOP_BAR_HEIGHT: Dp = 56.dp

@Composable
fun DinerIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.ink, size: Dp = 20.dp) {
    Image(imageVector = icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Barra superior de las pantallas secundarias (carrito, seguimiento, mis pedidos). */
@Composable
fun DinerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.canvas)) {
        Row(
            Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).padding(horizontal = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcons.Back, onClick = onBack, contentDescription = "Volver")
            Spacer(Modifier.width(Space.x2))
            Column(Modifier.weight(1f)) {
                AppText(title, style = AppTheme.type.h3, maxLines = 1)
                if (!subtitle.isNullOrBlank()) AppText(subtitle, style = AppTheme.type.label, color = AppTheme.colors.inkMuted, maxLines = 1)
            }
            actions()
            Spacer(Modifier.width(Space.x2))
        }
        AppDivider()
    }
}

/** Importe que "rueda" hacia arriba o abajo cuando cambia. */
@Composable
fun AnimatedMoney(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.type.title,
    color: Color = AppTheme.colors.ink,
) {
    AnimatedContent(
        targetState = cents,
        modifier = modifier,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { h -> if (up) h else -h } + fadeIn(tween(Motion.BASE)))
                .togetherWith(slideOutVertically(tween(Motion.FAST, easing = Motion.exit)) { h -> if (up) -h else h } + fadeOut(tween(Motion.FAST)))
                .using(SizeTransform(clip = false))
        },
        label = "money",
    ) { value ->
        AppText(formatMoney(value), style = style, color = color, maxLines = 1)
    }
}

/** Botón primario de ancho completo con importe animado: "Agregar · $185". */
@Composable
fun PriceButton(label: String, cents: Long, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AppTheme.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(AppTheme.shapes.control)
            .background(colors.primary)
            .pressable(enabled = enabled) { onClick() }
            .padding(horizontal = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AppText(label, style = AppTheme.type.title, color = colors.onPrimary, maxLines = 1)
        AppText("  ·  ", style = AppTheme.type.title, color = colors.onPrimary.copy(alpha = 0.6f), maxLines = 1)
        AnimatedMoney(cents, style = AppTheme.type.title, color = colors.onPrimary)
    }
}

/** Entrada escalonada (fade + slide) solo durante la primera carga del menú. */
fun Modifier.staggerIn(order: Int, loadedAt: Long): Modifier = composed {
    val animate = remember { nowMillis() - loadedAt < 1_400L }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(order.coerceIn(0, 12) * 55L)
            progress.animateTo(1f, tween(Motion.HERO, easing = Motion.standard))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/** Radio (round) o casilla con transición de color. */
@Composable
fun SelectionMark(checked: Boolean, round: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val fill by animateColorAsState(if (checked) colors.primary else Color.Transparent, tween(Motion.FAST), label = "mark-fill")
    val stroke by animateColorAsState(if (checked) colors.primary else colors.lineStrong, tween(Motion.FAST), label = "mark-stroke")
    val shape = if (round) CircleShape else AppTheme.shapes.xs
    Box(
        modifier.size(22.dp).clip(shape).background(fill).border(1.5.dp, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            if (round) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.onPrimary))
            else DinerIcon(AppIcons.Check, tint = colors.onPrimary, size = 14.dp)
        }
    }
}

@Composable
fun DinerSpinner(modifier: Modifier = Modifier, diameter: Dp = 56.dp, color: Color = AppTheme.colors.primary) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinner-angle",
    )
    val track = AppTheme.colors.line
    Canvas(modifier.size(diameter)) {
        val stroke = this.size.minDimension * 0.09f
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        drawArc(color, angle - 90f, 96f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Círculo que rebota y palomita que se dibuja: confirmación de pago. */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier, diameter: Dp = 96.dp) {
    val colors = AppTheme.colors
    val scale = remember { Animatable(0.4f) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, Motion.bouncy()) }
        delay(140)
        progress.animateTo(1f, tween(Motion.HERO, easing = Motion.standard))
    }
    Box(
        modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(colors.successSoft),
        contentAlignment = Alignment.Center,
    ) {
        val ink = colors.success
        Canvas(Modifier.size(diameter * 0.46f)) {
            val p = progress.value
            val a = Offset(this.size.width * 0.10f, this.size.height * 0.54f)
            val b = Offset(this.size.width * 0.40f, this.size.height * 0.82f)
            val c = Offset(this.size.width * 0.92f, this.size.height * 0.22f)
            val stroke = 4.dp.toPx()
            val first = (p / 0.4f).coerceIn(0f, 1f)
            val second = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (first > 0f) drawLine(ink, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first), stroke, StrokeCap.Round)
            if (second > 0f) drawLine(ink, b, Offset(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun TotalsRow(label: String, cents: Long, modifier: Modifier = Modifier, strong: Boolean = false) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        AppText(
            label,
            modifier = Modifier.weight(1f),
            style = if (strong) AppTheme.type.h3 else AppTheme.type.body,
            color = if (strong) colors.ink else colors.inkMuted,
        )
        AnimatedMoney(cents, style = if (strong) AppTheme.type.h3 else AppTheme.type.body, color = colors.ink)
    }
}

@Composable
fun InfoLine(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.inkMuted) {
    Row(modifier.padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        DinerIcon(icon, tint = tint, size = 18.dp)
        Spacer(Modifier.width(Space.x2))
        AppText(text, style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
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

fun tagTone(tag: String): Tone = when (tag.lowercase()) {
    "popular" -> Tone.PRIMARY
    "nuevo" -> Tone.INFO
    "picante" -> Tone.DANGER
    "vegetariano", "vegano" -> Tone.SUCCESS
    else -> Tone.NEUTRAL
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
