package app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

/** Campo de texto: etiqueta arriba, borde que se enciende en el color de marca al enfocar, error animado. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hasError = !error.isNullOrBlank()
    var reveal by remember { mutableStateOf(false) }
    val multiline = !singleLine || minLines > 1
    // Un campo de varias líneas en forma de cápsula se ve mal: usa el radio pequeño.
    val shape = if (multiline) AppTheme.shapes.sm else AppTheme.shapes.control

    val borderColor by animateColorAsState(
        when {
            hasError -> colors.danger
            focused -> colors.primary
            else -> colors.lineStrong
        },
        tween(Motion.BASE),
    )
    val borderWidth by animateDpAsState(if (focused || hasError) 1.75.dp else 1.dp, tween(Motion.BASE))
    val halo by animateFloatAsState(if (focused && enabled) 1f else 0f, tween(Motion.BASE))
    val labelColor by animateColorAsState(
        when {
            hasError -> colors.danger
            focused -> colors.ink
            else -> colors.inkMuted
        },
        tween(Motion.BASE),
    )
    val textStyle = AppTheme.type.body.copy(color = if (enabled) colors.ink else colors.inkSubtle)
    val haloColor = (if (hasError) colors.danger else colors.primary).copy(alpha = if (colors.isDark) 0.22f else 0.13f)

    Column(modifier) {
        if (label != null) {
            AppText(label, style = AppTheme.type.label, color = labelColor, maxLines = 1)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = textStyle,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            visualTransformation = if (isPassword && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Halo de enfoque: anillo suave alrededor del campo.
                            if (halo > 0f) {
                                val grow = 3.dp.toPx() * halo
                                val outline = shape.createOutline(
                                    Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this,
                                )
                                translate(-grow, -grow) { drawOutline(outline, haloColor.copy(alpha = haloColor.alpha * halo)) }
                            }
                        }
                        .background(if (enabled) colors.surface else colors.surfaceSunken, shape)
                        .border(borderWidth, borderColor, shape)
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = if (multiline) 14.dp else 16.dp, vertical = if (multiline) 12.dp else 0.dp),
                    verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        IconGlyph(leadingIcon, tint = if (focused) colors.ink else colors.inkSubtle, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            AppText(placeholder, style = AppTheme.type.body, color = colors.inkSubtle, maxLines = if (multiline) 3 else 1)
                        }
                        inner()
                    }
                    if (isPassword) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(AppTheme.shapes.pill)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = if (reveal) "Ocultar contraseña" else "Mostrar contraseña",
                                ) { reveal = !reveal },
                            contentAlignment = Alignment.Center,
                        ) {
                            IconGlyph(AppIcons.Eye, tint = if (reveal) colors.primary else colors.inkSubtle, size = 18.dp)
                        }
                    }
                }
            },
        )
        AnimatedVisibility(
            visible = hasError,
            enter = expandVertically(tween(Motion.BASE)) + fadeIn(tween(Motion.BASE)),
            exit = shrinkVertically(tween(Motion.FAST)) + fadeOut(tween(Motion.FAST)),
        ) {
            AppText(
                text = error.orEmpty(),
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                style = AppTheme.type.bodySm,
                color = colors.danger,
                maxLines = 2,
            )
        }
    }
}

/** Interruptor con pulgar animado por resorte. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) colors.primary else colors.lineStrong, tween(Motion.BASE))
    val thumbColor by animateColorAsState(
        if (checked) colors.onPrimary else if (colors.isDark) colors.ink else Color.White,
        tween(Motion.BASE),
    )
    val thumbWidth by animateDpAsState(if (pressed && enabled) 27.dp else 22.dp, Motion.snappy())
    val thumbX by animateDpAsState(if (checked) 46.dp - 3.dp - thumbWidth else 3.dp, Motion.snappy())

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .background(track, AppTheme.shapes.pill),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(width = thumbWidth, height = 22.dp)
                .softShadow(AppTheme.shapes.pill, Color.Black, blur = 6.dp, offsetY = 2.dp, alpha = if (colors.isDark) 0f else 0.16f)
                .background(thumbColor, AppTheme.shapes.pill),
        )
    }
}

/** Control segmentado: la pastilla seleccionada se desliza entre opciones. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.control
    val count = options.size.coerceAtLeast(1)
    val position by animateFloatAsState(selectedIndex.coerceIn(0, count - 1).toFloat(), Motion.snappy())
    val pillColor = if (colors.isDark) colors.surfaceRaised else colors.surface
    val pillBorder = if (colors.isDark) colors.lineStrong else colors.line
    val isDark = colors.isDark

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 44.dp)
            .background(colors.surfaceSunken, shape)
            .border(1.dp, colors.line, shape)
            .drawBehind {
                val pad = 4.dp.toPx()
                val segW = (size.width - pad * 2) / count
                val segSize = Size(segW, size.height - pad * 2)
                val outline = shape.createOutline(segSize, layoutDirection, this)
                translate(pad + segW * position, pad) {
                    if (!isDark) {
                        // sombra corta bajo la pastilla
                        translate(0f, 1.5.dp.toPx()) { drawOutline(outline, Color.Black.copy(alpha = 0.07f)) }
                    }
                    drawOutline(outline, pillColor)
                    drawOutline(outline, pillBorder, style = Stroke(1.dp.toPx()))
                }
            }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val textColor by animateColorAsState(if (selected) colors.ink else colors.inkMuted, tween(Motion.BASE))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { if (!selected) onSelect(index) }
                    .padding(horizontal = Space.x3),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = option,
                    style = AppTheme.type.bodySm.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = textColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
