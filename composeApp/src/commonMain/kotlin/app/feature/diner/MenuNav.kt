package app.feature.diner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.edgeBottom
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Buscar + categorías: una sola fila de 72, siempre pegada arriba (16 arriba, 44 de control, 12 abajo).
 * En reposo: botón de buscar de 44 y el carril segmentado, que se desplaza y asoma por el borde derecho.
 * Buscando: el carril cede su lugar al campo de búsqueda y un botón para cerrarlo.
 * [stuck]: ya está pegada — gana su filete inferior.
 */
@Composable
internal fun MenuNav(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    stuck: Boolean,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .then(if (stuck) Modifier.edgeBottom(colors.line) else Modifier)
            .padding(start = gutter, top = Space.x4, bottom = Space.x3),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            MenuSearchField(query, onQueryChange, Modifier.weight(1f))
            AppIconButton(AppIcons.Close, onClick = onCloseSearch, contentDescription = "Cerrar la búsqueda")
            Spacer(Modifier.width(gutter - Space.x2))
        } else {
            AppIconButton(AppIcons.Search, onClick = onOpenSearch, contentDescription = "Buscar en el menú")
            if (tabs.isNotEmpty()) MenuRail(tabs, selectedIndex, onSelect, Modifier.weight(1f), endGutter = gutter)
        }
    }
}

/** Campo de búsqueda de 44: superficie + filete (tinta al enfocar), lupa y texto 16/20. Toma el foco al aparecer. */
@Composable
private fun MenuSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val fill = AppTheme.controlFill
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val ring by animateColorAsState(if (focused) colors.ink else colors.line, tween(Motion.FAST), label = "search-ring")
    val style = AppTheme.type.title.copy(fontWeight = FontWeight.Normal)
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // Aún sin nodo de foco (primer cuadro): el campo sigue siendo usable al tocarlo.
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 60) onValueChange(it) },
        modifier = modifier.focusRequester(focus),
        textStyle = style.copy(color = colors.ink),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.ink),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.control)
                    .background(fill, shape)
                    .border(Sizes.hairline, ring, shape)
                    .padding(horizontal = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DinerIcon(AppIcons.Search, tint = if (focused) colors.ink else colors.inkSubtle)
                Spacer(Modifier.width(Space.x3))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) AppText("Buscar platillos, ingredientes…", style = style, color = colors.inkSubtle, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/**
 * Carril de categorías de la maqueta: una pista completa y cerrada (superficie + filete, radio 12, 44 de
 * alto) que se desplaza; la pastilla activa en tinta sólida va 4 px adentro (radio 8). Lo que asoma por el
 * borde es la pista, no un corte, y al llegar al tope queda el margen de página. Al cambiar de categoría el
 * segmento anterior queda al borde del carril: nunca se corta una etiqueta por la izquierda.
 * Cada segmento reserva su ancho en seminegrita: activar uno no mueve a los vecinos.
 */
@Composable
internal fun MenuRail(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    endGutter: Dp = Space.gutter,
) {
    val colors = AppTheme.colors
    val trackShape = AppTheme.shapes.md
    val pillShape = AppTheme.shapes.sm
    val scroll = rememberScrollState()
    // índice -> (x, ancho) en px dentro de la pista
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds[selectedIndex]
    val lead = bounds[(selectedIndex - 1).coerceAtLeast(0)]

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
    LaunchedEffect(selectedIndex, lead) {
        val l = lead ?: return@LaunchedEffect
        if (scroll.viewportSize > 0) {
            scroll.animateScrollTo(l.first.roundToInt().coerceIn(0, scroll.maxValue), tween(Motion.SLOW, easing = Motion.standard))
        }
    }

    val pillColor = colors.select
    val regular = AppTheme.type.bodySm.copy(fontWeight = FontWeight.Medium)
    val strong = AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold)
    Row(modifier.horizontalScroll(scroll)) {
        Row(
            modifier = Modifier
                .height(Sizes.control)
                .background(AppTheme.controlFill, trackShape)
                .border(Sizes.hairline, colors.line, trackShape)
                .drawBehind {
                    if (width.value > 0f) {
                        val pad = Space.x1.toPx()
                        val pill = Size((width.value - pad * 2).coerceAtLeast(0f), size.height - pad * 2)
                        val outline = pillShape.createOutline(pill, layoutDirection, this)
                        translate(left.value + pad, pad) { drawOutline(outline, pillColor) }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val textColor by animateColorAsState(
                    when {
                        selected -> colors.onSelect
                        hovered -> colors.ink
                        else -> colors.inkMuted
                    },
                    tween(Motion.FAST),
                    label = "rail-text",
                )
                Box(
                    modifier = Modifier
                        .onPlaced { coords ->
                            val next = coords.positionInParent().x to coords.size.width.toFloat()
                            if (bounds[index] != next) bounds[index] = next
                        }
                        .height(Sizes.control)
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = Space.x4),
                    contentAlignment = Alignment.Center,
                ) {
                    // Reserva invisible en seminegrita: el ancho no cambia al activarse.
                    AppText(tab, modifier = Modifier.alpha(0f).clearAndSetSemantics { }, style = strong, maxLines = 1)
                    AppText(tab, style = if (selected) strong else regular, color = textColor, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(endGutter))
    }
}
