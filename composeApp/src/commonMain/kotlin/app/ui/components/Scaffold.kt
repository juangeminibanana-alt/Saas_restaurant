package app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize

data class NavItem(val id: String, val label: String, val icon: ImageVector, val badge: Int? = null)

private val SidebarWidth = 248.dp
private val RailWidth = 72.dp
private val NavItemHeight = 44.dp
private val NavItemGap = 4.dp
private val ContentMaxWidth = 1240.dp

/** Barra lateral en EXPANDED, riel de íconos en MEDIUM, barra superior + navegación inferior en COMPACT. */
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
                ContentArea(horizontal = Space.x8, top = Space.x6, content = content)
            }
        }

        WindowSize.MEDIUM -> Row(modifier.fillMaxSize().background(colors.canvas)) {
            Rail(navItems, selectedId, onSelect, brand, userName, onLogout)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(title, compact = false, leading = null, actions = actions, trailing = null)
                ContentArea(horizontal = Space.x6, top = Space.x5, content = content)
            }
        }

        WindowSize.COMPACT -> Column(modifier.fillMaxSize().background(colors.canvas)) {
            TopBar(
                title = title,
                compact = true,
                leading = brand,
                actions = actions,
                trailing = if (onLogout != null) {
                    { AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted) }
                } else null,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ContentArea(horizontal = Space.x4, top = Space.x4, content = content)
            }
            BottomNav(navItems, selectedId, onSelect)
        }
    }
}

@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    Row(
        modifier.fillMaxWidth().padding(bottom = if (compact) Space.x4 else Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = if (compact) AppTheme.type.h2 else AppTheme.type.h1, maxLines = 2)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x1))
                AppText(subtitle, style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
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
    val lineColor = colors.line
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(if (compact) 56.dp else 64.dp)
            .drawBehind {
                drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (compact) Space.x4 else Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.widthIn(max = 36.dp).height(36.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) { leading() }
            Spacer(Modifier.width(Space.x3))
        }
        AppText(title, modifier = Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically, content = actions)
        if (trailing != null) {
            Spacer(Modifier.width(Space.x1))
            trailing()
        }
    }
}

@Composable
private fun Sidebar(
    navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    brand: @Composable () -> Unit, userName: String?, userRole: String?, onLogout: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val lineColor = colors.line
    Column(
        Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .background(colors.surface)
            .drawBehind {
                drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.x5),
            contentAlignment = Alignment.CenterStart,
        ) { brand() }

        val selectedIndex = navItems.indexOfFirst { it.id == selectedId }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3, vertical = Space.x3),
        ) {
            // Indicador: píldora suave que se desliza con resorte hasta el elemento activo.
            if (selectedIndex >= 0) {
                val y by animateDpAsState((NavItemHeight + NavItemGap) * selectedIndex, Motion.snappy(), label = "navIndicator")
                Box(
                    Modifier
                        .offset(y = y)
                        .fillMaxWidth()
                        .height(NavItemHeight)
                        .background(colors.primarySoft, AppTheme.shapes.sm),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .height(18.dp)
                            .background(colors.primary, CircleShape),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(NavItemGap)) {
                navItems.forEach { item ->
                    val selected = item.id == selectedId
                    val tint by animateColorAsState(if (selected) colors.primary else colors.inkMuted, tween(Motion.BASE), label = "navTint")
                    val textColor by animateColorAsState(if (selected) colors.ink else colors.inkMuted, tween(Motion.BASE), label = "navText")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(NavItemHeight)
                            .pressable(scale = 0.98f) { onSelect(item.id) }
                            .padding(horizontal = Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(item.icon, tint = tint, size = 20.dp)
                        Spacer(Modifier.width(Space.x3))
                        AppText(
                            item.label,
                            modifier = Modifier.weight(1f),
                            style = AppTheme.type.body.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                            color = textColor,
                            maxLines = 1,
                        )
                        NavBadge(item.badge)
                    }
                }
            }
        }

        if (userName != null || onLogout != null) {
            Box(Modifier.padding(horizontal = Space.x3).fillMaxWidth().height(1.dp).background(colors.line))
            Row(
                Modifier.fillMaxWidth().padding(start = Space.x4, end = Space.x2, top = Space.x3, bottom = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (userName != null) {
                    Avatar(userName, size = 36.dp)
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        AppText(userName, style = AppTheme.type.bodySm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                        if (!userRole.isNullOrBlank()) AppText(userRole, style = AppTheme.type.label, color = colors.inkSubtle, maxLines = 1)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (onLogout != null) {
                    AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted)
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
    val lineColor = colors.line
    val itemSize = 48.dp
    val gap = Space.x2
    Column(
        Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(colors.surface)
            .drawBehind {
                drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 40.dp).height(40.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) { brand() }
        }
        val selectedIndex = navItems.indexOfFirst { it.id == selectedId }
        Box(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Space.x3),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selectedIndex >= 0) {
                val y by animateDpAsState((itemSize + gap) * selectedIndex, Motion.snappy(), label = "railIndicator")
                Box(Modifier.offset(y = y).size(itemSize).background(colors.primarySoft, AppTheme.shapes.sm))
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                navItems.forEach { item ->
                    val selected = item.id == selectedId
                    val tint by animateColorAsState(if (selected) colors.primary else colors.inkMuted, tween(Motion.BASE), label = "railTint")
                    Box(
                        Modifier.size(itemSize).pressable(scale = 0.92f) { onSelect(item.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(item.icon, tint = tint, size = 22.dp)
                        if ((item.badge ?: 0) > 0) {
                            Box(Modifier.align(Alignment.TopEnd).padding(Space.x1)) { NavBadge(item.badge, small = true) }
                        }
                    }
                }
            }
        }
        if (userName != null) {
            Avatar(userName, size = 36.dp)
            Spacer(Modifier.height(Space.x2))
        }
        if (onLogout != null) {
            AppIconButton(AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión", tint = colors.inkMuted)
        }
        Spacer(Modifier.height(Space.x3))
    }
}

@Composable
private fun BottomNav(navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val lineColor = colors.line
    val scrollable = navItems.size > 5
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = Space.x1),
    ) {
        navItems.forEach { item ->
            val selected = item.id == selectedId
            val tint by animateColorAsState(if (selected) colors.primary else colors.inkSubtle, tween(Motion.BASE), label = "tabTint")
            val textColor by animateColorAsState(if (selected) colors.ink else colors.inkSubtle, tween(Motion.BASE), label = "tabText")
            val pill by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "tabPill")
            Column(
                (if (scrollable) Modifier.width(76.dp) else Modifier.weight(1f))
                    .height(62.dp)
                    .pressable(scale = 0.94f) { onSelect(item.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .graphicsLayer {
                                alpha = pill.coerceIn(0f, 1f)
                                scaleX = 0.6f + 0.4f * pill
                            }
                            .background(colors.primarySoft, CircleShape),
                    )
                    GlyphIcon(item.icon, tint = tint, size = 22.dp)
                    if ((item.badge ?: 0) > 0) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp)) { NavBadge(item.badge, small = true) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                AppText(
                    item.label,
                    style = AppTheme.type.label.copy(fontSize = 11.sp),
                    color = textColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NavBadge(count: Int?, small: Boolean = false) {
    if (count == null || count <= 0) return
    val colors = AppTheme.colors
    val height = if (small) 16.dp else 20.dp
    Box(
        Modifier
            .height(height)
            .widthIn(min = height)
            .background(colors.primary, CircleShape)
            .padding(horizontal = if (small) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            if (count > 99) "99+" else count.toString(),
            style = AppTheme.type.label.copy(fontSize = if (small) 10.sp else 11.sp, lineHeight = if (small) 12.sp else 14.sp),
            color = colors.onPrimary,
            maxLines = 1,
        )
    }
}

@Suppress("unused")
private val UnusedColor = Color.Unspecified
