package app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Sizes

@Composable
private fun StepButton(icon: ImageVector, label: String, width: Dp, height: Dp, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .size(width, height)
            .semantics { contentDescription = label }
            .pressable(enabled = enabled, scale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(icon, if (enabled) colors.ink else colors.inkSubtle, size = Sizes.icon)
    }
}

/** Número que cambia con un desplazamiento corto; el contenedor no se mueve. */
@Composable
private fun RollingNumber(value: Int, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(Motion.FAST, easing = Motion.standard)) { dir * it / 3 } + fadeIn(tween(Motion.FAST)))
                .togetherWith(slideOutVertically(tween(Motion.FAST, easing = Motion.exit)) { -dir * it / 3 } + fadeOut(tween(Motion.FAST)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "qty",
    ) { q ->
        AppText(q.toString(), style = style, color = color, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * − cantidad +. Filete siempre neutro. `compact`: cara de 32 px para filas (radio 8); normal: 120 × 48 para
 * la barra del platillo (radio 12). Con [inOrder] la cantidad es una ficha sólida de brasa: ya está en tu
 * pedido (por defecto, en el tamaño compacto cuando hay cantidad).
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 99,
    compact: Boolean = false,
    inOrder: Boolean = compact && quantity > 0,
) {
    val colors = AppTheme.colors
    val height = if (compact) Sizes.controlSm else Sizes.controlLg
    val shape = if (compact) AppTheme.shapes.sm else AppTheme.shapes.md
    val chipShape = if (compact) AppTheme.shapes.xs else AppTheme.shapes.sm
    val chip = if (compact) 24.dp else 32.dp
    val slot = if (compact) 28.dp else 32.dp
    val button = if (compact) Sizes.controlSm else Sizes.control
    val numberStyle = (if (compact) AppTheme.type.mono else AppTheme.type.monoMd)
        .let { if (inOrder) it.copy(fontWeight = FontWeight.SemiBold) else it }
    Row(
        modifier
            .then(if (compact) Modifier else Modifier.width(120.dp))
            .height(height)
            .clip(shape)
            .background(AppTheme.controlFill)
            .border(1.dp, colors.line, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton(AppIcons.Remove, "Quitar uno", button, height, enabled = quantity > min) { onChange((quantity - 1).coerceAtLeast(min)) }
        Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
            if (inOrder) {
                Box(Modifier.size(chip).background(colors.primary, chipShape), contentAlignment = Alignment.Center) {
                    RollingNumber(quantity, colors.onPrimary, numberStyle)
                }
            } else {
                RollingNumber(quantity, colors.ink, numberStyle)
            }
        }
        StepButton(AppIcons.Add, "Agregar uno", button, height, enabled = quantity < max) { onChange((quantity + 1).coerceAtMost(max)) }
    }
}

/**
 * "+" de fila (cara de 32 px, superficie + filete, radio 8) que se vuelve stepper cuando ya hay cantidad;
 * entonces la cantidad es una ficha sólida de brasa. El objetivo táctil es de 44 px aunque la cara mida 32.
 */
@Composable
fun AddToCartButton(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AnimatedContent(
        targetState = quantity > 0,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(Motion.FAST)).togetherWith(fadeOut(tween(Motion.FAST))).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterEnd,
        label = "add-to-cart",
    ) { active ->
        if (!active) {
            val shape = AppTheme.shapes.sm
            Box(
                Modifier
                    .size(Sizes.controlSm)
                    .clip(shape)
                    .background(AppTheme.controlFill)
                    .border(1.dp, colors.line, shape)
                    .semantics { contentDescription = "Agregar" }
                    .pressable(scale = 0.92f, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(AppIcons.Add, colors.ink, size = Sizes.icon)
            }
        } else {
            QuantityStepper(
                quantity = quantity,
                onChange = { next -> if (next > quantity) onAdd() else if (next < quantity) onRemove() },
                compact = true,
                inOrder = true,
            )
        }
    }
}
