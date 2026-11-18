package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Categorías como el riel segmentado de NOCHE, con desplazamiento horizontal: una pista completa y cerrada
 * (superficie + filete, radio 12, 44 de alto) y la pastilla activa en tinta sólida 4 px adentro (radio 8).
 * Lo que asoma por el borde al desplazar es la pista, no un corte. Cada segmento reserva su ancho en
 * seminegrita: activar uno no mueve a los vecinos. La categoría activa se centra sola.
 */
@Composable
fun CategoryTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val trackShape = AppTheme.shapes.md
    val pillShape = AppTheme.shapes.sm
    val scroll = rememberScrollState()
    // índice -> (x, ancho) en px dentro de la pista
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds[selectedIndex]

    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (!placed) {
            left.snapTo(t.first)
            width.snapTo(t.second)
            placed = true
        } else {
            launch { left.animateTo(t.first, Motion.snappy()) }
            launch { width.animateTo(t.second, Motion.snappy()) }
        }
    }
    LaunchedEffect(selectedIndex, target) {
        val t = target ?: return@LaunchedEffect
        val viewport = scroll.viewportSize
        if (viewport > 0) {
            val centered = (t.first + t.second / 2f - viewport / 2f).roundToInt().coerceIn(0, scroll.maxValue)
            scroll.animateScrollTo(centered, tween(Motion.SLOW, easing = Motion.standard))
        }
    }

    val pillColor = colors.select
    val regular = AppTheme.type.bodySm.copy(fontWeight = FontWeight.Medium)
    val strong = AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold)
    Box(modifier.horizontalScroll(scroll)) {
        Row(
            modifier = Modifier
                .height(Sizes.control)
                .background(AppTheme.controlFill, trackShape)
                .border(1.dp, colors.line, trackShape)
                .drawBehind {
                    if (width.value > 0f) {
                        val pad = 4.dp.toPx()
                        val pill = Size((width.value - pad * 2).coerceAtLeast(0f), size.height - pad * 2)
                        val outline = pillShape.createOutline(pill, layoutDirection, this)
                        translate(left.value + pad, pad) { drawOutline(outline, pillColor) }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val textColor by animateColorAsState(
                    when {
                        selected -> colors.onSelect
                        hovered -> colors.ink
                        else -> colors.inkMuted
                    },
                    tween(Motion.FAST),
                )
                Box(
                    modifier = Modifier
                        .onPlaced { coords ->
                            val next = coords.positionInParent().x to coords.size.width.toFloat()
                            if (bounds[index] != next) bounds[index] = next
                        }
                        .height(Sizes.control)
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = Space.x4),
                    contentAlignment = Alignment.Center,
                ) {
                    // Reserva invisible en seminegrita: el ancho no cambia al activarse.
                    AppText(tab, modifier = Modifier.alpha(0f).clearAndSetSemantics { }, style = strong, maxLines = 1)
                    AppText(tab, style = if (selected) strong else regular, color = textColor, maxLines = 1)
                }
            }
        }
    }
}
