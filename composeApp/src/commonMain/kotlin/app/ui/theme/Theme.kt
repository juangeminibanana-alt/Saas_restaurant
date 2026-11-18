package app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.resources.Res
import app.resources.geist_medium
import app.resources.geist_mono_medium
import app.resources.geist_mono_semibold
import app.resources.geist_regular
import app.resources.geist_semibold
import app.shared.model.BrandTheme
import org.jetbrains.compose.resources.Font

// ───────────────────────── NOCHE: color ─────────────────────────
// La interfaz es negra, plana y exacta; la fotografía es lo único que brilla.

private val Bg = Color(0xFF0B0B0C)                // fondo
private val Surface1 = Color(0xFF141416)          // controles, mosaicos, barra
private val Surface2 = Color(0xFF1B1B1E)          // control sobre superficie 1
private val Line = Color(0x14FFFFFF)              // filete: blanco al 8 %
private val LineStrong = Color(0x24FFFFFF)        // filete sobre fotografía: blanco al 14 %
private val Ink = Color(0xFFF2F2F3)               // texto principal   17.6 : 1
private val Muted = Color(0xFFA1A1A8)             // texto secundario   7.6 : 1
private val Faint = Color(0xFF8A8A92)             // etiquetas          5.6 : 1
private val Positive = Color(0xFF3ECF8E)          // "abierto" / correcto
private val Warning = Color(0xFFF2B84B)           // sólo paneles: atención
private val Danger = Color(0xFFF2555A)            // sólo paneles: error / destructivo
private val Info = Color(0xFF6CB4FF)              // sólo paneles: informativo
private val Scrim = Color(0x99000000)             // velo de diálogos y hojas: negro al 60 %

/** Brasa del restaurante de demostración; es el acento cuando el tenant no define el suyo. */
val DefaultAccent = Color(0xFFFF6A3D)

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Texto sobre un color sólido: tinta oscura sobre un color luminoso, tinta clara sobre uno profundo. */
internal fun contentColorOn(background: Color): Color =
    if (contrast(background, Bg) >= contrast(background, Ink)) Bg else Ink

/** El acento del tenant debe verse sobre negro: uno demasiado oscuro se aclara hasta 3 : 1 contra el fondo. */
private fun accentFor(brand: Color?): Color {
    var accent = (brand ?: DefaultAccent).copy(alpha = 1f)
    var t = 0f
    while (t < 1f && contrast(accent, Bg) < 3f) {
        t += 0.04f
        accent = lerp(brand ?: DefaultAccent, Color.White, t).copy(alpha = 1f)
    }
    return accent
}

/** Tinte plano y opaco de un color sobre superficie 1 (nunca translúcido, nunca degradado). */
private fun flatTint(color: Color): Color = color.copy(alpha = 0.14f).compositeOver(Surface1)

private fun nochePalette(accent: Color): AppColors = AppColors(
    canvas = Bg, surface = Surface1, surfaceRaised = Surface2, surfaceSunken = Surface2,
    line = Line, lineStrong = LineStrong,
    ink = Ink, inkMuted = Muted, inkSubtle = Faint,
    primary = accent, onPrimary = contentColorOn(accent), primarySoft = flatTint(accent),
    success = Positive, warning = Warning, danger = Danger, info = Info,
    successSoft = flatTint(Positive), warningSoft = flatTint(Warning),
    dangerSoft = flatTint(Danger), infoSoft = flatTint(Info),
    scrim = Scrim, isDark = true,
    select = Ink, onSelect = Bg,
    positive = Positive,
)

// ───────────────────────── NOCHE: tipografía ─────────────────────────
// Geist (400/500/600) para todo; Geist Mono (500/600) para precios, conteos, horas y etiquetas de 11.

@Composable
private fun geistSans(): FontFamily {
    val r = Font(Res.font.geist_regular, FontWeight.Normal)
    val m = Font(Res.font.geist_medium, FontWeight.Medium)
    val s = Font(Res.font.geist_semibold, FontWeight.SemiBold)
    return remember(r, m, s) { FontFamily(r, m, s) }
}

@Composable
private fun geistMono(): FontFamily {
    val m = Font(Res.font.geist_mono_medium, FontWeight.Medium)
    val s = Font(Res.font.geist_mono_semibold, FontWeight.SemiBold)
    return remember(m, s) { FontFamily(m, s) }
}

private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Escala 40 · 32 · 20 · 16 · 15 · 13 · 11; interlíneas en múltiplos de 4; tracking cerrado en lo grande. */
private fun buildTypography(sans: FontFamily, mono: FontFamily): AppTypography {
    fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, trackingEm: Float) = TextStyle(
        fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = trackingEm.em, lineHeightStyle = CenteredLines,
    )
    return AppTypography(
        display = style(sans, 40, 44, FontWeight.SemiBold, -0.04f),     // nombre del restaurante
        h1 = style(sans, 32, 36, FontWeight.SemiBold, -0.035f),         // nombre del platillo, título de página
        h2 = style(sans, 20, 24, FontWeight.SemiBold, -0.02f),          // título de sección
        h3 = style(sans, 16, 20, FontWeight.SemiBold, -0.011f),         // título de grupo, texto de botón
        title = style(sans, 16, 20, FontWeight.Medium, -0.011f),        // nombre en fila o mosaico grande
        body = style(sans, 16, 24, FontWeight.Normal, -0.011f),         // texto corrido
        bodySm = style(sans, 13, 20, FontWeight.Normal, 0f),            // descripción, dato secundario
        label = style(sans, 11, 16, FontWeight.Medium, 0.01f),          // nota mínima en sans
        titleSm = style(sans, 15, 20, FontWeight.Medium, -0.015f),      // nombre en mosaico chico
        monoLabel = style(mono, 11, 16, FontWeight.Medium, 0.06f),      // etiqueta en MAYÚSCULAS (LabelText)
        mono = style(mono, 13, 20, FontWeight.Medium, 0f),              // precios, conteos, tiempos
        monoMd = style(mono, 16, 20, FontWeight.Medium, -0.02f),        // precio en mosaico, cantidad grande
        monoLg = style(mono, 20, 24, FontWeight.Medium, -0.02f),        // total, precio del platillo
    )
}

// ───────────────────────── NOCHE: radios ─────────────────────────
// Un solo sistema: 4 / 8 / 12 / 16, interior = exterior − 4.

private val NocheShapes = AppShapes(
    xs = RoundedCornerShape(4.dp),        // ficha dentro de un control de 8
    sm = RoundedCornerShape(8.dp),        // segmento activo, botón de fila
    md = RoundedCornerShape(12.dp),       // controles, miniaturas
    lg = RoundedCornerShape(16.dp),       // mosaicos, tarjetas, diálogos
    control = RoundedCornerShape(12.dp),
    pill = RoundedCornerShape(8.dp),      // NOCHE no tiene cápsulas: alias heredado de sm
)

// ───────────────────────── Locals ─────────────────────────

private val FallbackColors = nochePalette(DefaultAccent)
private val FallbackTypography = buildTypography(FontFamily.Default, FontFamily.Monospace)

internal val LocalAppColors = staticCompositionLocalOf { FallbackColors }
internal val LocalAppTypography = staticCompositionLocalOf { FallbackTypography }
internal val LocalAppShapes = staticCompositionLocalOf { NocheShapes }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalAppShapes.current

    /**
     * Relleno de un control (botón secundario, campo, stepper, chip) según dónde vive: superficie 1 sobre
     * el fondo, superficie 2 dentro de una tarjeta, hoja o barra, y de vuelta superficie 1 si el contenedor
     * ya es superficie 2. Nunca se funde con lo que lo contiene. Ver [LocalSurfaceLevel].
     */
    val controlFill: Color
        @Composable @ReadOnlyComposable get() =
            if (LocalSurfaceLevel.current == 1) LocalAppColors.current.surfaceRaised else LocalAppColors.current.surface
}

// ───────────────────────── AppTheme ─────────────────────────

/**
 * Tema NOCHE. Marca blanca: del tenant sólo entra el color de acento (`brand.primary`, por defecto la brasa
 * #FF6A3D); el texto sobre el acento se calcula por contraste. La tipografía (Geist), los radios (4/8/12/16)
 * y el modo oscuro los fija el sistema: `fontDisplay`, `fontBody`, `radius` y `mode` del tenant se ignoran.
 * No pinta fondo: la raíz de cada pantalla pinta `AppTheme.colors.canvas`.
 */
@Composable
fun AppTheme(brand: BrandTheme? = null, content: @Composable () -> Unit) {
    val accent = remember(brand?.primary) { accentFor(parseHexColor(brand?.primary)) }
    val colors = remember(accent) { nochePalette(accent) }

    val sans = geistSans()
    val mono = geistMono()
    val type = remember(sans, mono) { buildTypography(sans, mono) }
    val shapes = NocheShapes

    // Puente a Material 3 para que cualquier componente Material herede los tokens de NOCHE.
    val scheme = remember(colors) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.surfaceRaised, onPrimaryContainer = colors.ink,
            secondary = colors.inkMuted, onSecondary = colors.canvas,
            secondaryContainer = colors.surfaceRaised, onSecondaryContainer = colors.ink,
            tertiary = colors.info, onTertiary = colors.canvas,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.inkMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.select, inverseOnSurface = colors.onSelect,
            error = colors.danger, onError = contentColorOn(colors.danger),
            errorContainer = colors.dangerSoft, onErrorContainer = colors.danger,
            outline = colors.lineStrong, outlineVariant = colors.line, scrim = colors.scrim,
        )
    }
    val materialType = remember(type) {
        Typography(
            displayLarge = type.display, displayMedium = type.display, displaySmall = type.h1,
            headlineLarge = type.h1, headlineMedium = type.h2, headlineSmall = type.h2,
            titleLarge = type.h2, titleMedium = type.h3, titleSmall = type.title,
            bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.bodySm,
            labelLarge = type.h3, labelMedium = type.label, labelSmall = type.label,
        )
    }
    val materialShapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(16.dp),
        )
    }
    // La selección de texto es tinta, no brasa: el acento se reserva para "tu pedido".
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.ink, backgroundColor = colors.ink.copy(alpha = 0.24f))
    }

    MaterialTheme(colorScheme = scheme, typography = materialType, shapes = materialShapes) {
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppTypography provides type,
            LocalAppShapes provides shapes,
            LocalContentColor provides colors.ink,
            androidx.compose.material3.LocalContentColor provides colors.ink,
            androidx.compose.material3.LocalTextStyle provides type.body.copy(color = colors.ink),
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
