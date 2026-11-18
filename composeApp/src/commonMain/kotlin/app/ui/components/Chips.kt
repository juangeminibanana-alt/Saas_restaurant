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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, DANGER, INFO }

/** Color sólido de un tono: sólo se usa en el punto de estado o en el texto de una etiqueta, nunca como relleno suave. */
@Composable
@ReadOnlyComposable
internal fun toneColor(tone: Tone): Color {
    val c = AppTheme.colors
    return when (tone) {
        Tone.NEUTRAL -> c.inkSubtle
        Tone.PRIMARY -> c.primary
        Tone.SUCCESS -> c.success
        Tone.WARNING -> c.warning
        Tone.DANGER -> c.danger
        Tone.INFO -> c.info
    }
}

/**
 * Chip de filtro / opción: superficie + filete, radio 8. Seleccionado: tinta sólida con texto oscuro
 * (la selección nunca usa la brasa).
 */
@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.sm
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null
    val fill = AppTheme.controlFill
    val bg by animateColorAsState(if (selected) colors.select else fill, tween(Motion.FAST))
    val fg by animateColorAsState(
        when {
            selected -> colors.onSelect
            hovered && clickable -> colors.ink
            else -> colors.inkMuted
        },
        tween(Motion.FAST),
    )
    val ring by animateColorAsState(
        when {
            selected -> Color.Transparent
            hovered && clickable -> colors.lineStrong
            else -> colors.line
        },
        tween(Motion.FAST),
    )
    val scale by animateFloatAsState(if (pressed && clickable) 0.97f else 1f, Motion.snappy())

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(36.dp)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, ring, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else Modifier,
            )
            .padding(horizontal = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconGlyph(icon, tint = fg, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        AppText(
            text = text,
            style = AppTheme.type.bodySm.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
            maxLines = 1,
        )
    }
}

/**
 * Estado: ficha plana (superficie + filete, radio 4) con un punto sólido del tono y la etiqueta en mono
 * MAYÚSCULAS. El color vive sólo en el punto; el texto es tinta.
 */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL, dot: Boolean = true) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.xs
    Row(
        modifier = modifier
            .height(24.dp)
            .background(AppTheme.controlFill, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            StatusDot(toneColor(tone))
            Spacer(Modifier.width(6.dp))
        }
        LabelText(text, color = if (tone == Tone.NEUTRAL) colors.inkMuted else colors.ink)
    }
}

/**
 * Etiqueta compacta en mono MAYÚSCULAS (p. ej. "NUEVO", "VEGANO", "POPULAR"), radio 4.
 * NEUTRAL y semánticos: ficha del color del fondo con filete (legible también encima de una fotografía) y el
 * texto en el color del tono. PRIMARY: tinta sólida (destacado); nunca brasa.
 */
@Composable
fun TagBadge(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.xs
    val solid = tone == Tone.PRIMARY
    Box(
        modifier = modifier
            .height(20.dp)
            .then(
                if (solid) Modifier.background(colors.select, shape)
                else Modifier.background(colors.canvas, shape).border(1.dp, colors.line, shape),
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        LabelText(
            text,
            color = when (tone) {
                Tone.PRIMARY -> colors.onSelect
                Tone.NEUTRAL -> colors.inkMuted
                else -> toneColor(tone)
            },
        )
    }
}
