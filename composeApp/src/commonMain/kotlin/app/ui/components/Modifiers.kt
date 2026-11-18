package app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * En pantallas táctiles el área de toque se amplía sola al mínimo accesible aunque la cara sea más chica.
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

// ───────────────────────── Filetes: la única "profundidad" de NOCHE ─────────────────────────

/** Filete interior de 1 px que sigue la forma (equivale a `box-shadow: inset 0 0 0 1px`). */
fun Modifier.ring(color: Color, shape: Shape, width: Dp = 1.dp): Modifier = border(width, color, shape)

/** Filete de 1 px en el borde superior, por dentro del elemento. */
fun Modifier.edgeTop(color: Color, width: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    drawRect(color, topLeft = Offset.Zero, size = Size(size.width, width.toPx()))
}

/** Filete de 1 px en el borde inferior, por dentro del elemento. */
fun Modifier.edgeBottom(color: Color, width: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    val h = width.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
}

/** Superficie plana de NOCHE: recorte + relleno + filete interior. Sin sombra. */
fun Modifier.flatSurface(fill: Color, line: Color, shape: Shape): Modifier =
    clip(shape).background(fill).border(1.dp, line, shape)

/**
 * Velo de NOCHE: el único degradado del sistema. Va sobre una fotografía, sólo bajo el texto que se le
 * encima, y se mide desde el pie (sólido a 32, transparente a 208): la foto no se corta, se apaga en [color].
 */
fun Modifier.photoScrim(color: Color): Modifier = drawWithContent {
    drawContent()
    val reach = 208.dp.toPx()
    val top = size.height - reach
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            0.173f to color.copy(alpha = 0.24f),
            0.327f to color.copy(alpha = 0.60f),
            0.481f to color.copy(alpha = 0.88f),
            0.654f to color.copy(alpha = 0.96f),
            0.846f to color,
            1f to color,
            startY = top,
            endY = size.height,
        ),
        topLeft = Offset(0f, top.coerceAtLeast(0f)),
        size = Size(size.width, size.height - top.coerceAtLeast(0f)),
    )
}

/** Pulso plano mientras algo carga: la superficie respira, sin barrido de brillo. Aplícalo después de `clip(shape)`. */
fun Modifier.shimmer(): Modifier = composed {
    val base = AppTheme.colors.surfaceRaised
    val transition = rememberInfiniteTransition()
    val alpha = transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = Motion.standard), RepeatMode.Reverse),
    )
    drawBehind { drawRect(base, alpha = alpha.value) }
}
