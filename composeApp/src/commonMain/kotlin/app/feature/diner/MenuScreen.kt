package app.feature.diner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.Menu
import app.shared.model.MenuCategory
import app.shared.model.MenuItem
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.ui.components.AddToCartButton
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.BrandMark
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.CategoryTabs
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.StatusPill
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.ThemeController
import app.ui.theme.WindowSize
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val KIND_HERO = 0
private const val KIND_SEARCH = 1
private const val KIND_TABS = 2
private const val KIND_HEADER = 3
private const val KIND_ITEMS = 4
private const val KIND_EMPTY = 5
private const val KIND_FOOTER = 6

/** Índice fijo de la fila de pestañas dentro de la lista (portada = 0, búsqueda = 1). */
private const val TABS_ROW_INDEX = 2

private class MenuRow(
    val kind: Int,
    val key: String,
    val category: MenuCategory? = null,
    val items: List<MenuItem> = emptyList(),
    val order: Int = 0,
)

private class MenuLayout(val rows: List<MenuRow>, val categories: List<MenuCategory>, val headerIndex: List<Int>)

private fun normalize(text: String): String {
    val sb = StringBuilder()
    for (ch in text.lowercase()) {
        sb.append(
            when (ch) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú', 'ü' -> 'u'; 'ñ' -> 'n'
                else -> ch
            },
        )
    }
    return sb.toString().trim()
}

private fun buildMenuLayout(menu: Menu, query: String, columns: Int): MenuLayout {
    val q = normalize(query)
    val rows = ArrayList<MenuRow>()
    rows.add(MenuRow(KIND_HERO, "hero"))
    rows.add(MenuRow(KIND_SEARCH, "search"))
    rows.add(MenuRow(KIND_TABS, "tabs"))
    val categories = ArrayList<MenuCategory>()
    val headerIndex = ArrayList<Int>()
    var order = 0
    for (category in menu.categories.sortedBy { it.sortOrder }) {
        val items = menu.items
            .filter { it.categoryId == category.id }
            .sortedBy { it.sortOrder }
            .filter { item ->
                q.isEmpty() || normalize(item.name).contains(q) || normalize(item.description).contains(q) ||
                    item.tags.any { normalize(it).contains(q) }
            }
        if (items.isEmpty()) continue
        headerIndex.add(rows.size)
        rows.add(MenuRow(KIND_HEADER, "cat-" + category.id, category = category, order = items.size))
        categories.add(category)
        items.chunked(columns).forEachIndexed { i, chunk ->
            rows.add(MenuRow(KIND_ITEMS, "row-" + category.id + "-" + i, items = chunk, order = order))
            order += chunk.size
        }
    }
    if (categories.isEmpty()) rows.add(MenuRow(KIND_EMPTY, "empty"))
    rows.add(MenuRow(KIND_FOOTER, "footer"))
    return MenuLayout(rows, categories, headerIndex)
}

/** Inicio del menú: lista con portada + (en escritorio) panel de pedido fijo a la derecha. */
@Composable
fun MenuScreen(state: DinerState, tenant: TenantPublic, listState: LazyListState, onExit: () -> Unit, onPay: () -> Unit) {
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 1180.dp).fillMaxSize()) {
            MenuPane(state, tenant, listState, onExit, Modifier.weight(1f).fillMaxHeight())
            if (expanded) CartPanel(state, tenant, onPay)
        }
        if (!expanded) {
            FloatingCartBar(state, Modifier.align(Alignment.BottomCenter)) { state.screen = DinerScreen.CART }
        }
    }
}

@Composable
private fun MenuPane(state: DinerState, tenant: TenantPublic, listState: LazyListState, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val windowSize = LocalWindowSize.current
    val compact = windowSize == WindowSize.COMPACT
    val colors = AppTheme.colors
    val isDark = colors.isDark
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val columns = when (windowSize) {
            WindowSize.COMPACT -> 1
            WindowSize.MEDIUM -> 2
            WindowSize.EXPANDED -> if (maxWidth >= 880.dp) 3 else 2
        }
        val pad = if (compact) Space.x4 else Space.x6
        val layout = remember(state.menu, state.query, columns) { buildMenuLayout(state.menu, state.query, columns) }
        val tabNames = remember(layout) { layout.categories.map { it.name } }

        val topBarPx = with(density) { TOP_BAR_HEIGHT.roundToPx() }
        val defaultTabsPx = with(density) { 52.dp.roundToPx() }
        var heroPx by remember { mutableStateOf(0) }
        var tabsPx by remember { mutableStateOf(defaultTabsPx) }
        var pendingTab by remember { mutableStateOf<Int?>(null) }

        val barSolid by remember(listState, topBarPx) {
            derivedStateOf {
                listState.firstVisibleItemIndex >= 1 ||
                    (heroPx > 0 && listState.firstVisibleItemScrollOffset > heroPx - topBarPx * 2)
            }
        }
        val tabsPinned by remember(listState, topBarPx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex >= TABS_ROW_INDEX) true
                else listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == TABS_ROW_INDEX }?.let { it.offset <= topBarPx } ?: false
            }
        }
        // Scroll-spy: la categoría cuyo encabezado ya pasó bajo la barra fija.
        val scrolledTab by remember(listState, layout, topBarPx) {
            derivedStateOf {
                val threshold = topBarPx + tabsPx + 12
                val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > threshold }?.index
                    ?: listState.firstVisibleItemIndex
                var selected = 0
                for (i in layout.headerIndex.indices) if (layout.headerIndex[i] <= anchor) selected = i
                if (!listState.canScrollForward && listState.firstVisibleItemIndex > 0 && layout.headerIndex.isNotEmpty()) {
                    selected = layout.headerIndex.size - 1
                }
                selected
            }
        }
        val selectedTab = (pendingTab ?: scrolledTab).coerceIn(0, (layout.categories.size - 1).coerceAtLeast(0))
        val goToTab: (Int) -> Unit = { index ->
            val target = layout.headerIndex.getOrNull(index)
            if (target != null) {
                pendingTab = index
                scope.launch {
                    try {
                        listState.animateScrollToItem(target, -(topBarPx + tabsPx))
                    } finally {
                        pendingTab = null
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = layout.rows.size, key = { layout.rows[it].key }, contentType = { layout.rows[it].kind }) { index ->
                val row = layout.rows[index]
                when (row.kind) {
                    KIND_HERO -> MenuHero(tenant, listState, compact, pad, Modifier.onSizeChanged { heroPx = it.height })
                    KIND_SEARCH -> AppTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = Space.x2, bottom = Space.x3),
                        placeholder = "Buscar platillos, ingredientes…",
                        leadingIcon = AppIcons.Search,
                    )
                    KIND_TABS -> if (tabNames.isNotEmpty()) {
                        CategoryTabs(
                            tabs = tabNames,
                            selectedIndex = selectedTab,
                            onSelect = goToTab,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = pad - Space.x2),
                        )
                    }
                    KIND_HEADER -> CategoryHeader(row.category, row.order, pad)
                    KIND_ITEMS -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = pad).padding(bottom = Space.x4),
                        horizontalArrangement = Arrangement.spacedBy(Space.x4),
                    ) {
                        row.items.forEachIndexed { i, item ->
                            DishCard(item, state, order = row.order + i, uniform = columns > 1, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - row.items.size) { Spacer(Modifier.weight(1f)) }
                    }
                    KIND_EMPTY -> EmptyState(
                        title = "Sin resultados",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = Space.x10),
                        message = if (state.query.isBlank()) "Este menú aún no tiene platillos publicados." else "No encontramos platillos para “${state.query.trim()}”.",
                        icon = AppIcons.Search,
                        actionText = if (state.query.isBlank()) null else "Limpiar búsqueda",
                        onAction = if (state.query.isBlank()) null else ({ state.query = "" }),
                    )
                    else -> MenuFooter(tenant, pad, bottomSpace = if (windowSize == WindowSize.EXPANDED) Space.x8 else 112.dp, onExit = onExit)
                }
            }
        }

        // Barra compacta + pestañas fijas (se superponen a la lista).
        val barAlpha by animateFloatAsState(if (barSolid) 1f else 0f, tween(Motion.BASE, easing = Motion.standard), label = "bar-alpha")
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().background(colors.canvas.copy(alpha = barAlpha)).height(TOP_BAR_HEIGHT).padding(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f).graphicsLayer { alpha = barAlpha }, verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(tenant.name, tenant.theme.logoUrl, size = 32.dp)
                    Spacer(Modifier.width(Space.x3))
                    AppText(tenant.name, style = AppTheme.type.h3, maxLines = 1)
                }
                AppIconButton(
                    icon = if (isDark) AppIcons.Sun else AppIcons.Moon,
                    onClick = { ThemeController.toggle(isDark) },
                    contentDescription = if (isDark) "Modo claro" else "Modo oscuro",
                    filled = true,
                )
                Spacer(Modifier.width(Space.x2))
                if (compact) {
                    AppIconButton(
                        icon = AppIcons.Receipt,
                        onClick = { state.screen = DinerScreen.HISTORY },
                        contentDescription = "Mis pedidos",
                        filled = true,
                        badge = state.history.size.takeIf { it > 0 },
                    )
                } else {
                    AppButton(
                        "Mis pedidos",
                        onClick = { state.screen = DinerScreen.HISTORY },
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.SM,
                        icon = AppIcons.Receipt,
                    )
                }
            }
            if (tabsPinned && tabNames.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(colors.canvas).onSizeChanged { if (it.height > 0) tabsPx = it.height }) {
                    CategoryTabs(
                        tabs = tabNames,
                        selectedIndex = selectedTab,
                        onSelect = goToTab,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad - Space.x2),
                    )
                    AppDivider()
                }
            } else if (barSolid) {
                AppDivider(Modifier.graphicsLayer { alpha = barAlpha })
            }
        }
    }
}

@Composable
private fun MenuHero(tenant: TenantPublic, listState: LazyListState, compact: Boolean, pad: Dp, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(if (compact) 288.dp else 348.dp).clipToBounds()) {
            DishImage(
                tenant.theme.coverUrl,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Parallax suave: la foto baja más lento que el contenido y crece un poco.
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                    translationY = offset * 0.42f
                    val s = 1.04f + (offset / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.10f
                    scaleX = s
                    scaleY = s
                },
                contentDescription = null,
                shape = RectangleShape,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to PhotoScrim.copy(alpha = 0.38f),
                        0.42f to PhotoScrim.copy(alpha = 0.04f),
                        1f to PhotoScrim.copy(alpha = 0.80f),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = pad, vertical = Space.x5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(tenant.name, tenant.theme.logoUrl, size = if (compact) 52.dp else 64.dp)
                Spacer(Modifier.width(Space.x4))
                Column(Modifier.weight(1f)) {
                    AppText(tenant.name, style = if (compact) AppTheme.type.h1 else AppTheme.type.display, color = OnPhoto, maxLines = 2)
                    if (tenant.tagline.isNotBlank()) {
                        AppText(tenant.tagline, style = AppTheme.type.body, color = OnPhoto.copy(alpha = 0.86f), maxLines = 2)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = Space.x4, bottom = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            val rating = ((tenant.rating * 10).roundToInt() / 10.0).toString()
            InfoStat(AppIcons.Star, rating, colors.warning)
            InfoStat(AppIcons.Clock, "${tenant.etaMinutes}–${tenant.etaMinutes + 10} min", colors.inkMuted)
            StatusPill(if (tenant.isOpen) "Abierto" else "Cerrado", tone = if (tenant.isOpen) Tone.SUCCESS else Tone.DANGER)
            if (tenant.cuisine.isNotBlank()) AppText(tenant.cuisine, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
        }
        if (!compact && tenant.description.isNotBlank()) {
            AppText(
                tenant.description,
                modifier = Modifier.padding(horizontal = pad).padding(bottom = Space.x2).widthIn(max = 640.dp),
                style = AppTheme.type.body,
                color = colors.inkMuted,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun InfoStat(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DinerIcon(icon, tint = tint, size = 18.dp)
        Spacer(Modifier.width(Space.x1))
        AppText(text, style = AppTheme.type.title, maxLines = 1)
    }
}

@Composable
private fun CategoryHeader(category: MenuCategory?, count: Int, pad: Dp) {
    if (category == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = Space.x6, bottom = Space.x4)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AppText(category.name, modifier = Modifier.weight(1f), style = AppTheme.type.h2, maxLines = 1)
            AppText(if (count == 1) "1 platillo" else "$count platillos", style = AppTheme.type.label, color = AppTheme.colors.inkSubtle)
        }
        if (category.description.isNotBlank()) {
            Spacer(Modifier.height(Space.x1))
            AppText(category.description, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 2)
        }
    }
}

/** Tarjeta grande de platillo: foto 16:10 con etiquetas, nombre, descripción, precio y botón de agregar. */
@Composable
private fun DishCard(item: MenuItem, state: DinerState, order: Int, uniform: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.lg
    val scope = rememberCoroutineScope()
    val pulse = remember { Animatable(1f) }
    val quantity = state.quantityOf(item.id)
    Column(
        modifier
            .staggerIn(order, state.loadedAt)
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .pressable(scale = 0.985f) { state.openSheet(item) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            DishImage(
                item.imageUrl,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (item.available) 1f else 0.45f },
                contentDescription = item.name,
                shape = RectangleShape,
            )
            if (item.tags.isNotEmpty()) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(Space.x3),
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                ) {
                    item.tags.take(if (uniform) 2 else 3).forEach { tag -> TagBadge(tagLabel(tag), tone = tagTone(tag)) }
                }
            }
            if (!item.available) {
                StatusPill("Agotado", modifier = Modifier.align(Alignment.Center), tone = Tone.NEUTRAL, dot = false)
            }
        }
        Column(Modifier.fillMaxWidth().padding(Space.x4).graphicsLayer { alpha = if (item.available) 1f else 0.55f }) {
            AppText(item.name, style = AppTheme.type.title, maxLines = if (uniform) 1 else 2)
            Spacer(Modifier.height(Space.x1))
            Box(if (uniform) Modifier.fillMaxWidth().height(38.dp) else Modifier.fillMaxWidth()) {
                AppText(item.description, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 2)
            }
            Spacer(Modifier.height(Space.x3))
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(formatMoney(item.priceCents), modifier = Modifier.weight(1f), style = AppTheme.type.h3, maxLines = 1)
                if (item.available) {
                    AddToCartButton(
                        quantity = quantity,
                        onAdd = {
                            if (state.requiresChoice(item)) {
                                state.openSheet(item)
                            } else {
                                state.add(item)
                                scope.launch {
                                    pulse.snapTo(0.965f)
                                    pulse.animateTo(1f, Motion.bouncy())
                                }
                            }
                        },
                        onRemove = { state.removeOne(item.id) },
                    )
                } else {
                    AppText("Agotado", style = AppTheme.type.label, color = colors.inkSubtle)
                }
            }
        }
    }
}

@Composable
private fun MenuFooter(tenant: TenantPublic, pad: Dp, bottomSpace: Dp, onExit: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = Space.x8, bottom = bottomSpace),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppDivider()
        Spacer(Modifier.height(Space.x6))
        BrandMark(tenant.name, tenant.theme.logoUrl, size = 36.dp)
        Spacer(Modifier.height(Space.x2))
        AppText(tenant.name, style = AppTheme.type.title, textAlign = TextAlign.Center)
        if (tenant.address.isNotBlank()) {
            AppText(tenant.address, style = AppTheme.type.bodySm, color = colors.inkMuted, textAlign = TextAlign.Center)
        }
        if (tenant.phone.isNotBlank()) {
            AppText(tenant.phone, style = AppTheme.type.bodySm, color = colors.inkMuted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Space.x3))
        // Salida discreta (vuelve al índice de la demo / app anfitriona).
        AppButton("Salir del menú", onClick = onExit, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
    }
}
