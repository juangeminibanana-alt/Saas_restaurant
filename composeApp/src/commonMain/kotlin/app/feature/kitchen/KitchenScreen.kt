package app.feature.kitchen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.TenantPublic
import app.shared.model.UserSession
import app.shared.util.label
import app.shared.util.nowMillis
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.CategoryTabs
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.Skeleton
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.ThemeController
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay

/** Medidas propias del tablero (lo demás sale de los tokens del sistema de diseño). */
private object BoardDims {
    val tabbedBelow = 720.dp
    val brandMark = 44.dp
    val brandMarkCompact = 36.dp
    val stageDot = 10.dp
    val liveDot = 14.dp
    val statIcon = 20.dp
    val countMinWidth = 32.dp
    val skeletonTicket = 184.dp
    const val PLACEHOLDER = "--:--"
}

@Composable
internal fun KitchenStage.tint(): Color = when (this) {
    KitchenStage.NEW -> AppTheme.colors.primary
    KitchenStage.PREPARING -> AppTheme.colors.info
    KitchenStage.READY -> AppTheme.colors.success
}

private val KitchenStage.emptyIcon: ImageVector
    get() = when (this) {
        KitchenStage.NEW -> AppIcons.Bell
        KitchenStage.PREPARING -> AppIcons.Flame
        KitchenStage.READY -> AppIcons.Check
    }

@Composable
internal fun KitchenScreen(
    tenant: TenantPublic,
    session: UserSession,
    board: KitchenBoardState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED

    // Reloj compartido: un solo tic por segundo para todos los cronómetros.
    val clock = produceState(initialValue = nowMillis()) {
        while (true) {
            delay(1_000L)
            value = nowMillis()
        }
    }
    val now: () -> Long = remember(clock) { { clock.value } }

    val columns by remember(board) { derivedStateOf { board.columns() } }
    // Lo que todavía depende de cocina (nuevos + en preparación) define los tiempos de espera.
    val pending = remember(columns) { columns[KitchenStage.NEW.ordinal] + columns[KitchenStage.PREPARING.ordinal] }
    val activeCount = columns.sumOf { it.size }

    Column(modifier = Modifier.fillMaxSize()) {
        KitchenHeader(
            tenant = tenant,
            session = session,
            columns = columns,
            pending = pending,
            live = board.live,
            now = now,
            compact = compact,
            expanded = expanded,
            onLogout = onLogout,
            onExit = onExit,
        )
        KitchenSummary(
            activeCount = activeCount,
            pending = pending,
            live = board.live,
            now = now,
            compact = compact,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val failure = board.error
            if (failure != null && board.isEmpty) {
                ErrorState(message = failure, modifier = Modifier.align(Alignment.Center), onRetry = onRetry)
            } else {
                KitchenBoard(
                    columns = columns,
                    board = board,
                    now = now,
                    loading = board.loading && board.isEmpty,
                    compact = compact,
                )
            }
        }
    }
}

// ───────────────────────── Encabezado ─────────────────────────

@Composable
private fun KitchenHeader(
    tenant: TenantPublic,
    session: UserSession,
    columns: List<List<Order>>,
    pending: List<Order>,
    live: Boolean,
    now: () -> Long,
    compact: Boolean,
    expanded: Boolean,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = AppTheme.colors
    val isDark = colors.isDark
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) Space.x4 else Space.x6, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            BrandMark(
                name = tenant.name,
                logoUrl = tenant.theme.logoUrl,
                size = if (compact) BoardDims.brandMarkCompact else BoardDims.brandMark,
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = tenant.name,
                    style = if (compact) AppTheme.type.title else AppTheme.type.h3,
                    maxLines = 1,
                )
                AppText(
                    text = "Cocina · " + session.name.ifBlank { session.role.label() },
                    style = AppTheme.type.bodySm,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    KitchenStage.entries.forEach { stage ->
                        StageCounter(stage = stage, count = columns[stage.ordinal].size)
                    }
                }
            }
            if (!compact) {
                LongestWait(pending = pending, now = now)
                LiveIndicator(live = live)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    icon = if (isDark) AppIcons.Sun else AppIcons.Moon,
                    onClick = { ThemeController.toggle(isDark) },
                    contentDescription = if (isDark) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                )
                AppIconButton(
                    icon = AppIcons.Logout,
                    onClick = onLogout,
                    contentDescription = "Cerrar sesión",
                )
                AppIconButton(
                    icon = AppIcons.Close,
                    onClick = onExit,
                    contentDescription = "Salir de la pantalla de cocina",
                )
            }
        }
        AppDivider()
    }
}

/** Contador de una columna en el encabezado (solo escritorio). */
@Composable
private fun StageCounter(stage: KitchenStage, count: Int) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(AppTheme.shapes.pill)
            .background(colors.surfaceSunken)
            .padding(horizontal = Space.x3, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        StageDot(color = stage.tint())
        AppText(text = stage.title, style = AppTheme.type.label, color = colors.inkMuted, maxLines = 1)
        AnimatedCount(
            count = count,
            style = AppTheme.type.title.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = colors.ink,
        )
    }
}

@Composable
private fun StageDot(color: Color) {
    Box(modifier = Modifier.size(BoardDims.stageDot).clip(AppTheme.shapes.pill).background(color))
}

/** Número que cambia con un deslizamiento vertical (sube al crecer, baja al disminuir). */
@Composable
private fun AnimatedCount(count: Int, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            val enter = slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { h -> if (up) h else -h } +
                fadeIn(tween(Motion.BASE))
            val exit = slideOutVertically(tween(Motion.BASE, easing = Motion.standard)) { h -> if (up) -h else h } +
                fadeOut(tween(Motion.FAST))
            enter.togetherWith(exit).using(SizeTransform(clip = false))
        },
    ) { value ->
        AppText(text = value.toString(), style = style, color = color, maxLines = 1)
    }
}

/** Indicador de conexión: punto que pulsa mientras llegan pedidos en tiempo real. */
@Composable
private fun LiveIndicator(live: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val tone by animateColorAsState(if (live) colors.success else colors.warning, tween(Motion.BASE))
    val soft by animateColorAsState(if (live) colors.successSoft else colors.warningSoft, tween(Motion.BASE))
    val wave by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
    )
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.pill)
            .background(soft)
            .padding(start = Space.x2, top = Space.x2, end = Space.x3, bottom = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Canvas(modifier = Modifier.size(BoardDims.liveDot)) {
            val radius = size.minDimension / 2f
            drawCircle(color = tone.copy(alpha = (1f - wave) * 0.55f), radius = radius * (0.45f + 0.55f * wave))
            drawCircle(color = tone, radius = radius * 0.45f)
        }
        AppText(
            text = if (live) "En vivo" else "Reconectando…",
            style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = tone,
            maxLines = 1,
        )
    }
}

/** Reloj del pedido que más lleva esperando en cocina. */
@Composable
private fun LongestWait(pending: List<Order>, now: () -> Long) {
    val colors = AppTheme.colors
    val oldest = pending.minOfOrNull { it.kitchenSince }
    val seconds = oldest?.let { ((now() - it).coerceAtLeast(0L)) / 1000L }
    val valueColor = if (seconds == null) colors.inkSubtle else elapsedColor(KitchenTiming.level(seconds))
    Row(
        modifier = Modifier
            .clip(AppTheme.shapes.pill)
            .border(1.dp, colors.line, AppTheme.shapes.pill)
            .padding(horizontal = Space.x3, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            imageVector = AppIcons.Clock,
            contentDescription = null,
            modifier = Modifier.size(BoardDims.statIcon),
            tint = colors.inkMuted,
        )
        AppText(text = "Mayor espera", style = AppTheme.type.label, color = colors.inkMuted, maxLines = 1)
        AppText(
            text = seconds?.let { formatKitchenElapsed(it) } ?: BoardDims.PLACEHOLDER,
            style = AppTheme.type.title.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = valueColor,
            maxLines = 1,
        )
    }
}

// ───────────────────────── Resumen ─────────────────────────

@Composable
private fun KitchenSummary(
    activeCount: Int,
    pending: List<Order>,
    live: Boolean,
    now: () -> Long,
    compact: Boolean,
) {
    val colors = AppTheme.colors
    val nowMs = now()
    val waits = pending.map { it.elapsedSeconds(nowMs) }
    val average = if (waits.isEmpty()) null else waits.sum() / waits.size
    val longest = waits.maxOrNull()
    val late = waits.count { it >= KitchenTiming.DANGER_SECONDS }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (compact) Space.x4 else Space.x6, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (compact) LiveIndicator(live = live)
        SummaryStat(
            icon = AppIcons.Receipt,
            label = "Pedidos activos",
            value = activeCount.toString(),
            valueColor = colors.ink,
        )
        SummaryStat(
            icon = AppIcons.Clock,
            label = "Tiempo promedio",
            value = average?.let { formatKitchenElapsed(it) } ?: BoardDims.PLACEHOLDER,
            valueColor = if (average == null) colors.inkSubtle else elapsedColor(KitchenTiming.level(average)),
        )
        if (compact) {
            SummaryStat(
                icon = AppIcons.Flame,
                label = "Mayor espera",
                value = longest?.let { formatKitchenElapsed(it) } ?: BoardDims.PLACEHOLDER,
                valueColor = if (longest == null) colors.inkSubtle else elapsedColor(KitchenTiming.level(longest)),
            )
        }
        SummaryStat(
            icon = AppIcons.Warning,
            label = "Con retraso",
            value = late.toString(),
            valueColor = if (late > 0) colors.danger else colors.ink,
        )
    }
}

@Composable
private fun SummaryStat(icon: ImageVector, label: String, value: String, valueColor: Color) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(BoardDims.statIcon),
            tint = colors.inkSubtle,
        )
        Column {
            AppText(text = label, style = AppTheme.type.label, color = colors.inkSubtle, maxLines = 1)
            AppText(
                text = value,
                style = AppTheme.type.h3.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

// ───────────────────────── Tablero ─────────────────────────

@Composable
private fun KitchenBoard(
    columns: List<List<Order>>,
    board: KitchenBoardState,
    now: () -> Long,
    loading: Boolean,
    compact: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tabbed = compact || maxWidth < BoardDims.tabbedBelow
        if (tabbed) {
            TabbedBoard(columns = columns, board = board, now = now, loading = loading)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Space.x6, end = Space.x6, bottom = Space.x6),
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                KitchenStage.entries.forEach { stage ->
                    KitchenColumn(
                        stage = stage,
                        orders = columns[stage.ordinal],
                        board = board,
                        now = now,
                        loading = loading,
                        showHeader = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Teléfono / ventana angosta: una columna a la vez con pestañas que muestran el conteo. */
@Composable
private fun TabbedBoard(
    columns: List<List<Order>>,
    board: KitchenBoardState,
    now: () -> Long,
    loading: Boolean,
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val tabs = KitchenStage.entries.map { stage -> stage.title + " · " + columns[stage.ordinal].size }
    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTabs(
            tabs = tabs,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally(tween(Motion.SLOW, easing = Motion.standard)) { w ->
                    if (forward) w / 4 else -w / 4
                } + fadeIn(tween(Motion.BASE))
                val exit = slideOutHorizontally(tween(Motion.BASE, easing = Motion.exit)) { w ->
                    if (forward) -w / 4 else w / 4
                } + fadeOut(tween(Motion.FAST))
                enter.togetherWith(exit)
            },
        ) { index ->
            val stage = KitchenStage.entries[index]
            KitchenColumn(
                stage = stage,
                orders = columns[index],
                board = board,
                now = now,
                loading = loading,
                showHeader = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Space.x4, top = Space.x3, end = Space.x4, bottom = Space.x4),
            )
        }
    }
}

@Composable
private fun KitchenColumn(
    stage: KitchenStage,
    orders: List<Order>,
    board: KitchenBoardState,
    now: () -> Long,
    loading: Boolean,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.lg
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(1.dp, colors.line, shape),
    ) {
        if (showHeader) ColumnHeader(stage = stage, count = orders.size)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                ColumnSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.x3,
                        top = if (showHeader) Space.x1 else Space.x3,
                        end = Space.x3,
                        bottom = Space.x4,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    items(items = orders, key = { it.id }) { order ->
                        KitchenTicket(
                            order = order,
                            stage = stage,
                            board = board,
                            now = now,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(Motion.BASE),
                                placementSpec = Motion.gentle<IntOffset>(),
                                fadeOutSpec = tween(Motion.BASE, easing = Motion.exit),
                            ),
                        )
                    }
                }
                ColumnEmpty(visible = orders.isEmpty(), stage = stage)
            }
        }
    }
}

@Composable
private fun ColumnHeader(stage: KitchenStage, count: Int) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        StageDot(color = stage.tint())
        AppText(text = stage.title, modifier = Modifier.weight(1f), style = AppTheme.type.h3, maxLines = 1)
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = BoardDims.countMinWidth)
                .clip(AppTheme.shapes.pill)
                .background(colors.surface)
                .border(1.dp, colors.line, AppTheme.shapes.pill)
                .padding(horizontal = Space.x3, vertical = Space.x1),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedCount(
                count = count,
                style = AppTheme.type.title.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = colors.ink,
            )
        }
    }
}

/** Estado vacío de una columna; aparece con un pequeño retraso para no pisar la salida del último ticket. */
@Composable
private fun ColumnEmpty(visible: Boolean, stage: KitchenStage) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.SLOW, delayMillis = Motion.SLOW)),
        exit = fadeOut(tween(Motion.FAST)),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(Space.x4), contentAlignment = Alignment.Center) {
            EmptyState(title = stage.emptyTitle, message = stage.emptyMessage, icon = stage.emptyIcon)
        }
    }
}

@Composable
private fun ColumnSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x3, vertical = Space.x1),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        repeat(2) {
            Skeleton(
                modifier = Modifier.fillMaxWidth().height(BoardDims.skeletonTicket),
                shape = AppTheme.shapes.lg,
            )
        }
    }
}
