package app.feature.kitchen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.shared.model.Order
import app.shared.model.TenantPublic
import app.shared.model.UserSession
import app.shared.util.label
import app.shared.util.nowMillis
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.CountChip
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.LabelText
import app.ui.components.SegmentedControl
import app.ui.components.Skeleton
import app.ui.components.edgeBottom
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay

/** Medidas propias del tablero (lo demás sale de los tokens del sistema de diseño). */
private object BoardDims {
    val tabbedBelow = 720.dp
    val headerCompact = Space.x14 + Space.x2
    val brandMark = 36.dp
    val skeletonTicket = 184.dp
    const val PLACEHOLDER = "--:--"
}

private val KitchenStage.emptyIcon: ImageVector
    get() = when (this) {
        KitchenStage.NEW -> AppIcons.Bell
        KitchenStage.PREPARING -> AppIcons.Flame
        KitchenStage.READY -> AppIcons.Check
    }

/** Margen lateral del tablero: 16 en teléfono, 24 en pantallas anchas. */
@Composable
private fun boardGutter(compact: Boolean): Dp = if (compact) Space.gutter else Space.x6

/**
 * Cifra del tablero: Geist Mono a tamaño de título. En la pared de la cocina se lee de lejos (32/36);
 * en teléfono baja a 20/24.
 */
@Composable
internal fun boardNumeral(compact: Boolean): TextStyle {
    val type = AppTheme.type
    return if (compact) type.monoLg else type.monoLg.copy(
        fontSize = type.h1.fontSize, lineHeight = type.h1.lineHeight, letterSpacing = type.h1.letterSpacing,
    )
}

/** Reloj corto para el tablero: "04:07" y, para esperas muy largas, "2h05". */
internal fun formatKitchenClock(totalSeconds: Long): String {
    val minutes = totalSeconds.coerceAtLeast(0L) / 60
    return if (minutes >= 100) "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}" else formatKitchenElapsed(totalSeconds)
}

/**
 * Tablero de cocina en NOCHE: negro, plano y legible de lejos. Los números de pedido y los tiempos van en
 * Geist Mono grande; la urgencia vive sólo en el color del cronómetro (verde → ámbar → rojo); la brasa es la
 * acción de cada ticket y la ficha "NUEVO" de lo que todavía nadie aceptó. Nada brilla ni pulsa.
 */
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

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        KitchenHeader(
            tenant = tenant,
            session = session,
            live = board.live,
            connectedOnce = board.connectedOnce,
            compact = compact,
            onLogout = onLogout,
            onExit = onExit,
        )
        KitchenSummary(activeCount = activeCount, pending = pending, now = now, compact = compact)
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
    live: Boolean,
    connectedOnce: Boolean,
    compact: Boolean,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) BoardDims.headerCompact else Sizes.bar)
            .edgeBottom(colors.line)
            .padding(horizontal = boardGutter(compact)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        BrandMark(name = tenant.name, logoUrl = tenant.theme.logoUrl, size = BoardDims.brandMark)
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = tenant.name, style = AppTheme.type.title, maxLines = 1)
            // En teléfono no cabe el indicador "En vivo": su punto acompaña a la etiqueta.
            LabelText(
                text = "Cocina · " + session.name.ifBlank { session.role.label() },
                dot = if (compact) (if (live) colors.positive else colors.warning) else null,
            )
        }
        if (!compact) LiveIndicator(live = live, connectedOnce = connectedOnce)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(icon = AppIcons.Logout, onClick = onLogout, contentDescription = "Cerrar sesión")
            AppIconButton(icon = AppIcons.Close, onClick = onExit, contentDescription = "Salir de la pantalla de cocina")
        }
    }
}

/** Estado de la conexión: un punto sólido y una etiqueta en mono. Verde = llegan pedidos en vivo. */
@Composable
private fun LiveIndicator(live: Boolean, connectedOnce: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    LabelText(
        text = when {
            live -> "En vivo"
            connectedOnce -> "Reconectando"
            else -> "Conectando"
        },
        modifier = modifier,
        color = if (live) colors.ink else colors.inkMuted,
        dot = if (live) colors.positive else colors.warning,
    )
}

// ───────────────────────── Resumen ─────────────────────────

/** Filete vertical de 1 px a la izquierda de una celda. */
private fun Modifier.edgeStart(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(color, topLeft = Offset.Zero, size = Size(1.dp.toPx(), size.height))
}

/**
 * Franja de cifras: una sola superficie con cuatro celdas iguales separadas por filetes. Cabe entera en
 * cualquier ancho (no se desplaza ni se corta). Sólo los tiempos toman color.
 */
@Composable
private fun KitchenSummary(activeCount: Int, pending: List<Order>, now: () -> Long, compact: Boolean) {
    val colors = AppTheme.colors
    val nowMs = now()
    val waits = pending.map { it.elapsedSeconds(nowMs) }
    val average = if (waits.isEmpty()) null else waits.sum() / waits.size
    val longest = waits.maxOrNull()
    val late = waits.count { it >= KitchenTiming.DANGER_SECONDS }
    val gutter = boardGutter(compact)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = gutter, end = gutter, top = Space.x3)
            .height(IntrinsicSize.Min)
            .flatSurface(colors.surface, colors.line, AppTheme.shapes.md),
    ) {
        SummaryCell(
            label = if (compact) "Activos" else "Pedidos activos",
            value = activeCount.toString(),
            valueColor = colors.ink,
            compact = compact, first = true,
            modifier = Modifier.weight(1f),
        )
        SummaryCell(
            label = if (compact) "Promedio" else "Tiempo promedio",
            value = average?.let { formatKitchenClock(it) } ?: BoardDims.PLACEHOLDER,
            valueColor = if (average == null) colors.inkSubtle else elapsedColor(KitchenTiming.level(average)),
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        SummaryCell(
            label = if (compact) "Máxima" else "Mayor espera",
            value = longest?.let { formatKitchenClock(it) } ?: BoardDims.PLACEHOLDER,
            valueColor = if (longest == null) colors.inkSubtle else elapsedColor(KitchenTiming.level(longest)),
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        SummaryCell(
            label = if (compact) "Retraso" else "Con retraso",
            value = late.toString(),
            valueColor = if (late > 0) colors.danger else colors.ink,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, compact: Boolean, modifier: Modifier = Modifier, first: Boolean = false) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(if (first) Modifier else Modifier.edgeStart(colors.line))
            .padding(horizontal = if (compact) Space.x3 else Space.x5, vertical = if (compact) Space.x2 else Space.x4),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else Space.x1),
    ) {
        LabelText(text = label)
        AppText(text = value, style = boardNumeral(compact), color = valueColor, maxLines = 1)
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
            TabbedBoard(columns = columns, board = board, now = now, loading = loading, compact = compact)
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = boardGutter(compact)),
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
                        large = true,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Teléfono / ventana angosta: una columna a la vez; el segmentado muestra el conteo de cada una. */
@Composable
private fun TabbedBoard(
    columns: List<List<Order>>,
    board: KitchenBoardState,
    now: () -> Long,
    loading: Boolean,
    compact: Boolean,
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val tabs = KitchenStage.entries.map { stage -> stage.shortTitle + " " + columns[stage.ordinal].size }
    val gutter = boardGutter(compact)
    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedControl(
            options = tabs,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = Space.x3),
        )
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.FAST)) },
        ) { index ->
            val stage = KitchenStage.entries[index]
            KitchenColumn(
                stage = stage,
                orders = columns[index],
                board = board,
                now = now,
                loading = loading,
                showHeader = false,
                large = !compact,
                modifier = Modifier.fillMaxSize().padding(horizontal = gutter),
            )
        }
    }
}

/** Nombre corto de la columna para el segmentado del teléfono. */
private val KitchenStage.shortTitle: String
    get() = when (this) {
        KitchenStage.NEW -> "Nuevos"
        KitchenStage.PREPARING -> "Preparando"
        KitchenStage.READY -> "Listos"
    }

@Composable
private fun KitchenColumn(
    stage: KitchenStage,
    orders: List<Order>,
    board: KitchenBoardState,
    now: () -> Long,
    loading: Boolean,
    showHeader: Boolean,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showHeader) ColumnHeader(stage = stage, count = orders.size)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                ColumnSkeleton()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Space.x3, bottom = Space.x6),
                    verticalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    items(items = orders, key = { it.id }) { order ->
                        KitchenTicket(
                            order = order,
                            stage = stage,
                            board = board,
                            now = now,
                            large = large,
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

/**
 * Cabecera de columna: nombre en mono MAYÚSCULAS y conteo en mono sobre un filete. Los "Nuevos" esperan a que
 * alguien los acepte: su conteo es una ficha sólida de brasa.
 */
@Composable
private fun ColumnHeader(stage: KitchenStage, count: Int) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(Sizes.controlLg).edgeBottom(colors.line),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        LabelText(text = stage.title, modifier = Modifier.weight(1f), color = colors.ink)
        if (stage == KitchenStage.NEW && count > 0) {
            CountChip(count = count, size = Space.x6)
        } else {
            AppText(text = count.toString(), style = AppTheme.type.monoMd, color = colors.inkMuted, maxLines = 1)
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
        modifier = Modifier.fillMaxWidth().padding(top = Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        repeat(2) {
            Skeleton(
                modifier = Modifier.fillMaxWidth().height(BoardDims.skeletonTicket),
                shape = AppTheme.shapes.lg,
            )
        }
    }
}
