package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.Motion
import app.ui.theme.Space

/**
 * Mosaico base de NOCHE: superficie 1 con filete interior de 1 px y radio 16. Sin sombra.
 * `elevated` no levanta nada: sólo refuerza el filete. Dentro de otra superficie la tarjeta sube sola a
 * superficie 2. Si es clicable, el filete se refuerza al pasar o presionar.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.x4,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.lg
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null
    val scale by animateFloatAsState(if (pressed && clickable) 0.99f else 1f, Motion.snappy())
    val ring by animateColorAsState(
        if (elevated || (clickable && (hovered || pressed))) colors.lineStrong else colors.line,
        tween(Motion.FAST),
    )
    // Una tarjeta dentro de otra superficie (tarjeta, hoja, diálogo, barra) sube a superficie 2.
    val raised = LocalSurfaceLevel.current == 1
    val bg = if (raised) colors.surfaceRaised else colors.surface

    CompositionLocalProvider(LocalSurfaceLevel provides if (raised) 2 else 1) {
        Column(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(bg, shape)
                .border(1.dp, ring, shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .hoverable(interaction)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    } else Modifier,
                )
                .padding(padding),
            content = content,
        )
    }
}

/** Filete divisor de 1 px. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
}

/**
 * Encabezado de sección: título 20/24 a la izquierda y, a la derecha, una etiqueta en mono ([label],
 * p. ej. "SELECCIÓN DE LA CASA") o una acción. [subtitle] va debajo del título, en texto chico.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    label: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppText(title, modifier = Modifier.weight(1f, fill = false).alignByBaseline(), style = AppTheme.type.h2, maxLines = 2)
                if (!label.isNullOrBlank()) {
                    LabelText(label, modifier = Modifier.padding(start = Space.x4).alignByBaseline())
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x1))
                AppText(subtitle, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 2)
            }
        }
        if (action != null) {
            Spacer(Modifier.width(Space.x3))
            action()
        }
    }
}
