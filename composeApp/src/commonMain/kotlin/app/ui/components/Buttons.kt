package app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.ui.theme.AppTheme
import app.ui.theme.LocalContentColor
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space

/**
 * PRIMARY: brasa sólida, la acción de "tu pedido" (una por pantalla). SECONDARY: superficie + filete.
 * GHOST: sólo texto. DANGER: superficie + filete con texto en rojo (nunca compite con la brasa).
 */
enum class ButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

/** SM 40 · MD 48 · LG 56 de alto. */
enum class ButtonSize { SM, MD, LG }

/** Indicador de carga: arco que gira, dibujado a mano. */
@Composable
internal fun SpinnerArc(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, strokeWidth: Dp = 1.5.dp) {
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

/**
 * Botón de NOCHE: radio 12, sin sombra. [mono] añade un dato en Geist Mono tras un punto medio
 * ("Agregar · $345"); [trailingIcon] va al final ("Ver pedido →").
 */
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
    trailingIcon: ImageVector? = null,
    mono: String? = null,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.control
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = enabled && !loading

    val height = when (size) { ButtonSize.SM -> 40.dp; ButtonSize.MD -> Sizes.controlLg; ButtonSize.LG -> Sizes.controlXl }
    val padH = when (size) { ButtonSize.SM -> Space.x3; ButtonSize.MD -> Space.x4; ButtonSize.LG -> Space.x6 }
    val iconSize = if (size == ButtonSize.SM) 16.dp else Sizes.icon
    val textStyle = if (size == ButtonSize.SM) AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold) else AppTheme.type.h3
    val monoStyle = (if (size == ButtonSize.SM) AppTheme.type.mono else AppTheme.type.monoMd).copy(fontWeight = FontWeight.SemiBold)

    val control = AppTheme.controlFill
    val baseBg: Color
    val baseFg: Color
    val ringColor: Color
    when (variant) {
        ButtonVariant.PRIMARY -> { baseBg = colors.primary; baseFg = colors.onPrimary; ringColor = Color.Transparent }
        ButtonVariant.SECONDARY -> { baseBg = control; baseFg = colors.ink; ringColor = colors.line }
        ButtonVariant.DANGER -> { baseBg = control; baseFg = colors.danger; ringColor = colors.line }
        ButtonVariant.GHOST -> { baseBg = Color.Transparent; baseFg = colors.ink; ringColor = Color.Transparent }
    }
    // Respuesta al puntero: un velo plano del color del contenido sobre el relleno. Sin brillo ni sombra.
    val veil = when {
        !active -> 0f
        pressed -> if (variant == ButtonVariant.PRIMARY) 0.16f else 0.10f
        hovered -> if (variant == ButtonVariant.PRIMARY) 0.08f else 0.05f
        else -> 0f
    }
    val veilColor = if (variant == ButtonVariant.DANGER) colors.ink else baseFg
    val targetBg = when {
        !enabled && variant == ButtonVariant.PRIMARY -> control
        veil > 0f -> if (baseBg.alpha == 0f) veilColor.copy(alpha = veil + 0.02f) else veilColor.copy(alpha = veil).compositeOver(baseBg)
        variant == ButtonVariant.GHOST -> baseFg.copy(alpha = 0f)
        else -> baseBg
    }
    val targetFg = if (!enabled) colors.inkSubtle else baseFg
    val targetRing = when {
        !enabled && variant == ButtonVariant.PRIMARY -> colors.line
        ringColor.alpha > 0f && hovered && active -> colors.lineStrong
        else -> ringColor
    }
    val bg by animateColorAsState(targetBg, tween(Motion.FAST))
    val fg by animateColorAsState(targetFg, tween(Motion.FAST))
    val ring by animateColorAsState(targetRing, tween(Motion.FAST))
    val scale by animateFloatAsState(if (pressed && active) 0.98f else 1f, Motion.snappy())
    val contentAlpha by animateFloatAsState(if (loading) 0f else 1f, tween(Motion.FAST))

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(shape)
            .background(bg, shape)
            .then(if (ring.alpha > 0f) Modifier.border(1.dp, ring, shape) else Modifier)
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
                horizontalArrangement = Arrangement.spacedBy(Space.x2, Alignment.CenterHorizontally),
            ) {
                if (icon != null) IconGlyph(icon, tint = fg, size = iconSize)
                AppText(text, style = textStyle, color = fg, maxLines = 1)
                if (mono != null) {
                    AppText("·", style = textStyle, color = fg, maxLines = 1)
                    AppText(mono, style = monoStyle, color = fg, maxLines = 1)
                }
                if (trailingIcon != null) IconGlyph(trailingIcon, tint = fg, size = iconSize)
            }
        }
        if (loading) {
            SpinnerArc(color = fg, size = if (size == ButtonSize.SM) 16.dp else 20.dp)
        }
    }
}

/**
 * Botón de ícono de 44 px, radio 12. Por defecto: superficie + filete (como "Buscar" en el menú).
 * [filled]: fondo sólido con filete fuerte, para ir encima de una fotografía (cerrar / regresar).
 * [plain]: sin superficie ni filete, para acciones dentro de una fila, barra o encabezado.
 * [badge]: ficha sólida de brasa con el conteo (lo que ya está en tu pedido).
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = AppTheme.colors.ink,
    filled: Boolean = false,
    badge: Int? = null,
    plain: Boolean = false,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.snappy())
    val rest = when {
        filled -> colors.canvas
        plain -> colors.ink.copy(alpha = 0f)
        else -> AppTheme.controlFill
    }
    val veil = when {
        pressed -> 0.10f
        hovered -> 0.05f
        else -> 0f
    }
    val bg by animateColorAsState(
        when {
            veil == 0f -> rest
            plain && !filled -> colors.ink.copy(alpha = veil + 0.02f)
            else -> colors.ink.copy(alpha = veil).compositeOver(rest)
        },
        tween(Motion.FAST),
    )
    val ring = when {
        filled -> colors.lineStrong
        plain -> Color.Transparent
        hovered -> colors.lineStrong
        else -> colors.line
    }

    Box(
        modifier = modifier
            .size(Sizes.control)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.control)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(bg)
                .then(if (ring.alpha > 0f) Modifier.border(1.dp, ring, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            IconGlyph(icon, tint = tint, size = Sizes.icon)
        }

        val count = badge ?: 0
        val lastCount = remember { intArrayOf(1) } // conserva el último número mientras la ficha sale
        if (count > 0) lastCount[0] = count
        AnimatedVisibility(
            visible = count > 0,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            enter = fadeIn(tween(Motion.FAST)),
            exit = fadeOut(tween(Motion.FAST)),
        ) {
            CountChip(lastCount[0])
        }
    }
}

/** Ficha sólida de brasa con un conteo en mono: la cantidad que ya está en tu pedido. Radio 4. */
@Composable
fun CountChip(count: Int, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .height(size)
            .defaultMinSize(minWidth = size)
            .background(colors.primary, AppTheme.shapes.xs)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = if (count > 99) "99+" else count.toString(),
            style = (if (size < 24.dp) AppTheme.type.monoLabel else AppTheme.type.mono).copy(fontWeight = FontWeight.SemiBold, letterSpacing = AppTheme.type.mono.letterSpacing),
            color = colors.onPrimary,
            maxLines = 1,
        )
    }
}
