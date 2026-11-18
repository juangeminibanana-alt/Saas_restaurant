package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

/** Entrada de navegación. [section] agrupa: al cambiar de sección se dibuja su nombre como etiqueta en mono. */
data class NavItem(val id: String, val label: String, val icon: ImageVector, val badge: Int? = null, val section: String? = null)

private val SidebarWidth = 248.dp
private val RailWidth = 72.dp
private val NavItemHeight = 44.dp
private val NavItemGap = 4.dp
private val ContentMaxWidth = 1240.dp

/**
 * Armazón de los paneles en NOCHE: todo sobre el fondo, separado sólo por filetes. Barra lateral en EXPANDED,
 * riel de íconos en MEDIUM, barra superior + navegación inferior en COMPACT. La entrada activa es superficie 2
 * con texto en tinta (sin brasa); el título de la barra superior es una etiqueta en mono MAYÚSCULAS.
 */
@Composable
fun AdminScaffold(
    title: String, navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, brand: @Composable () -> Unit = {}, userName: String? = null, userRole: String? = null,
    onLogout: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    when (LocalWindowSize.current) {
        WindowSize.EXPANDED -> Row(modifier.fillMaxSize().background(colors.canvas)) {
            Sidebar(navItems, selectedId, onSelect, brand, userName, userRole, onLogout)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(title, compact = false, leading = null, actions = actions, trailing = null)
                ContentArea(horizontal = Space.x8, top = Space.x8, content = content)
            }
        }

        WindowSize.MEDIUM -> Row(modifier.fillMaxSize().background(colors.canvas)) {
            Rail(navItems, selectedId, onSelect, brand, userName, onLogout)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(title, compact = false, leading = null, actions = actions, trailing = null)
                ContentArea(horizontal = Space.x6, top = Space.x6, content = content)
            }
        }

        WindowSize.COMPACT -> Column(modifier.fillMaxSize().background(colors.canvas)) {
            TopBar(
                title = title,
                compact = true,
                leading = brand,
                actions = actions,
                trailing = if (onLogout != null) {
                    { AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted, plain = true) }
                } else null,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ContentArea(horizontal = Space.gutter, top = Space.x4, content = content)
            }
            BottomNav(navItems, selectedId, onSelect)
        }
    }
}

/**
 * Encabezado de página: [label] opcional como antetítulo en mono MAYÚSCULAS, título 32/36 (20/24 en COMPACT),
 * subtítulo en texto chico y acciones a la derecha.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    label: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    Row(
        modifier.fillMaxWidth().padding(bottom = if (compact) Space.x4 else Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (!label.isNullOrBlank()) {
                LabelText(label, color = AppTheme.colors.inkMuted)
                Spacer(Modifier.height(Space.x1))
            }
            AppText(title, style = if (compact) AppTheme.type.h2 else AppTheme.type.h1, maxLines = 2)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x1))
                AppText(subtitle, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
            }
        }
        Spacer(Modifier.width(Space.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ───────────────────────── Piezas ─────────────────────────

@Composable
private fun ContentArea(horizontal: Dp, top: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(start = horizontal, end = horizontal, top = top),
        ) {
            content()
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    compact: Boolean,
    leading: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(if (compact) Space.x14 else 64.dp)
            .edgeBottom(colors.line)
            .padding(horizontal = if (compact) Space.gutter else Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.widthIn(max = 36.dp).height(36.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) { leading() }
            Spacer(Modifier.width(Space.x3))
        }
        LabelText(title, modifier = Modifier.weight(1f), color = colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically, content = actions)
        if (trailing != null) {
            Spacer(Modifier.width(Space.x1))
            trailing()
        }
    }
}

/** Filete vertical a la derecha, por dentro. */
private fun Modifier.edgeEnd(color: androidx.compose.ui.graphics.Color): Modifier = drawWithContent {
    drawContent()
    val w = 1.dp.toPx()
    drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
}

@Composable
private fun Sidebar(
    navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    brand: @Composable () -> Unit, userName: String?, userRole: String?, onLogout: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .background(colors.canvas)
            .edgeEnd(colors.line)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).edgeBottom(colors.line).padding(horizontal = Space.x5),
            contentAlignment = Alignment.CenterStart,
        ) { brand() }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3, vertical = Space.x3),
            verticalArrangement = Arrangement.spacedBy(NavItemGap),
        ) {
            var lastSection: String? = null
            navItems.forEachIndexed { index, item ->
                if (item.section != null && item.section != lastSection) {
                    LabelText(
                        item.section,
                        modifier = Modifier.padding(start = Space.x3, top = if (index == 0) Space.x1 else Space.x4, bottom = Space.x1),
                    )
                }
                lastSection = item.section
                val selected = item.id == selectedId
                val bg by animateColorAsState(
                    if (selected) colors.surfaceRaised else colors.surfaceRaised.copy(alpha = 0f), tween(Motion.FAST), label = "navBg",
                )
                val tint by animateColorAsState(if (selected) colors.ink else colors.inkSubtle, tween(Motion.FAST), label = "navTint")
                val textColor by animateColorAsState(if (selected) colors.ink else colors.inkMuted, tween(Motion.FAST), label = "navText")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(NavItemHeight)
                        .background(bg, AppTheme.shapes.sm)
                        .pressable(scale = 0.99f) { onSelect(item.id) }
                        .padding(horizontal = Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(item.icon, tint = tint, size = Sizes.icon)
                    Spacer(Modifier.width(Space.x3))
                    AppText(item.label, modifier = Modifier.weight(1f), style = AppTheme.type.titleSm, color = textColor, maxLines = 1)
                    NavBadge(item.badge)
                }
            }
        }

        if (userName != null || onLogout != null) {
            Row(
                Modifier.fillMaxWidth().edgeTop(colors.line).padding(start = Space.x5, end = Space.x2, top = Space.x3, bottom = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (userName != null) {
                    Avatar(userName, size = 32.dp)
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        AppText(userName, style = AppTheme.type.bodySm.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                        if (!userRole.isNullOrBlank()) LabelText(userRole)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (onLogout != null) {
                    AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted, plain = true)
                }
            }
        }
    }
}

@Composable
private fun Rail(
    navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    brand: @Composable () -> Unit, userName: String?, onLogout: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(colors.canvas)
            .edgeEnd(colors.line)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).edgeBottom(colors.line), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 40.dp).height(40.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) { brand() }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Space.x3),
            verticalArrangement = Arrangement.spacedBy(NavItemGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            navItems.forEach { item ->
                val selected = item.id == selectedId
                val bg by animateColorAsState(
                    if (selected) colors.surfaceRaised else colors.surfaceRaised.copy(alpha = 0f), tween(Motion.FAST), label = "railBg",
                )
                val tint by animateColorAsState(if (selected) colors.ink else colors.inkSubtle, tween(Motion.FAST), label = "railTint")
                Box(
                    Modifier.size(Sizes.control).background(bg, AppTheme.shapes.sm).pressable(scale = 0.96f) { onSelect(item.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(item.icon, tint = tint, size = Sizes.icon)
                    if ((item.badge ?: 0) > 0) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) { NavBadge(item.badge, small = true) }
                    }
                }
            }
        }
        if (userName != null) {
            Avatar(userName, size = 32.dp)
            Spacer(Modifier.height(Space.x2))
        }
        if (onLogout != null) {
            AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted, plain = true)
        }
        Spacer(Modifier.height(Space.x3))
    }
}

@Composable
private fun BottomNav(navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val scrollable = navItems.size > 5
    CompositionLocalProvider(LocalSurfaceLevel provides 1) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .edgeTop(colors.line)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = Space.x1),
        ) {
            navItems.forEach { item ->
                val selected = item.id == selectedId
                val tint by animateColorAsState(if (selected) colors.ink else colors.inkSubtle, tween(Motion.FAST), label = "tabTint")
                val bg by animateColorAsState(
                    if (selected) colors.surfaceRaised else colors.surfaceRaised.copy(alpha = 0f), tween(Motion.FAST), label = "tabBg",
                )
                Column(
                    (if (scrollable) Modifier.width(76.dp) else Modifier.weight(1f))
                        .height(64.dp)
                        .pressable(scale = 0.97f) { onSelect(item.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.width(48.dp).height(28.dp).background(bg, AppTheme.shapes.sm))
                        GlyphIcon(item.icon, tint = tint, size = Sizes.icon)
                        if ((item.badge ?: 0) > 0) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) { NavBadge(item.badge, small = true) }
                        }
                    }
                    Spacer(Modifier.height(Space.x1))
                    AppText(item.label, style = AppTheme.type.label, color = tint, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Conteo pendiente de una entrada de navegación: ficha sólida de brasa en mono. */
@Composable
private fun NavBadge(count: Int?, small: Boolean = false) {
    if (count == null || count <= 0) return
    CountChip(count, size = if (small) 16.dp else 20.dp)
}
