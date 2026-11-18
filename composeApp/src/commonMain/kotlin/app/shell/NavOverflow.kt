package app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.ui.components.AppBottomSheet
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.CountChip
import app.ui.components.LabelText
import app.ui.components.NavItem
import app.ui.components.OptionList
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter

/** Identificador de la entrada "Más" de la navegación inferior. */
internal const val MoreNavId = "__mas"

/** Destinos que caben en la barra inferior de un teléfono sin recortar ninguno. */
private const val MaxBottomTabs = 5

/**
 * Navegación inferior sin recortes para los paneles. En teléfono, si hay más de cinco destinos, los cuatro primeros
 * van en la barra y el quinto es "Más": abre una hoja con el resto (su conteo suma los conteos que guarda).
 * En pantallas anchas pasa todo tal cual. Debe envolver al `AdminScaffold`: la hoja se dibuja encima de él.
 *
 * [content] recibe las entradas, la entrada activa y el manejador ya resueltos para la barra.
 */
@Composable
internal fun NavOverflowHost(
    navItems: List<NavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    content: @Composable (items: List<NavItem>, selectedId: String, onSelect: (String) -> Unit) -> Unit,
) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    if (!compact || navItems.size <= MaxBottomTabs) {
        content(navItems, selectedId, onSelect)
        return
    }
    var open by remember { mutableStateOf(false) }
    val shown = navItems.take(MaxBottomTabs - 1)
    val hidden = navItems.drop(MaxBottomTabs - 1)
    val hiddenBadge = hidden.sumOf { it.badge ?: 0 }.takeIf { it > 0 }
    val more = NavItem(id = MoreNavId, label = "Más", icon = AppIcons.MoreVert, badge = hiddenBadge)
    val activeId = if (hidden.any { it.id == selectedId }) MoreNavId else selectedId

    Box(Modifier.fillMaxSize()) {
        content(shown + more, activeId) { id -> if (id == MoreNavId) open = true else onSelect(id) }
        AppBottomSheet(visible = open, onDismiss = { open = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, bottom = Space.x4),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                LabelText("Más secciones")
                OptionList {
                    hidden.forEachIndexed { index, item ->
                        OverflowRow(
                            item = item,
                            selected = item.id == selectedId,
                            first = index == 0,
                            onClick = {
                                open = false
                                onSelect(item.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Fila de la hoja "Más": ícono en cuadro, nombre y, a la derecha, el conteo o la marca de la sección activa. */
@Composable
private fun OverflowRow(item: NavItem, selected: Boolean, first: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.edgeTop(colors.line))
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .pressable(scale = 0.995f, onClick = onClick)
            .height(Sizes.controlXl)
            .padding(horizontal = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Sizes.controlSm).flatSurface(if (selected) colors.select else AppTheme.controlFill, colors.line, AppTheme.shapes.sm),
            contentAlignment = Alignment.Center,
        ) {
            OverflowIcon(item.icon, if (selected) colors.onSelect else colors.ink)
        }
        Spacer(Modifier.width(Space.x3))
        AppText(item.label, Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
        val badge = item.badge ?: 0
        if (badge > 0) {
            CountChip(badge)
            Spacer(Modifier.width(Space.x2))
        }
        OverflowIcon(if (selected) AppIcons.Check else AppIcons.ChevronRight, if (selected) colors.ink else colors.inkSubtle)
    }
}

@Composable
private fun OverflowIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Image(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon), colorFilter = ColorFilter.tint(tint))
}
