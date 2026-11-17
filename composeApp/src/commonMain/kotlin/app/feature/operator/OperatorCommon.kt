package app.feature.operator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.TenantStatus
import app.shared.util.nowMillis
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import app.ui.theme.parseHexColor
import kotlin.math.roundToLong

/** Alto útil de la consola; los diálogos lo usan para acotar su zona con scroll. */
internal val LocalOpViewportHeight = compositionLocalOf { 720.dp }

internal enum class OpSection(val id: String, val label: String, val shortLabel: String) {
    OVERVIEW("resumen", "Resumen", "Resumen"),
    TENANTS("restaurantes", "Restaurantes", "Restaurantes"),
    PLANS("planes", "Planes", "Planes"),
    SUBSCRIPTIONS("suscripciones", "Suscripciones", "Suscripciones"),
    SUPPORT("soporte", "Soporte", "Soporte"),
    CREDENTIALS("credenciales", "Credenciales maestras", "Credenciales");

    companion object {
        fun of(id: String): OpSection = entries.firstOrNull { it.id == id } ?: OVERVIEW
    }
}

// ───────────────────────── Layout ─────────────────────────

/** Contenedor de página: scroll vertical, ancho máximo cómodo y aire según el tamaño de ventana. */
@Composable
internal fun OpPage(content: @Composable ColumnScope.() -> Unit) {
    val pad = when (LocalWindowSize.current) {
        WindowSize.COMPACT -> Space.x4
        WindowSize.MEDIUM -> Space.x6
        WindowSize.EXPANDED -> Space.x8
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scroll = if (constraints.hasBoundedHeight) {
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Box(Modifier.fillMaxWidth().then(scroll), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = pad, bottom = pad + Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x6),
                content = content,
            )
        }
    }
}

/** Superficie con borde fino para tablas y tarjetas propias de la consola. */
@Composable
internal fun OpPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = AppTheme.colors.line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(AppTheme.shapes.lg)
            .background(AppTheme.colors.surface)
            .border(1.dp, borderColor, AppTheme.shapes.lg),
        content = content,
    )
}

/** Rejilla simple de N columnas (sin lazy: las listas de la consola son cortas). */
@Composable
internal fun <T> OpGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = Space.x4,
    equalHeight: Boolean = false,
    cell: @Composable (T) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(cols).forEach { rowItems ->
            val rowModifier = if (equalHeight && cols > 1) Modifier.height(IntrinsicSize.Min) else Modifier
            Row(rowModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowItems.forEach { item ->
                    val cellModifier = if (equalHeight && cols > 1) Modifier.fillMaxHeight() else Modifier
                    Box(Modifier.weight(1f).then(cellModifier)) { cell(item) }
                }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ───────────────────────── Tabla ─────────────────────────

internal data class OpColumn(val title: String, val weight: Float, val end: Boolean = false)

@Composable
internal fun OpTableHeader(columns: List<OpColumn>, trailing: Dp = 0.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceSunken)
            .padding(horizontal = Space.x5, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { column ->
            AppText(
                column.title.uppercase(),
                Modifier.weight(column.weight).padding(end = Space.x3),
                style = AppTheme.type.label,
                color = AppTheme.colors.inkSubtle,
                maxLines = 1,
                textAlign = if (column.end) TextAlign.End else TextAlign.Start,
            )
        }
        if (trailing > 0.dp) Spacer(Modifier.width(trailing))
    }
}

@Composable
internal fun RowScope.OpCell(column: OpColumn, content: @Composable () -> Unit) {
    Box(
        Modifier.weight(column.weight).padding(end = Space.x3),
        contentAlignment = if (column.end) Alignment.CenterEnd else Alignment.CenterStart,
    ) { content() }
}

// ───────────────────────── Piezas pequeñas ─────────────────────────

@Composable
internal fun OpIcon(icon: ImageVector, tint: Color = AppTheme.colors.inkMuted, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Monograma del restaurante sobre su color de marca (o tinta si es neutro). */
@Composable
internal fun TenantMonogram(name: String, hex: String?, size: Dp = 36.dp) {
    val colors = AppTheme.colors
    val brand = parseHexColor(hex) ?: colors.ink
    val darkInk = if (colors.isDark) colors.canvas else colors.ink
    val lightInk = if (colors.isDark) colors.ink else colors.surface
    val on = if (brand.luminance() > 0.55f) darkInk else lightInk
    Box(
        Modifier.size(size).clip(AppTheme.shapes.sm).background(brand),
        contentAlignment = Alignment.Center,
    ) {
        AppText(name.trim().take(1).uppercase().ifEmpty { "·" }, style = AppTheme.type.title, color = on)
    }
}

@Composable
internal fun BrandDot(hex: String?, size: Dp = 10.dp) {
    val color = parseHexColor(hex) ?: AppTheme.colors.ink
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, AppTheme.colors.line, CircleShape))
}

@Composable
internal fun OpKeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        AppText(label, style = AppTheme.type.label, color = AppTheme.colors.inkSubtle)
        AppText(value, style = AppTheme.type.body)
    }
}

@Composable
internal fun monoStyle(): TextStyle = AppTheme.type.bodySm.copy(fontFamily = FontFamily.Monospace)

/** Dato copiable (links, credenciales) sobre fondo hundido. */
@Composable
internal fun OpCopyRow(label: String, value: String, modifier: Modifier = Modifier) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.md)
            .background(AppTheme.colors.surfaceSunken)
            .padding(start = Space.x4, end = Space.x2, top = Space.x2, bottom = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            AppText(label, style = AppTheme.type.label, color = AppTheme.colors.inkSubtle)
            AppText(value, style = monoStyle().copy(fontSize = AppTheme.type.body.fontSize), maxLines = 1)
        }
        AppIconButton(
            AppIcons.Copy,
            onClick = {
                clipboard.setText(AnnotatedString(value))
                Toasts.show("Copiado al portapapeles", Tone.SUCCESS)
            },
            contentDescription = "Copiar $label",
            tint = AppTheme.colors.inkMuted,
        )
    }
}

/** Aviso informativo de una línea (banda suave). */
@Composable
internal fun OpNotice(text: String, modifier: Modifier = Modifier, icon: ImageVector = AppIcons.Info) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.md)
            .background(AppTheme.colors.infoSoft)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpIcon(icon, tint = AppTheme.colors.info, size = 20.dp)
        AppText(text, Modifier.weight(1f), style = AppTheme.type.bodySm, color = AppTheme.colors.ink)
    }
}

// ───────────────────────── Formato ─────────────────────────

internal fun TenantStatus.tone(): Tone = when (this) {
    TenantStatus.TRIAL -> Tone.INFO
    TenantStatus.ACTIVE -> Tone.SUCCESS
    TenantStatus.PAST_DUE -> Tone.WARNING
    TenantStatus.SUSPENDED -> Tone.DANGER
}

internal fun TenantStatus.billingLabel(): String = when (this) {
    TenantStatus.TRIAL -> "Periodo de prueba"
    TenantStatus.ACTIVE -> "Al corriente"
    TenantStatus.PAST_DUE -> "Cobro rechazado"
    TenantStatus.SUSPENDED -> "Pausada"
}

/** 2.5 -> "2.5%", 12.0 -> "12%". */
internal fun formatPct(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val abs = if (tenths < 0) -tenths else tenths
    val body = if (abs % 10 == 0L) (abs / 10).toString() else "${abs / 10}.${abs % 10}"
    return (if (tenths < 0) "-" else "") + body + "%"
}

internal fun formatSignedPct(value: Double): String = (if (value >= 0) "+" else "") + formatPct(value)

/** Puntos base -> porcentaje: 250 -> "2.5%". */
internal fun formatBps(bps: Int): String {
    val whole = bps / 100
    val frac = bps % 100
    val body = when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
    return "$body%"
}

internal fun formatCount(n: Int): String {
    val abs = if (n < 0) -n else n
    return (if (n < 0) "-" else "") + abs.toString().reversed().chunked(3).joinToString(",").reversed()
}

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
internal const val DAY_MS = 86_400_000L

/** "5 nov 2026" (UTC), sin APIs de plataforma. */
internal fun formatDate(epochMs: Long): String {
    val days = epochMs.floorDiv(DAY_MS)
    val z = days + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return "$d ${MONTHS[(m - 1).toInt().coerceIn(0, 11)]} $year"
}

/**
 * Próxima fecha relevante de cobro, derivada del alta (ciclos de 30 días; prueba de 14 días).
 * Es un cálculo de presentación: la fecha real la dicta el preapproval de Mercado Pago.
 */
internal fun nextBillingLabel(status: TenantStatus, createdAt: Long, now: Long = nowMillis()): String {
    val start = if (createdAt in 1..now) createdAt else now
    val cycle = 30 * DAY_MS
    val elapsedCycles = (now - start) / cycle
    val next = start + (elapsedCycles + 1) * cycle
    return when (status) {
        TenantStatus.ACTIVE -> formatDate(next)
        TenantStatus.TRIAL -> {
            val trialEnd = start + 14 * DAY_MS
            "Fin de prueba · " + formatDate(if (trialEnd > now) trialEnd else next)
        }
        TenantStatus.PAST_DUE -> "Reintento · " + formatDate(now + 2 * DAY_MS)
        TenantStatus.SUSPENDED -> "Sin cobro programado"
    }
}
