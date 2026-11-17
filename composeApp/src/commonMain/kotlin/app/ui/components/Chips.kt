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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import app.ui.theme.AppTheme
import app.ui.theme.Motion

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, DANGER, INFO }

/** (color de texto/acento, fondo suave) para un tono. */
@Composable
private fun tonePair(tone: Tone): Pair<Color, Color> {
    val c = AppTheme.colors
    return when (tone) {
        Tone.NEUTRAL -> c.inkMuted to c.surfaceSunken
        Tone.PRIMARY -> c.primary to c.primarySoft
        Tone.SUCCESS -> c.success to c.successSoft
        Tone.WARNING -> c.warning to c.warningSoft
        Tone.DANGER -> c.danger to c.dangerSoft
        Tone.INFO -> c.info to c.infoSoft
    }
}

/** Chip de filtro / opción. Seleccionado: fondo en color de marca. */
@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null
    val bg by animateColorAsState(
        when {
            selected -> colors.primary
            hovered && clickable -> colors.surfaceSunken
            else -> colors.surface
        },
        tween(Motion.BASE),
    )
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.ink, tween(Motion.BASE))
    val border by animateColorAsState(
        when {
            selected -> colors.primary
            hovered && clickable -> colors.inkSubtle
            else -> colors.lineStrong
        },
        tween(Motion.BASE),
    )
    val scale by animateFloatAsState(if (pressed && clickable) 0.95f else 1f, Motion.snappy())

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(36.dp)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else Modifier,
            )
            .padding(horizontal = 14.dp),
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

/** Estado en forma de píldora, con punto de color opcional. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL, dot: Boolean = true) {
    val (fg, bg) = tonePair(tone)
    Row(
        modifier = modifier
            .height(26.dp)
            .background(bg, AppTheme.shapes.pill)
            .padding(start = if (dot) 9.dp else 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        AppText(
            text = text,
            style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (tone == Tone.NEUTRAL) AppTheme.colors.inkMuted else fg,
            maxLines = 1,
        )
    }
}

/** Etiqueta compacta (p. ej. "Nuevo", "Vegano", "Popular"). */
@Composable
fun TagBadge(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL) {
    val (fg, bg) = tonePair(tone)
    Box(
        modifier = modifier
            .height(22.dp)
            .background(bg, AppTheme.shapes.xs)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            style = AppTheme.type.label.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
            color = fg,
            maxLines = 1,
        )
    }
}
