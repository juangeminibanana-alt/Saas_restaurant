package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space

/**
 * Marca de selección de 20 px. La selección es tinta sólida, nunca brasa.
 * Radio (por defecto): aro de 1.5 px que al elegirse pasa a tinta y enciende un punto de 10 px.
 * [multiple]: casilla de radio 4 que al elegirse se rellena de tinta con una palomita oscura.
 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, multiple: Boolean = false) {
    val colors = AppTheme.colors
    val ring by animateColorAsState(if (selected) colors.select else colors.inkSubtle, tween(Motion.FAST), label = "mark-ring")
    val fill by animateColorAsState(if (selected) colors.select else colors.select.copy(alpha = 0f), tween(Motion.FAST), label = "mark-fill")
    if (multiple) {
        val shape = AppTheme.shapes.xs
        Box(modifier.size(20.dp).background(fill, shape).border(1.5.dp, ring, shape), contentAlignment = Alignment.Center) {
            if (selected) GlyphIcon(AppIcons.Check, colors.onSelect, size = 14.dp)
        }
    } else {
        Box(modifier.size(20.dp).border(1.5.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(fill, CircleShape))
        }
    }
}

/**
 * Lista de opciones de NOCHE: un solo bloque de superficie + filete (radio 12) con filas separadas por filetes.
 * Dentro van [OptionRow] (o cualquier fila propia de 48 de alto).
 */
@Composable
fun OptionList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val fill = AppTheme.controlFill
    val level = LocalSurfaceLevel.current
    CompositionLocalProvider(LocalSurfaceLevel provides if (level == 1) 2 else 1) {
        Column(
            modifier.fillMaxWidth().clip(shape).background(fill).border(1.dp, colors.line, shape),
            content = content,
        )
    }
}

/**
 * Fila de opción de 48 px: marca de selección, nombre 16/20 y, a la derecha, un dato en mono ([trailing],
 * p. ej. "+$25"). [first] = false dibuja el filete que la separa de la fila anterior.
 * [multiple] cambia el radio por una casilla. Deshabilitada, se atenúa y no responde.
 */
@Composable
fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    multiple: Boolean = false,
    first: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizes.controlLg)
            .then(if (first) Modifier else Modifier.edgeTop(colors.line))
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = if (multiple) Role.Checkbox else Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected, multiple = multiple)
        Spacer(Modifier.width(Space.x3))
        AppText(
            text,
            modifier = Modifier.weight(1f),
            style = AppTheme.type.title.copy(fontWeight = FontWeight.Normal),
            color = if (enabled) colors.ink else colors.inkSubtle,
            maxLines = 2,
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.x3))
            AppText(trailing, style = AppTheme.type.mono, color = if (enabled) colors.ink else colors.inkSubtle, maxLines = 1)
        }
    }
}
