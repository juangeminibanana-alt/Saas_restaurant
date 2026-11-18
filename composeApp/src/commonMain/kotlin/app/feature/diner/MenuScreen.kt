package app.feature.diner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.Menu
import app.shared.model.MenuItem
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.nowMillis
import app.ui.components.AddToCartButton
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BottomBar
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.LabelText
import app.ui.components.StatusDot
import app.ui.components.edgeBottom
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.photoScrim
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val KIND_COVER = 0
private const val KIND_NAV = 1
private const val KIND_HEAD = 2
private const val KIND_BENTO = 3
private const val KIND_ITEMS = 4
private const val KIND_EMPTY = 5
private const val KIND_FOOTER = 6

/** Índice fijo de la fila pegada (buscar + categorías) dentro de la lista: la portada es el 0. */
private const val NAV_INDEX = 1

/** Lo que el resto de la primera pantalla necesita bajo la portada: fila pegada, encabezado, mosaico grande y barra. */
private val CoverReserve = 528.dp
private val CoverMin = 240.dp
private val CoverMax = 316.dp

/** Medidas del bento de la maqueta. */
private val TileMediaLarge = 240.dp
private val TileMediaSmall = 132.dp
private val BentoWideHeight = 372.dp

/** Compensación óptica del nombre a 40 px: el asta de la primera letra cae en el margen (−0.075em). */
private val CoverNameInset = 3.dp

/** Ancho mínimo del panel del menú para acomodar los platillos en dos columnas. */
private val TwoColumnMin = 720.dp

private class MenuRow(
    val kind: Int,
    val key: String,
    val title: String = "",
    val label: String = "",
    val items: List<MenuItem> = emptyList(),
    /** Encabezado de una sección que no es la primera: lleva 32 de aire arriba. */
    val topGap: Boolean = false,
    val firstLine: Boolean = false,
    val lastLine: Boolean = false,
)

private class MenuLayout(val rows: List<MenuRow>, val tabs: List<String>, val headIndex: List<Int>)

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

private fun MenuItem.isPopular(): Boolean = tags.any { it.equals("popular", ignoreCase = true) }

/**
 * "Esta semana": el platillo insignia (el popular de mayor precio) en grande y dos populares más en chico,
 * de categorías distintas cuando se puede. Con dos populares van los dos en chico; con uno, sólo el grande.
 */
private fun featuredItems(menu: Menu): List<MenuItem> {
    val order = menu.categories.sortedBy { it.sortOrder }.map { it.id }
    val popular = menu.items
        .filter { it.available && it.isPopular() }
        .sortedWith(compareBy({ order.indexOf(it.categoryId).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.sortOrder }))
    if (popular.size < 3) return popular
    val lead = popular.maxByOrNull { it.priceCents } ?: return emptyList()
    val rest = popular.filter { it.id != lead.id }
    val picks = ArrayList<MenuItem>()
    for (candidate in rest) {
        if (picks.size < 2 && candidate.categoryId != lead.categoryId && picks.none { it.categoryId == candidate.categoryId }) picks.add(candidate)
    }
    for (candidate in rest) {
        if (picks.size < 2 && picks.none { it.id == candidate.id }) picks.add(candidate)
    }
    return listOf(lead) + picks
}

private fun buildMenuLayout(menu: Menu, query: String, columns: Int): MenuLayout {
    val q = normalize(query)
    val rows = ArrayList<MenuRow>()
    rows.add(MenuRow(KIND_COVER, "cover"))
    rows.add(MenuRow(KIND_NAV, "nav"))
    val tabs = ArrayList<String>()
    val headIndex = ArrayList<Int>()

    val featured = if (q.isEmpty()) featuredItems(menu) else emptyList()
    if (featured.isNotEmpty()) {
        headIndex.add(rows.size)
        tabs.add("Destacados")
        rows.add(MenuRow(KIND_HEAD, "head-featured", title = "Esta semana", label = "Selección de la casa"))
        rows.add(MenuRow(KIND_BENTO, "bento", items = featured))
    }
    for (category in menu.categories.sortedBy { it.sortOrder }) {
        val items = menu.items
            .filter { it.categoryId == category.id }
            .sortedBy { it.sortOrder }
            .filter { item ->
                q.isEmpty() || normalize(item.name).contains(q) || normalize(item.description).contains(q) ||
                    item.tags.any { normalize(it).contains(q) }
            }
        if (items.isEmpty()) continue
        val gap = headIndex.isNotEmpty()
        headIndex.add(rows.size)
        tabs.add(category.name)
        rows.add(
            MenuRow(
                KIND_HEAD, "head-" + category.id,
                title = category.name,
                label = category.description.ifBlank { dishCount(items.size) },
                topGap = gap,
            ),
        )
        val lines = items.chunked(columns)
        lines.forEachIndexed { i, line ->
            rows.add(MenuRow(KIND_ITEMS, "row-" + category.id + "-" + i, items = line, firstLine = i == 0, lastLine = i == lines.lastIndex))
        }
    }
    if (tabs.isEmpty()) rows.add(MenuRow(KIND_EMPTY, "empty"))
    rows.add(MenuRow(KIND_FOOTER, "footer"))
    return MenuLayout(rows, tabs, headIndex)
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
            MenuOrderBar(state, Modifier.align(Alignment.BottomCenter)) { state.screen = DinerScreen.CART }
        }
    }
}

@Composable
private fun MenuPane(state: DinerState, tenant: TenantPublic, listState: LazyListState, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val windowSize = LocalWindowSize.current
    val compact = windowSize == WindowSize.COMPACT
    val expanded = windowSize == WindowSize.EXPANDED
    val gutter = dinerGutter(compact)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= TwoColumnMin) 2 else 1
        val paneHeight = maxHeight
        val coverHeight = (paneHeight - CoverReserve).coerceIn(CoverMin, CoverMax)
        val layout = remember(state.menu, state.query, columns) { buildMenuLayout(state.menu, state.query, columns) }
        var searching by remember { mutableStateOf(state.query.isNotBlank()) }
        var pendingTab by remember { mutableStateOf<Int?>(null) }

        val navPx = with(density) { Sizes.bar.roundToPx() }
        val sectionGapPx = with(density) { Space.x8.roundToPx() }
        val spyLeadPx = with(density) { Space.x2.roundToPx() }

        // La fila de buscar + categorías ya está pegada arriba: gana su filete.
        val stuck by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex >= NAV_INDEX } }
        // El segmento activo sigue a la sección que se está leyendo: la última cuyo inicio ya pasó bajo la fila pegada.
        val scrolledTab by remember(listState, layout, navPx) {
            derivedStateOf {
                val heads = layout.headIndex
                val first = listState.firstVisibleItemIndex
                if (heads.isEmpty() || (first == 0 && listState.firstVisibleItemScrollOffset == 0)) {
                    0
                } else {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val line = navPx + spyLeadPx
                    var selected = 0
                    heads.forEachIndexed { k, headIndex ->
                        val item = visible.firstOrNull { it.index == headIndex }
                        val top = when {
                            item != null -> item.offset + if (layout.rows[headIndex].topGap) sectionGapPx else 0
                            headIndex < first -> Int.MIN_VALUE
                            else -> Int.MAX_VALUE
                        }
                        if (top <= line) selected = k
                    }
                    if (!listState.canScrollForward) selected = heads.lastIndex
                    selected
                }
            }
        }
        val selectedTab = (pendingTab ?: scrolledTab).coerceIn(0, (layout.tabs.size - 1).coerceAtLeast(0))
        val goToTab: (Int) -> Unit = { index ->
            val target = layout.headIndex.getOrNull(index)
            if (target != null) {
                pendingTab = index
                scope.launch {
                    try {
                        // La sección queda justo debajo de la fila pegada (su aire superior se esconde tras ella).
                        val gap = if (layout.rows[target].topGap) sectionGapPx else 0
                        listState.animateScrollToItem(target, gap - navPx)
                    } finally {
                        pendingTab = null
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            layout.rows.forEach { row ->
                if (row.kind == KIND_NAV) {
                    stickyHeader(key = row.key, contentType = row.kind) { _ ->
                        MenuNav(
                            tabs = layout.tabs,
                            selectedIndex = selectedTab,
                            onSelect = goToTab,
                            searching = searching,
                            query = state.query,
                            onQueryChange = { state.query = it },
                            onOpenSearch = {
                                searching = true
                                scope.launch { listState.animateScrollToItem(NAV_INDEX) }
                            },
                            onCloseSearch = {
                                searching = false
                                state.query = ""
                            },
                            stuck = stuck,
                            gutter = gutter,
                        )
                    }
                } else {
                    item(key = row.key, contentType = row.kind) {
                        when (row.kind) {
                            KIND_COVER -> MenuCover(tenant, state, coverHeight, gutter, compact)
                            KIND_HEAD -> SectionHead(
                                title = row.title,
                                modifier = Modifier.padding(horizontal = gutter).padding(top = if (row.topGap) Space.x8 else 0.dp),
                                label = row.label,
                            )
                            KIND_BENTO -> Bento(row.items, state, gutter, wide = columns > 1)
                            KIND_ITEMS -> DishLine(row, state, columns, gutter)
                            KIND_EMPTY -> EmptyState(
                                title = "Sin resultados",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.x6),
                                message = if (state.query.isBlank()) "Este menú aún no tiene platillos publicados." else "No encontramos platillos para “${state.query.trim()}”.",
                                icon = AppIcons.Search,
                                actionText = if (state.query.isBlank()) null else "Limpiar búsqueda",
                                onAction = if (state.query.isBlank()) null else ({ state.query = "" }),
                            )
                            else -> MenuFooter(
                                tenant = tenant,
                                state = state,
                                gutter = gutter,
                                // barra inferior + aire; en escritorio no hay barra
                                bottomSpace = if (expanded) Space.x8 else Sizes.bar + Space.x6,
                                // Buscando, el pie llena lo que falte: aunque haya pocos resultados la fila de búsqueda sigue pegada arriba.
                                minHeight = if (searching) paneHeight - Sizes.bar else 0.dp,
                                onExit = onExit,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Portada + identidad ─────────────────────────

/** Día de la semana (1 = lunes … 7 = domingo) en la hora del centro de México (UTC−6, sin horario de verano). */
private fun weekdayInMexico(nowMs: Long): Int {
    val dayMs = 86_400_000L
    val local = nowMs - 6 * 3_600_000L
    val days = if (local >= 0) local / dayMs else (local - dayMs + 1) / dayMs
    // El 1 de enero de 1970 fue jueves.
    return (((days + 3) % 7 + 7) % 7).toInt() + 1
}

/** "Abierto · cierra 23:00" / "Cerrado · abre 13:00": el estado en tinta, la hora en mono. */
@Composable
private fun openingFact(tenant: TenantPublic): AnnotatedString {
    val colors = AppTheme.colors
    val mono = AppTheme.type.mono
    val today = remember(tenant.hours) { tenant.hours.firstOrNull { it.day == weekdayInMexico(nowMillis()) } }
    val strong = SpanStyle(color = colors.ink, fontWeight = FontWeight.Medium)
    val figure = SpanStyle(color = colors.ink, fontFamily = mono.fontFamily, fontWeight = mono.fontWeight, letterSpacing = mono.letterSpacing)
    return buildAnnotatedString {
        withStyle(strong) { append(if (tenant.isOpen) "Abierto" else "Cerrado") }
        val hour = if (tenant.isOpen) today?.close else today?.open
        if (!hour.isNullOrBlank()) {
            append(if (tenant.isOpen) " · cierra " else " · abre ")
            withStyle(figure) { append(hour) }
        }
    }
}

/** Cifra en mono + unidad en sans ("25–35 min", "4.8 / 5"). */
@Composable
private fun figureFact(figure: String, unit: String): AnnotatedString {
    val colors = AppTheme.colors
    val mono = AppTheme.type.mono
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colors.ink, fontFamily = mono.fontFamily, fontWeight = mono.fontWeight, letterSpacing = mono.letterSpacing)) { append(figure) }
        append(unit)
    }
}

@Composable
private fun Fact(text: AnnotatedString, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = AppTheme.type.bodySm.copy(color = AppTheme.colors.inkMuted),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
    )
}

/**
 * Portada: la foto es la atmósfera y la identidad va encima, sobre el velo — antetítulo en mono, nombre 40/44,
 * un filete y la ficha de datos (estado · tiempo · calificación) de margen a margen. La imagen se apaga en el
 * fondo en vez de cortarse. Sin foto de portada, la identidad va sola sobre el fondo.
 */
@Composable
private fun MenuCover(tenant: TenantPublic, state: DinerState, height: Dp, gutter: Dp, compact: Boolean) {
    val colors = AppTheme.colors
    val cover = tenant.theme.coverUrl
    val hasPhoto = !cover.isNullOrBlank()
    Box(if (hasPhoto) Modifier.fillMaxWidth().height(height) else Modifier.fillMaxWidth().padding(top = Space.x18)) {
        if (hasPhoto) {
            val frame = framingFor(cover, if (compact) PhotoUse.COVER else PhotoUse.COVER_WIDE)
            DishImage(
                cover,
                modifier = Modifier.matchParentSize().photoScrim(colors.canvas),
                contentDescription = null,
                shape = RectangleShape,
                ring = false,
                alignment = frame.alignment,
                zoom = frame.zoom,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = gutter)) {
            if (tenant.tagline.isNotBlank()) {
                LabelText(tenant.tagline, color = colors.inkMuted)
                Spacer(Modifier.height(Space.x1))
            }
            AppText(tenant.name, modifier = Modifier.offset(x = -CoverNameInset), style = AppTheme.type.display, maxLines = 2)
            Spacer(Modifier.height(Space.x3))
            Row(
                Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (tenant.isOpen) colors.positive else colors.inkSubtle)
                    Spacer(Modifier.width(Space.x2))
                    Fact(openingFact(tenant))
                }
                Spacer(Modifier.width(Space.x3))
                Fact(figureFact("${tenant.etaMinutes}–${tenant.etaMinutes + 10}", " min"))
                Spacer(Modifier.width(Space.x3))
                Fact(figureFact(((tenant.rating * 10).roundToInt() / 10.0).toString(), " / 5"))
            }
        }
        // Mis pedidos: aparece sobre la portada en cuanto hay un pedido en esta visita.
        if (state.history.isNotEmpty()) {
            AppIconButton(
                icon = AppIcons.Receipt,
                onClick = { state.screen = DinerScreen.HISTORY },
                modifier = Modifier.align(Alignment.TopEnd).padding(gutter),
                contentDescription = "Mis pedidos",
                filled = true,
                badge = state.history.size,
            )
        }
    }
}

// ───────────────────────── Bento destacado ─────────────────────────

/** Uno grande, dos chicos. En pantallas anchas el grande va a la izquierda y los chicos apilados a la derecha. */
@Composable
private fun Bento(items: List<MenuItem>, state: DinerState, gutter: Dp, wide: Boolean) {
    val lead = if (items.size == 2) null else items.firstOrNull()
    val small = if (items.size == 2) items else items.drop(1)
    if (wide && lead != null && small.size == 2) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter).height(BentoWideHeight),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            FeatureTile(lead, state, large = true, mediaHeight = null, modifier = Modifier.weight(2f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                small.forEach { FeatureTile(it, state, large = false, mediaHeight = null, modifier = Modifier.weight(1f).fillMaxWidth()) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            if (lead != null) FeatureTile(lead, state, large = true, mediaHeight = TileMediaLarge, modifier = Modifier.fillMaxWidth())
            if (small.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    small.forEach { FeatureTile(it, state, large = false, mediaHeight = TileMediaSmall, modifier = Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

/**
 * Mosaico con foto: superficie 1 con filete de foto y radio 16; la foto a sangre arriba y el pie (margen 12)
 * separado por un filete. Grande: nombre 16/20 con el precio en mono 16 en el mismo renglón y la descripción
 * debajo. Chico: nombre 15/20 y el precio en mono al fondo. [mediaHeight] nulo: la foto toma el alto que sobra.
 */
@Composable
private fun FeatureTile(item: MenuItem, state: DinerState, large: Boolean, mediaHeight: Dp?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val frame = framingFor(item.imageUrl, if (large) PhotoUse.TILE_LARGE else PhotoUse.TILE_SMALL)
    Column(
        modifier
            .pressable(scale = 0.99f) { state.openSheet(item) }
            .flatSurface(colors.surface, colors.lineStrong, AppTheme.shapes.lg),
    ) {
        DishImage(
            item.imageUrl,
            modifier = (if (mediaHeight != null) Modifier.height(mediaHeight) else Modifier.weight(1f)).fillMaxWidth(),
            contentDescription = item.name,
            shape = RectangleShape,
            ring = false,
            alignment = frame.alignment,
            zoom = frame.zoom,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (mediaHeight != null && !large) Modifier.weight(1f) else Modifier)
                .edgeTop(colors.line)
                .padding(Space.x3),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (large) {
                Row(Modifier.fillMaxWidth()) {
                    AppText(item.name, modifier = Modifier.weight(1f).alignByBaseline(), style = AppTheme.type.title, maxLines = 2)
                    AppText(
                        formatMoney(item.priceCents),
                        modifier = Modifier.padding(start = Space.x3).alignByBaseline(),
                        style = AppTheme.type.monoMd,
                        maxLines = 1,
                    )
                }
                if (item.description.isNotBlank()) {
                    AppText(item.description, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                }
            } else {
                AppText(item.name, style = AppTheme.type.titleSm, maxLines = 2)
                AppText(formatMoney(item.priceCents), style = AppTheme.type.mono, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── Filas de platillo ─────────────────────────

/** Un renglón de la lista: una fila en teléfono, dos en pantallas anchas (misma altura, mismos filetes). */
@Composable
private fun DishLine(row: MenuRow, state: DinerState, columns: Int, gutter: Dp) {
    if (columns <= 1) {
        val item = row.items.firstOrNull() ?: return
        DishRow(item, state, first = row.firstLine, last = row.lastLine, modifier = Modifier.fillMaxWidth().padding(horizontal = gutter))
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter).height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Space.x6),
        ) {
            row.items.forEach { item ->
                DishRow(item, state, first = row.firstLine, last = row.lastLine, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            repeat(columns - row.items.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * Fila de platillo: miniatura de 80 en macro · arriba el texto (la altura de mayúsculas del nombre coincide
 * con el borde de la foto) · abajo, en una franja de 32, el precio en mono y el control. Toda la fila abre
 * el platillo; el control agrega directo, o abre el platillo si hay que elegir algo.
 */
@Composable
private fun DishRow(item: MenuItem, state: DinerState, first: Boolean, last: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val quantity = state.quantityOf(item.id)
    val frame = framingFor(item.imageUrl, PhotoUse.THUMB)
    Row(
        modifier
            .then(if (first) Modifier.edgeTop(colors.line) else Modifier)
            .then(if (last) Modifier else Modifier.edgeBottom(colors.line))
            .pressable(scale = 1f) { state.openSheet(item) }
            .padding(top = Space.x3, bottom = if (last) 0.dp else Space.x3),
    ) {
        DishImage(
            item.imageUrl,
            modifier = Modifier.size(Sizes.thumb).alpha(if (item.available) 1f else 0.4f),
            contentDescription = null,
            alignment = frame.alignment,
            zoom = frame.zoom,
        )
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f).heightIn(min = Sizes.thumb), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().pullUp(Space.x1)) {
                AppText(item.name, style = AppTheme.type.title, color = if (item.available) colors.ink else colors.inkMuted, maxLines = 2)
                if (item.description.isNotBlank()) {
                    AppText(item.description, style = AppTheme.type.bodySm, color = if (item.available) colors.inkMuted else colors.inkSubtle, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth().height(Sizes.controlSm), verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    formatMoney(item.priceCents),
                    modifier = Modifier.weight(1f),
                    style = AppTheme.type.mono,
                    color = if (item.available) colors.ink else colors.inkSubtle,
                    maxLines = 1,
                )
                if (item.available) {
                    AddToCartButton(
                        quantity = quantity,
                        onAdd = { if (state.requiresChoice(item)) state.openSheet(item) else state.add(item) },
                        onRemove = { state.removeOne(item.id) },
                    )
                } else {
                    LabelText("Agotado")
                }
            }
        }
    }
}

// ───────────────────────── Pie de página ─────────────────────────

@Composable
private fun MenuFooter(tenant: TenantPublic, state: DinerState, gutter: Dp, bottomSpace: Dp, minHeight: Dp, onExit: () -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(start = gutter, end = gutter, top = Space.x8, bottom = bottomSpace)) {
        AppText(
            if (tenant.currency == "MXN") "Precios en pesos mexicanos. IVA incluido." else "Precios en ${tenant.currency}. Impuestos incluidos.",
            style = AppTheme.type.bodySm,
            color = colors.inkSubtle,
        )
        Spacer(Modifier.height(Space.x8))
        Column(Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x4)) {
            LabelText(tenant.name)
            if (tenant.address.isNotBlank()) {
                Spacer(Modifier.height(Space.x1))
                AppText(tenant.address, style = AppTheme.type.bodySm, color = colors.inkMuted)
            }
            if (tenant.phone.isNotBlank()) {
                AppText(tenant.phone, style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
            }
            Spacer(Modifier.height(Space.x4))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppButton(
                    "Mis pedidos",
                    onClick = { state.screen = DinerScreen.HISTORY },
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.SM,
                    icon = AppIcons.Receipt,
                )
                // Salida discreta (vuelve al índice de la demo / app anfitriona).
                AppButton("Salir del menú", onClick = onExit, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
            }
        }
    }
}

// ───────────────────────── Barra inferior ─────────────────────────

/**
 * Barra del menú (teléfono y tablet): sólida, con filete. A la izquierda "N PLATILLOS" en mono sobre la suma
 * de los platillos en mono 20/24; a la derecha la brasa: "Ver pedido →". Sólo existe cuando hay algo en el pedido.
 */
@Composable
fun MenuOrderBar(state: DinerState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    AnimatedVisibility(
        visible = state.itemCount > 0,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { it } + fadeIn(tween(Motion.FAST)),
        exit = slideOutVertically(tween(Motion.BASE, easing = Motion.exit)) { it } + fadeOut(tween(Motion.BASE)),
    ) {
        BottomBar {
            Column(Modifier.width(BarSummaryWidth)) {
                LabelText(dishCount(state.itemCount))
                AnimatedMoney(state.subtotalCents, style = AppTheme.type.monoLg)
            }
            AppButton("Ver pedido", onClick = onOpen, modifier = Modifier.weight(1f), trailingIcon = AppIcons.Forward)
        }
    }
}

/** Ancho del bloque izquierdo de la barra inferior (resumen o stepper grande). */
internal val BarSummaryWidth = 120.dp
