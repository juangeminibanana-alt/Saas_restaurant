package app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion

/**
 * Click con respuesta táctil: escala con resorte al presionar, sin ripple, con rol de botón.
 * El área táctil se amplía sola al mínimo accesible aunque el elemento sea más pequeño.
 */
fun Modifier.pressable(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, Motion.snappy())
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

/** Brillo que recorre el elemento mientras carga. Aplícalo después de `clip(shape)`. */
fun Modifier.shimmer(): Modifier = composed {
    val colors = AppTheme.colors
    val base = if (colors.isDark) colors.surfaceRaised else Color(0xFFECEAE4)
    val highlight = if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.75f)
    val transition = rememberInfiniteTransition()
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Restart),
    )
    drawBehind {
        drawRect(base)
        val band = maxOf(size.width * 0.55f, 96.dp.toPx())
        val x = -band + (size.width + band * 2f) * progress.value
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - band, 0f),
                end = Offset(x, size.height * 0.35f),
            ),
        )
    }
}

/**
 * Sombra suave y ancha (capas translúcidas concéntricas) para el modo claro.
 * En oscuro no dibuja nada: ahí la elevación se expresa con superficies más claras y bordes finos.
 */
internal fun Modifier.softShadow(
    shape: Shape,
    color: Color,
    blur: Dp = 28.dp,
    offsetY: Dp = 10.dp,
    alpha: Float = 0.10f,
): Modifier = if (alpha <= 0f) this else drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val radius = when (outline) {
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
        else -> 0f
    }
    val steps = 10
    val blurPx = blur.toPx()
    val dy = offsetY.toPx()
    val layerAlpha = alpha / steps * 1.35f
    for (i in 0 until steps) {
        val t = (i + 1) / steps.toFloat()
        val spread = blurPx * 0.5f * t - blurPx * 0.12f
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(-spread, -spread + dy * (0.4f + 0.6f * t)),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius((radius + spread).coerceAtLeast(0f)),
        )
    }
}
