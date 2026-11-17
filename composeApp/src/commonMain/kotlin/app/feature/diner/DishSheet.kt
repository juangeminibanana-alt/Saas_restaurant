package app.feature.diner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.MenuItem
import app.shared.model.ModifierGroup
import app.shared.model.ModifierOption
import app.shared.util.formatMoney
import app.ui.components.AppBottomSheet
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.DishImage
import app.ui.components.QuantityStepper
import app.ui.components.TagBadge
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.Space

/** Detalle del platillo en hoja inferior (diálogo centrado en escritorio). */
@Composable
fun DishSheet(state: DinerState, viewportHeight: Dp) {
    AppBottomSheet(visible = state.sheetVisible, onDismiss = { state.closeSheet() }) {
        val item = state.sheetItem
        if (item != null) {
            key(item.id, state.sheetSession) { DishSheetContent(item, state, viewportHeight) }
        }
    }
}

@Composable
private fun DishSheetContent(item: MenuItem, state: DinerState, viewportHeight: Dp) {
    val colors = AppTheme.colors
    val selected = remember { mutableStateMapOf<String, List<String>>() }
    var quantity by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val chosen: List<ModifierOption> = item.modifierGroups.flatMap { g ->
        val ids = selected[g.id] ?: emptyList()
        g.options.filter { it.id in ids }
    }
    val unitCents = item.priceCents + chosen.sumOf { it.priceDeltaCents }
    val totalCents = unitCents * quantity
    val missing = item.modifierGroups.filter { (selected[it.id]?.size ?: 0) < it.minSelect }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (viewportHeight * 0.70f).coerceAtLeast(240.dp)).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                DishImage(item.imageUrl, modifier = Modifier.fillMaxSize(), contentDescription = item.name, shape = RectangleShape)
                AppIconButton(
                    AppIcons.Close,
                    onClick = { state.closeSheet() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(Space.x3),
                    contentDescription = "Cerrar",
                    filled = true,
                )
                if (item.tags.isNotEmpty()) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(Space.x3),
                        horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    ) {
                        item.tags.take(4).forEach { tag -> TagBadge(tagLabel(tag), tone = tagTone(tag)) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.x5).padding(top = Space.x5, bottom = Space.x4)) {
                AppText(item.name, style = AppTheme.type.h1)
                Spacer(Modifier.height(Space.x2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(formatMoney(item.priceCents), style = AppTheme.type.h3)
                    Spacer(Modifier.width(Space.x3))
                    DinerIcon(AppIcons.Clock, tint = colors.inkSubtle, size = 16.dp)
                    Spacer(Modifier.width(Space.x1))
                    AppText("${item.prepMinutes} min", style = AppTheme.type.bodySm, color = colors.inkMuted)
                    if (!item.available) {
                        Spacer(Modifier.width(Space.x3))
                        TagBadge("Agotado", tone = Tone.NEUTRAL)
                    }
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(Space.x3))
                    AppText(item.description, style = AppTheme.type.body, color = colors.inkMuted)
                }
                item.modifierGroups.forEach { group ->
                    Spacer(Modifier.height(Space.x5))
                    AppDivider()
                    Spacer(Modifier.height(Space.x4))
                    ModifierGroupSection(
                        group = group,
                        selected = selected[group.id] ?: emptyList(),
                        error = showErrors && group in missing,
                        onToggle = { option ->
                            val current = selected[group.id] ?: emptyList()
                            selected[group.id] = when {
                                group.maxSelect <= 1 -> if (option.id in current && group.minSelect == 0) emptyList() else listOf(option.id)
                                option.id in current -> current - option.id
                                current.size < group.maxSelect -> current + option.id
                                else -> current
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Space.x5))
                AppTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 140) notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Notas para cocina",
                    placeholder = "Sin cebolla, salsa aparte…",
                    singleLine = false,
                    minLines = 2,
                )
            }
        }
        AppDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            QuantityStepper(quantity = quantity, onChange = { quantity = it.coerceIn(1, 20) }, min = 1, max = 20)
            PriceButton(
                label = if (item.available) "Agregar" else "Agotado",
                cents = totalCents,
                enabled = item.available,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (missing.isNotEmpty()) {
                        showErrors = true
                        Toasts.show("Elige una opción en: " + missing.joinToString(", ") { it.name }, Tone.WARNING)
                    } else {
                        state.add(item, quantity, chosen.map { it.id }, notes.trim())
                        state.closeSheet()
                    }
                },
            )
        }
    }
}

private fun groupHint(group: ModifierGroup): String = when {
    group.minSelect > 0 && group.maxSelect <= 1 -> "Obligatorio · elige 1"
    group.minSelect > 0 && group.minSelect == group.maxSelect -> "Obligatorio · elige ${group.minSelect}"
    group.minSelect > 0 -> "Obligatorio · elige de ${group.minSelect} a ${group.maxSelect}"
    else -> "Opcional · hasta ${group.maxSelect.coerceAtLeast(1)}"
}

@Composable
private fun ModifierGroupSection(group: ModifierGroup, selected: List<String>, error: Boolean, onToggle: (ModifierOption) -> Unit) {
    val colors = AppTheme.colors
    val single = group.maxSelect <= 1
    val satisfied = selected.size >= group.minSelect
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(group.name, style = AppTheme.type.title)
                AppText(
                    if (error) "Elige una opción para continuar" else groupHint(group),
                    style = AppTheme.type.bodySm,
                    color = if (error) colors.danger else colors.inkMuted,
                )
            }
            if (group.minSelect > 0) {
                TagBadge(
                    if (satisfied) "Listo" else "Obligatorio",
                    tone = when {
                        error -> Tone.DANGER
                        satisfied -> Tone.SUCCESS
                        else -> Tone.WARNING
                    },
                )
            }
        }
        Spacer(Modifier.height(Space.x2))
        group.options.forEach { option ->
            val checked = option.id in selected
            val enabled = single || checked || selected.size < group.maxSelect
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                    .pressable(enabled = enabled, scale = 0.99f) { onToggle(option) }
                    .padding(vertical = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionMark(checked = checked, round = single)
                Spacer(Modifier.width(Space.x3))
                AppText(option.name, modifier = Modifier.weight(1f), style = AppTheme.type.body)
                if (option.priceDeltaCents != 0L) {
                    AppText(
                        (if (option.priceDeltaCents > 0) "+" else "") + formatMoney(option.priceDeltaCents),
                        style = AppTheme.type.bodySm,
                        color = colors.inkMuted,
                    )
                }
            }
        }
    }
}
