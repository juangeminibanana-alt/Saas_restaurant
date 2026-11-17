package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pestañas de categoría con scroll horizontal. Una pastilla suave en el color de marca se desliza
 * con resorte hasta la pestaña activa, que además se centra sola en la vista.
 */
@Composable
fun CategoryTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.pill
    val scroll = rememberScrollState()
    // índice -> (x, ancho) en px dentro del contenido desplazable
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

    val pillColor = colors.primarySoft
    Row(
        modifier = modifier
            .horizontalScroll(scroll)
            .drawBehind {
                if (width.value > 0f) {
                    val outline = shape.createOutline(Size(width.value, size.height), layoutDirection, this)
                    translate(left.value, 0f) { drawOutline(outline, pillColor) }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val textColor by animateColorAsState(
                if (selected || hovered) colors.ink else colors.inkMuted,
                tween(Motion.BASE),
            )
            Box(
                modifier = Modifier
                    .onPlaced { coords ->
                        val next = coords.positionInParent().x to coords.size.width.toFloat()
                        if (bounds[index] != next) bounds[index] = next
                    }
                    .height(40.dp)
                    .clip(shape)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = Space.x4),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = tab,
                    style = AppTheme.type.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}
