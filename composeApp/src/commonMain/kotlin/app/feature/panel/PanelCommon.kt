package app.feature.panel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.OrderStatus
import app.shared.model.Role
import app.ui.components.AppButton
import app.ui.components.AppCard
import app.ui.components.AppDialog
import app.ui.components.AppText
import app.ui.components.ButtonVariant
import app.ui.components.LabelText
import app.ui.components.Skeleton
import app.ui.components.StatusDot
import app.ui.components.Tone
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlin.coroutines.cancellation.CancellationException

// ───────────────────────── Carga asíncrona ─────────────────────────

internal sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Stable
internal class Loader<T> {
    var state: Load<T> by mutableStateOf<Load<T>>(Load.Loading)
    var version: Int by mutableStateOf(0)
    val value: T? get() = (state as? Load.Ready<T>)?.value
    fun reload() { version++ }
    fun set(value: T) { state = Load.Ready(value) }
}

/** Mensaje listo para mostrar a partir de cualquier fallo del repositorio. */
internal fun errorText(e: Throwable, fallback: String): String = e.message?.takeIf { it.isNotBlank() } ?: fallback

@Composable
internal fun <T> rememberLoader(key: Any? = Unit, block: suspend () -> T): Loader<T> {
    val loader = remember(key) { Loader<T>() }
    val current by rememberUpdatedState(block)
    LaunchedEffect(loader, loader.version) {
        if (loader.state !is Load.Ready) loader.state = Load.Loading
        loader.state = try {
            Load.Ready(current())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Load.Failed(errorText(e, "No pudimos cargar la información. Intenta de nuevo."))
        }
    }
    return loader
}

// ───────────────────────── Layout ─────────────────────────

@Composable
internal fun pagePadding(): Dp = when (LocalWindowSize.current) {
    WindowSize.COMPACT -> Space.x4
    WindowSize.MEDIUM -> Space.x6
    WindowSize.EXPANDED -> Space.x8
}

/**
 * Contenedor estándar de una sección: scroll vertical y ancho máximo. El margen lateral y el superior ya los pone
 * el armazón del panel (16 en teléfono), así que aquí no se repiten: el contenido llega de margen a margen.
 */
@Composable
internal fun PanelPage(maxWidth: Dp = 1180.dp, content: @Composable ColumnScope.() -> Unit) {
    // Alineado a la izquierda: todas las secciones comparten el mismo borde, aunque unas sean más angostas.
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            content()
            Spacer(Modifier.height(Space.x10))
        }
    }
}

/** Rejilla simple (no perezosa) con columnas de igual ancho. [equalHeight] iguala la altura de cada fila. */
@Composable
internal fun <T> SimpleGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = Space.x2,
    equalHeight: Boolean = false,
    cell: @Composable (T) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    val stretch = equalHeight && cols > 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(cols).forEach { rowItems ->
            Row(
                (if (stretch) Modifier.height(IntrinsicSize.Min) else Modifier).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowItems.forEach { item -> Box(Modifier.weight(1f).then(if (stretch) Modifier.fillMaxHeight() else Modifier)) { cell(item) } }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ───────────────────────── Piezas pequeñas ─────────────────────────

@Composable
internal fun PanelIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.inkMuted, size: Dp = 18.dp) {
    Image(imageVector = icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/**
 * Ícono dentro de un cuadro plano (superficie + filete, radio 8) para encabezados de tarjeta. El ícono va en tinta:
 * el estado se dice con un `StatusPill`, no tiñendo el cuadro. [tone] se conserva por compatibilidad.
 */
@Composable
internal fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") tone: Tone = Tone.NEUTRAL, size: Dp = Sizes.control) {
    val c = AppTheme.colors
    Box(modifier.size(size).flatSurface(AppTheme.controlFill, c.line, AppTheme.shapes.sm), contentAlignment = Alignment.Center) {
        PanelIcon(icon, tint = c.ink, size = Sizes.icon)
    }
}

/** Nombre de campo o de bloque: etiqueta en mono MAYÚSCULAS, con una pista opcional debajo. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        LabelText(text)
        if (hint != null) AppText(hint, style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle)
    }
}

/** Opción seleccionable: superficie + filete; elegida, el filete pasa a tinta (la selección nunca usa la brasa). */
@Composable
internal fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = Space.x3,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val ring by animateColorAsState(if (selected) c.select else c.line, tween(Motion.FAST), label = "choice-ring")
    Column(
        modifier
            .flatSurface(AppTheme.controlFill, ring, AppTheme.shapes.md)
            .pressable(onClick = onClick)
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x1),
        content = content,
    )
}

/** Aviso plano: superficie + filete con un punto del tono; título en tinta y texto en gris. Sin fondos de color. */
@Composable
internal fun PanelNotice(text: String, modifier: Modifier = Modifier, title: String? = null, tone: Tone = Tone.NEUTRAL) {
    val c = AppTheme.colors
    val dot = when (tone) {
        Tone.SUCCESS -> c.positive
        Tone.WARNING -> c.warning
        Tone.DANGER -> c.danger
        Tone.INFO -> c.info
        Tone.PRIMARY -> c.primary
        Tone.NEUTRAL -> c.inkSubtle
    }
    Row(
        modifier.fillMaxWidth().flatSurface(AppTheme.controlFill, c.line, AppTheme.shapes.md).padding(Space.x3),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(dot, Modifier.padding(top = Space.x2 - 1.dp))
        Spacer(Modifier.width(Space.x2))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            if (title != null) AppText(title, style = AppTheme.type.titleSm)
            AppText(text, style = AppTheme.type.bodySm, color = if (title != null) c.inkMuted else c.ink)
        }
    }
}

/** Encabezado de columna de una tabla: etiqueta en mono MAYÚSCULAS. */
@Composable
internal fun ColumnLabel(text: String, modifier: Modifier = Modifier, end: Boolean = false) {
    LabelText(text, modifier = modifier, textAlign = if (end) TextAlign.End else null)
}

/** Renglón de dato: nombre a la izquierda y cifra en mono a la derecha. [strong] = total. */
@Composable
internal fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, Modifier.weight(1f), style = if (strong) AppTheme.type.title else AppTheme.type.bodySm, color = if (strong) AppTheme.colors.ink else AppTheme.colors.inkMuted)
        AppText(value, style = if (strong) AppTheme.type.monoMd else AppTheme.type.mono, maxLines = 1)
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
    loading: Boolean = false,
) {
    AppDialog(onDismiss = onDismiss, title = title, maxWidth = 440.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            AppText(message, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2, Alignment.End)) {
                AppButton("Cancelar", onClick = onDismiss, variant = ButtonVariant.GHOST)
                AppButton(confirmText, onClick = onConfirm, variant = if (danger) ButtonVariant.DANGER else ButtonVariant.PRIMARY, loading = loading)
            }
        }
    }
}

// ───────────────────────── Skeletons ─────────────────────────

@Composable
internal fun SkeletonRows(rows: Int = 5, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            repeat(rows) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    Skeleton(Modifier.size(44.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Skeleton(Modifier.fillMaxWidth(0.45f).height(14.dp))
                        Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp))
                    }
                    Skeleton(Modifier.width(64.dp).height(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun SkeletonCards(count: Int, columns: Int, height: Dp = 112.dp) {
    SimpleGrid(items = (0 until count).toList(), columns = columns) {
        Skeleton(Modifier.fillMaxWidth().height(height), shape = AppTheme.shapes.lg)
    }
}

// ───────────────────────── Formato ─────────────────────────

/** 150 → "1.5%", 200 → "2%", 125 → "1.25%". */
internal fun formatBps(bps: Int): String {
    val whole = bps / 100
    val frac = bps % 100
    return when {
        frac == 0 -> "$whole%"
        frac % 10 == 0 -> "$whole.${frac / 10}%"
        else -> "$whole.${frac.toString().padStart(2, '0')}%"
    }
}

/** "185" / "185.5" / "$1,250.00" → centavos; null si no es un importe válido. */
internal fun parsePesosToCents(text: String): Long? {
    val t = text.trim().replace("$", "").replace(",", "").replace(" ", "")
    if (t.isEmpty()) return null
    val parts = t.split(".")
    if (parts.size > 2) return null
    val wholeText = parts[0].ifEmpty { "0" }
    if (!wholeText.all { it.isDigit() } || wholeText.length > 9) return null
    val fracText = if (parts.size == 2) parts[1] else ""
    if (fracText.length > 2 || !fracText.all { it.isDigit() }) return null
    return wholeText.toLong() * 100 + fracText.padEnd(2, '0').toLong()
}

internal fun centsToPesosText(cents: Long): String {
    val whole = cents / 100
    val frac = (cents % 100).toInt()
    return if (frac == 0) whole.toString() else "$whole." + frac.toString().padStart(2, '0')
}

internal fun OrderStatus.tone(): Tone = when (this) {
    OrderStatus.PENDING_PAYMENT -> Tone.NEUTRAL
    OrderStatus.PAID -> Tone.INFO
    OrderStatus.PREPARING -> Tone.WARNING
    OrderStatus.READY -> Tone.SUCCESS
    OrderStatus.COMPLETED -> Tone.NEUTRAL
    OrderStatus.CANCELLED -> Tone.DANGER
    OrderStatus.REFUNDED -> Tone.DANGER
}

internal fun OrderStatus.isActive(): Boolean = this == OrderStatus.PAID || this == OrderStatus.PREPARING || this == OrderStatus.READY

internal fun Role.tone(): Tone = when (this) {
    Role.OWNER -> Tone.PRIMARY
    Role.MANAGER -> Tone.INFO
    Role.CASHIER -> Tone.SUCCESS
    Role.KITCHEN -> Tone.WARNING
    Role.OPERATOR -> Tone.NEUTRAL
}
