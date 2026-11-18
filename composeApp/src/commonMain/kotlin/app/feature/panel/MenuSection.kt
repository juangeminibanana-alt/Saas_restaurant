package app.feature.panel

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shared.model.Menu
import app.shared.model.MenuCategory
import app.shared.model.MenuItem
import app.shared.model.ModifierGroup
import app.shared.model.ModifierOption
import app.shared.util.formatMoney
import app.shared.util.newId
import app.shell.LocalRepo
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val knownTags = listOf("popular", "nuevo", "picante", "vegetariano", "vegano", "sin gluten")

/** Las etiquetas son datos, no alertas: "popular" va en tinta sólida (destacado) y el resto en ficha neutra. */
private fun tagTone(tag: String): Tone = when (tag.lowercase()) {
    "popular" -> Tone.PRIMARY
    else -> Tone.NEUTRAL
}

private fun tagLabel(tag: String): String = tag.replaceFirstChar { it.uppercase() }

@Composable
internal fun MenuSection() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { repo.getAdminMenu() }
    val size = LocalWindowSize.current

    var categoryId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var itemDialogOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    var categoryDialogOpen by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<MenuCategory?>(null) }
    var deletingItem by remember { mutableStateOf<MenuItem?>(null) }
    var deletingCategory by remember { mutableStateOf<MenuCategory?>(null) }

    fun mutate(fallback: String, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toasts.show(errorText(e, fallback), Tone.DANGER)
            } finally {
                busy = false
            }
        }
    }

    fun replaceItem(saved: MenuItem) {
        val m = loader.value ?: return
        val exists = m.items.any { it.id == saved.id }
        loader.set(m.copy(items = if (exists) m.items.map { if (it.id == saved.id) saved else it } else m.items + saved))
    }

    fun toggleAvailability(item: MenuItem, available: Boolean) {
        replaceItem(item.copy(available = available)) // optimista
        scope.launch {
            try {
                replaceItem(repo.setItemAvailability(item.id, available))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                replaceItem(item)
                Toasts.show(errorText(e, "No pudimos cambiar la disponibilidad"), Tone.DANGER)
            }
        }
    }

    val openNewItem: () -> Unit = { editingItem = null; itemDialogOpen = true }
    val menu = loader.value
    val subtitle = if (menu == null) "Categorías, platillos, precios y disponibilidad"
    else "${menu.items.size} platillos en ${menu.categories.size} categorías"

    PanelPage {
        if (size == WindowSize.COMPACT) {
            PageHeader("Menú", subtitle = subtitle)
            AppButton("Nuevo platillo", onClick = openNewItem, icon = AppIcons.Add, fullWidth = true, enabled = menu != null)
        } else {
            PageHeader("Menú", subtitle = subtitle) {
                AppButton("Nueva categoría", onClick = { editingCategory = null; categoryDialogOpen = true }, variant = ButtonVariant.SECONDARY, icon = AppIcons.Tag, enabled = menu != null)
                AppButton("Nuevo platillo", onClick = openNewItem, icon = AppIcons.Add, enabled = menu != null)
            }
        }

        when (val state = loader.state) {
            Load.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) { repeat(4) { Skeleton(Modifier.width(96.dp).height(36.dp), shape = AppTheme.shapes.sm) } }
                SkeletonRows(rows = 6)
            }
            is Load.Failed -> ErrorState(state.message, onRetry = { loader.reload() })
            is Load.Ready -> {
                val current = state.value
                val categories = current.categories.sortedBy { it.sortOrder }
                val selectedCategory = categories.firstOrNull { it.id == categoryId }
                val categoryNames = categories.associate { it.id to it.name }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                    AppChip("Todos · ${current.items.size}", selected = selectedCategory == null, onClick = { categoryId = null })
                    categories.forEach { cat ->
                        val count = current.items.count { it.categoryId == cat.id }
                        AppChip("${cat.name} · $count", selected = selectedCategory?.id == cat.id, onClick = { categoryId = cat.id })
                    }
                    AppChip("Nueva categoría", onClick = { editingCategory = null; categoryDialogOpen = true }, icon = AppIcons.Add)
                }

                if (selectedCategory != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Column(Modifier.weight(1f)) {
                            AppText(selectedCategory.name, style = AppTheme.type.h2, maxLines = 1)
                            if (selectedCategory.description.isNotBlank()) AppText(selectedCategory.description, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 2)
                        }
                        AppButton("Renombrar", onClick = { editingCategory = selectedCategory; categoryDialogOpen = true }, variant = ButtonVariant.GHOST, size = ButtonSize.SM, icon = AppIcons.Edit)
                        AppButton("Eliminar", onClick = { deletingCategory = selectedCategory }, variant = ButtonVariant.GHOST, size = ButtonSize.SM, icon = AppIcons.Delete)
                    }
                }

                AppTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Buscar platillo por nombre o etiqueta", leadingIcon = AppIcons.Search)

                val q = query.trim().lowercase()
                val items = current.items
                    .filter { selectedCategory == null || it.categoryId == selectedCategory.id }
                    .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) } }
                    .sortedBy { it.sortOrder }

                if (items.isEmpty()) {
                    AppCard(Modifier.fillMaxWidth()) {
                        if (q.isNotEmpty()) {
                            EmptyState("Sin resultados", message = "No encontramos platillos con “${query.trim()}”.", icon = AppIcons.Search)
                        } else {
                            EmptyState(
                                "Aún no hay platillos aquí",
                                message = "Agrega tu primer platillo con foto, precio y extras. Tus clientes lo verán al instante.",
                                icon = AppIcons.Menu, actionText = "Nuevo platillo", onAction = openNewItem,
                            )
                        }
                    }
                } else if (size == WindowSize.EXPANDED) {
                    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                            ColumnLabel("Platillo", Modifier.weight(2.6f))
                            ColumnLabel("Categoría", Modifier.weight(1f))
                            ColumnLabel("Etiquetas", Modifier.weight(1.4f))
                            ColumnLabel("Precio", Modifier.width(88.dp), end = true)
                            ColumnLabel("Disponible", Modifier.width(196.dp))
                        }
                        items.forEach { item ->
                            AppDivider()
                            ItemTableRow(
                                item = item, categoryName = categoryNames[item.categoryId] ?: "Sin categoría",
                                onAvailability = { toggleAvailability(item, it) },
                                onEdit = { editingItem = item; itemDialogOpen = true },
                                onDelete = { deletingItem = item },
                            )
                        }
                    }
                } else {
                    SimpleGrid(items = items, columns = if (size == WindowSize.MEDIUM) 2 else 1, spacing = Space.x3) { item ->
                        ItemCard(
                            item = item, categoryName = categoryNames[item.categoryId] ?: "Sin categoría",
                            onAvailability = { toggleAvailability(item, it) },
                            onEdit = { editingItem = item; itemDialogOpen = true },
                            onDelete = { deletingItem = item },
                        )
                    }
                }
            }
        }
    }

    // ── Diálogos
    if (itemDialogOpen && menu != null) {
        ItemDialog(
            initial = editingItem, menu = menu, defaultCategoryId = categoryId, saving = busy,
            onDismiss = { itemDialogOpen = false },
            onSave = { item ->
                mutate("No pudimos guardar el platillo") {
                    replaceItem(repo.upsertItem(item))
                    itemDialogOpen = false
                    Toasts.show(if (editingItem == null) "Platillo agregado a tu menú" else "Platillo actualizado", Tone.SUCCESS)
                }
            },
        )
    }
    if (categoryDialogOpen && menu != null) {
        CategoryDialog(
            initial = editingCategory, saving = busy,
            onDismiss = { categoryDialogOpen = false },
            onSave = { name, description ->
                mutate("No pudimos guardar la categoría") {
                    val base = editingCategory ?: MenuCategory(id = newId("cat"), name = name, sortOrder = (menu.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1)
                    val saved = repo.upsertCategory(base.copy(name = name, description = description))
                    val m = loader.value ?: menu
                    val exists = m.categories.any { it.id == saved.id }
                    loader.set(m.copy(categories = if (exists) m.categories.map { if (it.id == saved.id) saved else it } else m.categories + saved))
                    categoryId = saved.id
                    categoryDialogOpen = false
                    Toasts.show("Categoría guardada", Tone.SUCCESS)
                }
            },
        )
    }
    deletingItem?.let { item ->
        ConfirmDialog(
            title = "Eliminar platillo",
            message = "“${item.name}” dejará de aparecer en tu menú. Esta acción no se puede deshacer.",
            confirmText = "Eliminar", loading = busy,
            onDismiss = { deletingItem = null },
            onConfirm = {
                mutate("No pudimos eliminar el platillo") {
                    repo.deleteItem(item.id)
                    loader.value?.let { m -> loader.set(m.copy(items = m.items.filter { it.id != item.id })) }
                    deletingItem = null
                    Toasts.show("Platillo eliminado", Tone.NEUTRAL)
                }
            },
        )
    }
    deletingCategory?.let { cat ->
        val count = menu?.items?.count { it.categoryId == cat.id } ?: 0
        ConfirmDialog(
            title = "Eliminar categoría",
            message = if (count > 0) "“${cat.name}” tiene $count platillos que dejarán de mostrarse en tu menú. Esta acción no se puede deshacer."
            else "“${cat.name}” se quitará de tu menú. Esta acción no se puede deshacer.",
            confirmText = "Eliminar", loading = busy,
            onDismiss = { deletingCategory = null },
            onConfirm = {
                mutate("No pudimos eliminar la categoría") {
                    repo.deleteCategory(cat.id)
                    deletingCategory = null
                    categoryId = null
                    loader.set(repo.getAdminMenu())
                    Toasts.show("Categoría eliminada", Tone.NEUTRAL)
                }
            },
        )
    }
}

// ───────────────────────── Filas y tarjetas ─────────────────────────

@Composable
private fun TagRow(tags: List<String>, modifier: Modifier = Modifier, max: Int = 3) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
        tags.take(max).forEach { TagBadge(tagLabel(it), tone = tagTone(it)) }
        if (tags.size > max) AppText("+${tags.size - max}", style = AppTheme.type.mono, color = AppTheme.colors.inkSubtle)
    }
}

@Composable
private fun ItemTableRow(item: MenuItem, categoryName: String, onAvailability: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dim = if (item.available) 1f else 0.55f
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
        Row(Modifier.weight(2.6f).alpha(dim), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            DishImage(url = item.imageUrl, modifier = Modifier.size(Sizes.controlXl), shape = AppTheme.shapes.sm)
            Column(Modifier.weight(1f)) {
                AppText(item.name, style = AppTheme.type.title, maxLines = 1)
                AppText(item.description.ifBlank { "Sin descripción" }, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
            }
        }
        AppText(categoryName, Modifier.weight(1f).alpha(dim), style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
        TagRow(item.tags, Modifier.weight(1.4f).alpha(dim), max = 2)
        AppText(formatMoney(item.priceCents), Modifier.width(88.dp).alpha(dim), style = AppTheme.type.mono, textAlign = TextAlign.End, maxLines = 1)
        Row(Modifier.width(196.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            AppSwitch(checked = item.available, onCheckedChange = onAvailability)
            Spacer(Modifier.weight(1f))
            AppIconButton(AppIcons.Edit, onClick = onEdit, contentDescription = "Editar ${item.name}", tint = AppTheme.colors.inkMuted, plain = true)
            AppIconButton(AppIcons.Delete, onClick = onDelete, contentDescription = "Eliminar ${item.name}", tint = AppTheme.colors.inkMuted, plain = true)
        }
    }
}

@Composable
private fun ItemCard(item: MenuItem, categoryName: String, onAvailability: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(Modifier.alpha(if (item.available) 1f else 0.55f), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                DishImage(url = item.imageUrl, modifier = Modifier.size(Sizes.thumb), shape = AppTheme.shapes.md)
                Column(Modifier.weight(1f).heightIn(min = Sizes.thumb)) {
                    LabelText(categoryName)
                    AppText(item.name, style = AppTheme.type.title, maxLines = 1)
                    if (item.description.isNotBlank()) AppText(item.description, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    AppText(formatMoney(item.priceCents), style = AppTheme.type.mono, maxLines = 1)
                }
            }
            if (item.tags.isNotEmpty()) TagRow(item.tags)
            AppDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppSwitch(checked = item.available, onCheckedChange = onAvailability)
                AppText(if (item.available) "Disponible" else "Agotado", Modifier.weight(1f), style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
                AppIconButton(AppIcons.Edit, onClick = onEdit, contentDescription = "Editar ${item.name}")
                AppIconButton(AppIcons.Delete, onClick = onDelete, contentDescription = "Eliminar ${item.name}", tint = AppTheme.colors.inkMuted)
            }
        }
    }
}

// ───────────────────────── Diálogo de categoría ─────────────────────────

@Composable
private fun CategoryDialog(initial: MenuCategory?, saving: Boolean, onDismiss: () -> Unit, onSave: (name: String, description: String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var tried by remember { mutableStateOf(false) }
    AppDialog(onDismiss = onDismiss, title = if (initial == null) "Nueva categoría" else "Renombrar categoría", maxWidth = 460.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            AppTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "Nombre", placeholder = "Entradas, Tacos, Bebidas…", error = if (tried && name.isBlank()) "Escribe un nombre" else null)
            AppTextField(description, { description = it }, Modifier.fillMaxWidth(), label = "Descripción (opcional)", placeholder = "Una línea que antoje")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2, Alignment.End)) {
                AppButton("Cancelar", onClick = onDismiss, variant = ButtonVariant.GHOST)
                AppButton("Guardar", onClick = { tried = true; if (name.isNotBlank()) onSave(name.trim(), description.trim()) }, loading = saving)
            }
        }
    }
}

// ───────────────────────── Diálogo de platillo ─────────────────────────

@Stable
private class OptionDraft(val id: String, name: String, price: String) {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
}

@Stable
private class GroupDraft(val id: String, name: String, required: Boolean, val minSelect: Int, val maxSelect: Int, initialOptions: List<OptionDraft>) {
    var name by mutableStateOf(name)
    var required by mutableStateOf(required)
    val options: SnapshotStateList<OptionDraft> = mutableStateListOf<OptionDraft>().apply { addAll(initialOptions) }

    fun toGroup(): ModifierGroup? {
        val opts = options.filter { it.name.isNotBlank() }.map { ModifierOption(it.id, it.name.trim(), parsePesosToCents(it.price) ?: 0L) }
        if (name.isBlank() && opts.isEmpty()) return null
        return ModifierGroup(
            id = id,
            name = name.trim().ifBlank { "Extras" },
            minSelect = if (required) maxOf(1, minSelect) else 0,
            maxSelect = maxOf(1, maxSelect),
            options = opts,
        )
    }
}

private fun ModifierGroup.toDraft(): GroupDraft = GroupDraft(
    id = id, name = name, required = minSelect > 0, minSelect = minSelect, maxSelect = maxSelect,
    initialOptions = options.map { OptionDraft(it.id, it.name, if (it.priceDeltaCents == 0L) "" else centsToPesosText(it.priceDeltaCents)) },
)

@Composable
private fun ItemDialog(initial: MenuItem?, menu: Menu, defaultCategoryId: String?, saving: Boolean, onDismiss: () -> Unit, onSave: (MenuItem) -> Unit) {
    val categories = remember(menu) { menu.categories.sortedBy { it.sortOrder } }
    val demoPhotos = remember(menu) { menu.items.mapNotNull { it.imageUrl }.filter { it.startsWith("res://demo/") }.distinct() }
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var priceText by remember { mutableStateOf(initial?.let { centsToPesosText(it.priceCents) }.orEmpty()) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: defaultCategoryId ?: categories.firstOrNull()?.id) }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl.orEmpty()) }
    var tags by remember { mutableStateOf(initial?.tags.orEmpty().toSet()) }
    var prepText by remember { mutableStateOf((initial?.prepMinutes ?: 15).toString()) }
    val groups = remember { mutableStateListOf<GroupDraft>().apply { addAll(initial?.modifierGroups.orEmpty().map { it.toDraft() }) } }
    var tried by remember { mutableStateOf(false) }

    val cents = parsePesosToCents(priceText)
    val prep = prepText.trim().toIntOrNull()?.takeIf { it in 1..240 }
    val nameError = if (tried && name.isBlank()) "Escribe el nombre del platillo" else null
    val priceError = if (tried && (cents == null || cents <= 0L)) "Escribe un precio válido en pesos" else null
    val prepError = if (tried && prep == null) "Entre 1 y 240 min" else null

    val submit: () -> Unit = {
        tried = true
        val cat = categoryId
        if (name.isNotBlank() && cents != null && cents > 0L && prep != null && cat != null) {
            val base = initial ?: MenuItem(
                id = newId("itm"), categoryId = cat, name = name.trim(), priceCents = cents,
                sortOrder = (menu.items.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
            onSave(
                base.copy(
                    categoryId = cat,
                    name = name.trim(),
                    description = description.trim(),
                    priceCents = cents,
                    imageUrl = imageUrl.trim().ifBlank { null },
                    tags = knownTags.filter { it in tags } + tags.filter { it !in knownTags },
                    prepMinutes = prep,
                    modifierGroups = groups.mapNotNull { it.toGroup() },
                ),
            )
        }
    }

    AppDialog(onDismiss = onDismiss, title = if (initial == null) "Nuevo platillo" else "Editar platillo", maxWidth = 680.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = if (compact) 440.dp else 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            AppTextField(name, { name = it }, Modifier.fillMaxWidth(), label = "Nombre", placeholder = "Tacos de arrachera", error = nameError)
            AppTextField(description, { description = it }, Modifier.fillMaxWidth(), label = "Descripción", placeholder = "Ingredientes y lo que lo hace especial", singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                AppTextField(priceText, { priceText = it }, Modifier.weight(1f), label = "Precio (pesos)", placeholder = "185", leadingIcon = AppIcons.Cash, error = priceError, keyboardType = KeyboardType.Decimal)
                AppTextField(prepText, { prepText = it }, Modifier.weight(1f), label = "Preparación (min)", placeholder = "15", leadingIcon = AppIcons.Clock, error = prepError, keyboardType = KeyboardType.Number)
            }

            FieldLabel("Categoría")
            if (categories.isEmpty()) {
                PanelNotice("Primero crea una categoría para poder guardar el platillo.", tone = Tone.DANGER)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    categories.forEach { cat -> AppChip(cat.name, selected = categoryId == cat.id, onClick = { categoryId = cat.id }) }
                }
            }

            FieldLabel("Foto", hint = "Pega la URL de una imagen o elige una de las fotos de tu menú")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                DishImage(url = imageUrl.trim().ifBlank { null }, modifier = Modifier.size(64.dp), shape = AppTheme.shapes.sm)
                AppTextField(imageUrl, { imageUrl = it }, Modifier.weight(1f), placeholder = "https://… o res://demo/archivo.jpg", leadingIcon = AppIcons.Image, keyboardType = KeyboardType.Uri)
            }
            if (demoPhotos.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    demoPhotos.forEach { photo ->
                        val selected = imageUrl.trim() == photo
                        val shape = AppTheme.shapes.sm
                        // Elegida = filete en tinta (la selección no usa la brasa).
                        Box(
                            Modifier.size(64.dp).clip(shape)
                                .border(if (selected) 2.dp else 1.dp, if (selected) AppTheme.colors.select else AppTheme.colors.line, shape)
                                .pressable(onClick = { imageUrl = photo })
                                .padding(Space.x1),
                        ) {
                            DishImage(url = photo, modifier = Modifier.fillMaxSize(), shape = AppTheme.shapes.xs, ring = false)
                        }
                    }
                }
            }

            FieldLabel("Etiquetas")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                (knownTags + tags.filter { it !in knownTags }).forEach { tag ->
                    AppChip(tagLabel(tag), selected = tag in tags, onClick = { tags = if (tag in tags) tags - tag else tags + tag })
                }
            }

            AppDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Extras y opciones", Modifier.weight(1f), hint = "Términos, tamaños, ingredientes extra…")
                AppButton(
                    "Agregar grupo",
                    onClick = { groups.add(GroupDraft(newId("grp"), "", false, 0, 1, listOf(OptionDraft(newId("opt"), "", "")))) },
                    variant = ButtonVariant.SECONDARY, size = ButtonSize.SM, icon = AppIcons.Add,
                )
            }
            groups.forEach { group ->
                key(group.id) { GroupEditor(group = group, onRemove = { groups.remove(group) }) }
            }
        }
        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2, Alignment.End)) {
            AppButton("Cancelar", onClick = onDismiss, variant = ButtonVariant.GHOST)
            AppButton(if (initial == null) "Agregar platillo" else "Guardar cambios", onClick = submit, loading = saving, enabled = categories.isNotEmpty())
        }
    }
}

@Composable
private fun GroupEditor(group: GroupDraft, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, AppTheme.colors.line, AppTheme.shapes.md).padding(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            AppTextField(group.name, { group.name = it }, Modifier.weight(1f), placeholder = "Nombre del grupo (ej. Término)")
            AppIconButton(AppIcons.Delete, onClick = onRemove, contentDescription = "Quitar grupo", tint = AppTheme.colors.inkMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            AppSwitch(checked = group.required, onCheckedChange = { group.required = it })
            AppText(if (group.required) "Obligatorio: el cliente debe elegir" else "Opcional", style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
        }
        group.options.forEach { option ->
            key(option.id) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    AppTextField(option.name, { option.name = it }, Modifier.weight(1f), placeholder = "Opción")
                    AppTextField(option.price, { option.price = it }, Modifier.width(112.dp), placeholder = "+$0", keyboardType = KeyboardType.Decimal)
                    AppIconButton(AppIcons.Close, onClick = { group.options.remove(option) }, contentDescription = "Quitar opción")
                }
            }
        }
        AppButton("Agregar opción", onClick = { group.options.add(OptionDraft(newId("opt"), "", "")) }, variant = ButtonVariant.GHOST, size = ButtonSize.SM, icon = AppIcons.Add)
    }
}
