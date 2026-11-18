package app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Sizes
import app.ui.theme.Space

/** Ícono con tinte usando solo foundation (sin Material). */
@Composable
internal fun GlyphIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    androidx.compose.foundation.Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * Mosaico con un ícono al centro (cabeceras de estados, tarjetas de métricas, etc.): relleno plano con filete, radio 8.
 * Un [background] de la familia "suave" se pinta tal cual; el ícono lleva [tint].
 */
@Composable
internal fun GlyphTile(icon: ImageVector, tint: Color, background: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape = AppTheme.shapes.sm
    Box(
        modifier.size(size).background(background, shape).border(1.dp, AppTheme.colors.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(icon, tint, size = if (size >= 40.dp) Sizes.icon else 16.dp)
    }
}

/** Tres puntos de tinta que se encienden en cascada: carga tranquila, plana, sin spinner genérico. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().padding(Space.x8), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val transition = rememberInfiniteTransition(label = "loading")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val phase by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1100
                                0f at 0 using LinearEasing
                                1f at 260 using LinearEasing
                                0f at 620 using LinearEasing
                                0f at 1100
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = androidx.compose.animation.core.StartOffset(index * 150),
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = 0.24f + 0.76f * phase }
                            .background(colors.ink, CircleShape),
                    )
                }
            }
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x4))
                LabelText(message, color = colors.inkSubtle, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().padding(Space.x8), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphTile(AppIcons.Warning, tint = colors.danger, background = AppTheme.controlFill, size = Sizes.control)
            Spacer(Modifier.height(Space.x4))
            AppText("Algo no salió bien", style = AppTheme.type.h2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.x2))
            AppText(message, style = AppTheme.type.bodySm, color = colors.inkMuted, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(Space.x5))
                AppButton("Reintentar", onClick = onRetry, variant = ButtonVariant.SECONDARY, icon = AppIcons.Refresh)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String = "",
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().padding(Space.x8), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                GlyphTile(icon, tint = colors.inkMuted, background = AppTheme.controlFill, size = Sizes.control)
                Spacer(Modifier.height(Space.x4))
            }
            AppText(title, style = AppTheme.type.h2, textAlign = TextAlign.Center)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(Space.x2))
                AppText(message, style = AppTheme.type.bodySm, color = colors.inkMuted, textAlign = TextAlign.Center)
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(Space.x5))
                AppButton(actionText, onClick = onAction, variant = ButtonVariant.SECONDARY)
            }
        }
    }
}
