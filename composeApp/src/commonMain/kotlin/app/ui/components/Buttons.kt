package app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.theme.AppTheme
import app.ui.theme.LocalContentColor
import app.ui.theme.Motion
import app.ui.theme.contentColorOn

enum class ButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }
enum class ButtonSize { SM, MD, LG }

/** Indicador de carga: arco que gira, dibujado a mano. */
@Composable
internal fun SpinnerArc(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp, strokeWidth: Dp = 2.dp) {
    val transition = rememberInfiniteTransition()
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 820, easing = LinearEasing), RepeatMode.Restart),
    )
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = color.copy(alpha = color.alpha * 0.22f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = angle.value - 90f, sweepAngle = 96f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    size: ButtonSize = ButtonSize.MD,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.control
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = enabled && !loading

    val height = when (size) { ButtonSize.SM -> 36.dp; ButtonSize.MD -> 46.dp; ButtonSize.LG -> 54.dp }
    val padH = when (size) { ButtonSize.SM -> 14.dp; ButtonSize.MD -> 20.dp; ButtonSize.LG -> 26.dp }
    val iconSize = when (size) { ButtonSize.SM -> 16.dp; ButtonSize.MD -> 18.dp; ButtonSize.LG -> 20.dp }
    val textStyle = when (size) {
        ButtonSize.SM -> AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold)
        ButtonSize.MD -> AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold)
        ButtonSize.LG -> AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }

    val baseBg: Color
    val baseFg: Color
    val borderColor: Color
    when (variant) {
        ButtonVariant.PRIMARY -> { baseBg = colors.primary; baseFg = colors.onPrimary; borderColor = Color.Transparent }
        ButtonVariant.DANGER -> { baseBg = colors.danger; baseFg = contentColorOn(colors.danger); borderColor = Color.Transparent }
        ButtonVariant.SECONDARY -> { baseBg = colors.surface; baseFg = colors.ink; borderColor = colors.lineStrong }
        ButtonVariant.GHOST -> { baseBg = Color.Transparent; baseFg = colors.ink; borderColor = Color.Transparent }
    }
    val solid = variant == ButtonVariant.PRIMARY || variant == ButtonVariant.DANGER
    // Capa de interacción: un velo del color de contenido sobre el fondo.
    val veil = when {
        !active -> 0f
        pressed -> if (solid) 0.16f else 0.09f
        hovered -> if (solid) 0.09f else 0.05f
        else -> 0f
    }
    val targetBg = when {
        !enabled && solid -> colors.surfaceSunken
        veil > 0f -> if (baseBg.alpha == 0f) baseFg.copy(alpha = veil + 0.02f) else baseFg.copy(alpha = veil).compositeOver(baseBg)
        variant == ButtonVariant.GHOST -> baseFg.copy(alpha = 0f)
        else -> baseBg
    }
    val targetFg = if (!enabled) colors.inkSubtle else baseFg
    val bg by animateColorAsState(targetBg, tween(Motion.FAST))
    val fg by animateColorAsState(targetFg, tween(Motion.FAST))
    val border by animateColorAsState(
        if (variant == ButtonVariant.SECONDARY && hovered && active) colors.inkSubtle else if (!enabled && solid) colors.line else borderColor,
        tween(Motion.FAST),
    )
    val scale by animateFloatAsState(if (pressed && active) 0.97f else 1f, Motion.snappy())
    val contentAlpha by animateFloatAsState(if (loading) 0f else 1f, tween(Motion.FAST))

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (variant == ButtonVariant.PRIMARY && enabled && !colors.isDark) {
                    Modifier.softShadow(shape, colors.primary, blur = 18.dp, offsetY = 6.dp, alpha = 0.16f)
                } else Modifier,
            )
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(shape)
            .background(bg, shape)
            .then(if (border.alpha > 0f) Modifier.border(1.dp, border, shape) else Modifier)
            .hoverable(interaction, enabled = active)
            .then(if (active) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = padH),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Row(
                modifier = Modifier.alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    IconGlyph(icon, tint = fg, size = iconSize)
                    Spacer(Modifier.width(8.dp))
                }
                AppText(text, style = textStyle, color = fg, maxLines = 1)
            }
        }
        if (loading) {
            SpinnerArc(color = fg, size = if (size == ButtonSize.SM) 16.dp else 18.dp)
        }
    }
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = AppTheme.colors.ink,
    filled: Boolean = false,
    badge: Int? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.snappy())
    val restBg = if (filled) colors.surfaceSunken else Color.Transparent
    val hoverBg = if (filled) colors.line else colors.surfaceSunken
    val bg by animateColorAsState(
        when {
            pressed -> if (filled) colors.lineStrong else colors.line
            hovered -> hoverBg
            else -> if (filled) restBg else hoverBg.copy(alpha = 0f)
        },
        tween(Motion.FAST),
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(AppTheme.shapes.pill)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            IconGlyph(icon, tint = tint, size = 22.dp)
        }

        // Insignia de conteo: aparece con escala y rebota cada vez que cambia el número.
        val count = badge ?: 0
        val lastCount = remember { intArrayOf(1) } // conserva el último número mientras la insignia sale
        if (count > 0) lastCount[0] = count
        val shown = lastCount[0]
        val bounce = remember { Animatable(1f) }
        LaunchedEffect(count) {
            if (count > 0) {
                bounce.snapTo(0.55f)
                bounce.animateTo(1f, Motion.bouncy())
            }
        }
        AnimatedVisibility(
            visible = count > 0,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 1.dp),
            enter = scaleIn(Motion.bouncy()) + fadeIn(tween(Motion.FAST)),
            exit = scaleOut(tween(Motion.FAST)) + fadeOut(tween(Motion.FAST)),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    }
                    .defaultMinSize(minWidth = 18.dp)
                    .height(18.dp)
                    .border(1.5.dp, colors.canvas, AppTheme.shapes.pill)
                    .padding(1.dp)
                    .background(colors.primary, AppTheme.shapes.pill)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = if (shown > 99) "99+" else shown.toString(),
                    style = AppTheme.type.label.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
