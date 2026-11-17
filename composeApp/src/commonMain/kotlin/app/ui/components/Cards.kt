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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import app.ui.theme.Motion
import app.ui.theme.Space

/**
 * Superficie base. Claro: borde finísimo + sombra suave y ancha si `elevated`.
 * Oscuro: sin sombra; la elevación es una superficie más clara con borde.
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
    val shape = AppTheme.shapes.md
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clickable = onClick != null
    val scale by animateFloatAsState(if (pressed && clickable) 0.985f else 1f, Motion.snappy())
    val lift by animateFloatAsState(
        when {
            colors.isDark -> 0f
            elevated && hovered && clickable -> 1.5f
            elevated -> 1f
            hovered && clickable -> 0.6f
            else -> 0f
        },
        Motion.gentle(),
    )
    val border by animateColorAsState(
        when {
            clickable && (hovered || pressed) -> colors.lineStrong
            elevated && !colors.isDark -> colors.line.copy(alpha = 0.7f)
            else -> colors.line
        },
        tween(Motion.BASE),
    )
    val bg = if (elevated) colors.surfaceRaised else colors.surface

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .softShadow(shape, colors.ink, blur = 32.dp, offsetY = 12.dp, alpha = 0.075f * lift)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
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

/** Línea divisoria de 1dp. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
}

/** Encabezado de sección: título + subtítulo opcional y una acción alineada a la derecha. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = AppTheme.type.h3, maxLines = 2)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                AppText(subtitle, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 2)
            }
        }
        if (action != null) {
            Spacer(Modifier.width(Space.x3))
            action()
        }
    }
}
