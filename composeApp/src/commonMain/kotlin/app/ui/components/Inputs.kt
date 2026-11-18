package app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Campo de texto de NOCHE: etiqueta en mono MAYÚSCULAS arriba, superficie + filete de 1 px, radio 12, 48 de alto.
 * Al enfocar, el filete pasa a tinta (nunca a brasa); con error, a rojo y el mensaje aparece debajo.
 */
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
    val shape = AppTheme.shapes.md

    val ring by animateColorAsState(
        when {
            hasError -> colors.danger
            focused -> colors.ink
            else -> colors.line
        },
        tween(Motion.FAST),
    )
    val labelColor by animateColorAsState(
        when {
            hasError -> colors.danger
            focused -> colors.ink
            else -> colors.inkSubtle
        },
        tween(Motion.FAST),
    )
    // Una sola línea: 16/20 para que el texto quede centrado en 48; varias líneas: 16/24.
    val fieldStyle = if (multiline) AppTheme.type.body else AppTheme.type.body.copy(lineHeight = 20.sp)
    val textStyle = fieldStyle.copy(color = if (enabled) colors.ink else colors.inkSubtle)
    val fill = AppTheme.controlFill

    Column(modifier) {
        if (label != null) {
            LabelText(label, color = labelColor)
            Spacer(Modifier.height(Space.x2))
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
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(fill, shape)
                        .border(1.dp, ring, shape)
                        .defaultMinSize(minHeight = Sizes.controlLg)
                        .padding(horizontal = Space.x4, vertical = if (multiline) Space.x3 else 0.dp),
                    verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        IconGlyph(leadingIcon, tint = if (focused) colors.ink else colors.inkSubtle, size = Sizes.icon)
                        Spacer(Modifier.width(Space.x3))
                    }
                    Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            AppText(placeholder, style = fieldStyle, color = colors.inkSubtle, maxLines = if (multiline) 3 else 1)
                        }
                        inner()
                    }
                    if (isPassword) {
                        Spacer(Modifier.width(Space.x2))
                        Box(
                            Modifier
                                .size(Sizes.controlSm)
                                .clip(AppTheme.shapes.sm)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = if (reveal) "Ocultar contraseña" else "Mostrar contraseña",
                                ) { reveal = !reveal },
                            contentAlignment = Alignment.Center,
                        ) {
                            IconGlyph(AppIcons.Eye, tint = if (reveal) colors.ink else colors.inkSubtle, size = Sizes.icon)
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
                modifier = Modifier.padding(top = Space.x2),
                style = AppTheme.type.bodySm,
                color = colors.danger,
                maxLines = 2,
            )
        }
    }
}

/**
 * Interruptor plano de 44 × 24. Encendido: pista en tinta sólida con el pulgar oscuro (la selección es tinta,
 * no brasa). Apagado: superficie + filete con el pulgar gris.
 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val trackShape = AppTheme.shapes.md
    val track by animateColorAsState(if (checked) colors.select else colors.surfaceRaised, tween(Motion.FAST))
    val ring by animateColorAsState(if (checked) Color.Transparent else colors.lineStrong, tween(Motion.FAST))
    val thumbColor by animateColorAsState(if (checked) colors.onSelect else colors.inkMuted, tween(Motion.FAST))
    val thumbX by animateDpAsState(if (checked) 24.dp else 4.dp, Motion.snappy())

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .background(track, trackShape)
            .border(1.dp, ring, trackShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = thumbX).size(16.dp).background(thumbColor, AppTheme.shapes.sm))
    }
}

/**
 * Control segmentado de NOCHE: riel de superficie + filete (radio 12, 44 de alto) y, 4 px adentro, la pastilla
 * activa en tinta sólida con texto oscuro (radio 8). Cada segmento mide lo que su texto y el sobrante se
 * reparte por igual, así que las etiquetas largas no se cortan; el ancho de cada uno se reserva en seminegrita
 * para que activar uno no mueva a los vecinos.
 * [large]: 48 de alto y cuerpo 16, para elecciones del platillo (término, tamaño…).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val pillShape = AppTheme.shapes.sm
    val pillColor = colors.select
    val height = if (large) Sizes.controlLg else Sizes.control
    val padH = if (large) Space.x1 + Space.x1 else Space.x3
    val regular = if (large) AppTheme.type.title else AppTheme.type.bodySm.copy(fontWeight = FontWeight.Medium)
    val strong = regular.copy(fontWeight = FontWeight.SemiBold)

    // índice -> (x, ancho) en px dentro del riel; la pastilla se desliza hasta el segmento activo
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds[selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))]
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

    Layout(
        modifier = modifier
            .height(height)
            .background(AppTheme.controlFill, shape)
            .border(1.dp, colors.line, shape)
            .drawBehind {
                if (width.value > 0f) {
                    val pad = 4.dp.toPx()
                    val pill = Size((width.value - pad * 2).coerceAtLeast(0f), size.height - pad * 2)
                    val outline = pillShape.createOutline(pill, layoutDirection, this)
                    translate(left.value + pad, pad) { drawOutline(outline, pillColor) }
                }
            },
        content = {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(if (selected) colors.onSelect else colors.inkMuted, tween(Motion.FAST))
                Box(
                    modifier = Modifier
                        .onPlaced { coords ->
                            val next = coords.positionInParent().x to coords.size.width.toFloat()
                            if (bounds[index] != next) bounds[index] = next
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { if (!selected) onSelect(index) }
                        .padding(horizontal = padH),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(option, modifier = Modifier.alpha(0f).clearAndSetSemantics { }, style = strong, maxLines = 1)
                    AppText(option, style = if (selected) strong else regular, color = textColor, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        },
    ) { measurables, constraints ->
        val n = measurables.size
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else height.roundToPx()
        if (n == 0) return@Layout layout(constraints.minWidth, h) { }
        // Como `flex: 1 0 auto`: base = ancho del contenido; el sobrante se reparte por igual.
        val natural = IntArray(n) { measurables[it].maxIntrinsicWidth(h) }
        val total = natural.sum()
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else total.coerceAtLeast(constraints.minWidth)
        val widths = IntArray(n)
        var used = 0
        for (i in 0 until n) {
            widths[i] = if (total <= w) natural[i] + (w - total) / n else (natural[i].toLong() * w / total.coerceAtLeast(1)).toInt()
            used += widths[i]
        }
        widths[n - 1] += w - used
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i].coerceAtLeast(0), h)) }
        layout(w, h) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, 0)
                x += p.width
            }
        }
    }
}
