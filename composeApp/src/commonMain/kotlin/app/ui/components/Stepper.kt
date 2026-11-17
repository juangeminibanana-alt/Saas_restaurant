package app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion

@Composable
private fun StepButton(icon: ImageVector, tint: Color, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .pressable(enabled = enabled, scale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(icon, tint, size = 18.dp)
    }
}

/** Número que "rueda" hacia arriba o abajo al cambiar. */
@Composable
private fun RollingNumber(value: Int, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { dir * it / 2 } + fadeIn(tween(Motion.BASE)))
                .togetherWith(slideOutVertically(tween(Motion.FAST, easing = Motion.exit)) { -dir * it / 2 } + fadeOut(tween(Motion.FAST)))
        },
        contentAlignment = Alignment.Center,
        label = "qty",
    ) { q ->
        AppText(q.toString(), Modifier.widthIn(min = 26.dp), style = AppTheme.type.title, color = color, textAlign = TextAlign.Center)
    }
}

/** − cantidad + con número animado. */
@Composable
fun QuantityStepper(
    quantity: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 99,
    compact: Boolean = false,
) {
    val colors = AppTheme.colors
    val height = if (compact) 36.dp else 44.dp
    val shape = AppTheme.shapes.control
    Row(
        modifier.height(height).clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(AppIcons.Remove, colors.ink, height, enabled = quantity > min) { onChange((quantity - 1).coerceAtLeast(min)) }
        RollingNumber(quantity, colors.ink)
        StepButton(AppIcons.Add, colors.ink, height, enabled = quantity < max) { onChange((quantity + 1).coerceAtMost(max)) }
    }
}

/** "+" redondo que se transforma en stepper en color de marca cuando hay cantidad. */
@Composable
fun AddToCartButton(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AnimatedContent(
        targetState = quantity > 0,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(Motion.BASE)) + scaleIn(animationSpec = Motion.snappy(), initialScale = 0.8f))
                .togetherWith(fadeOut(tween(Motion.FAST)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterEnd,
        label = "add-to-cart",
    ) { active ->
        if (!active) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(colors.primary).pressable(scale = 0.9f, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(AppIcons.Add, colors.onPrimary, size = 20.dp)
            }
        } else {
            Row(
                Modifier.height(44.dp).clip(AppTheme.shapes.pill).background(colors.primary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton(AppIcons.Remove, colors.onPrimary, 44.dp, enabled = true, onClick = onRemove)
                RollingNumber(quantity, colors.onPrimary)
                StepButton(AppIcons.Add, colors.onPrimary, 44.dp, enabled = true, onClick = onAdd)
            }
        }
    }
}
