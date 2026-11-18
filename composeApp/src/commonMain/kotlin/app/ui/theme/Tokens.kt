package app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Paleta de NOCHE ("oscuro de precisión"). La interfaz es negra, plana y exacta; lo único que cambia
 * entre restaurantes es [primary] / [onPrimary] (la "brasa").
 *
 * - [canvas] fondo · [surface] superficie 1 (controles, mosaicos, barras) · [surfaceRaised] superficie 2
 *   (control sobre superficie 1) · [surfaceSunken] alias heredado de superficie 2 (NOCHE no tiene hundidos).
 * - [line] filete de 1 px · [lineStrong] filete sobre fotografía o para dar énfasis.
 * - [ink] texto principal · [inkMuted] secundario · [inkSubtle] etiquetas.
 * - [primary] brasa: significa "tu pedido" y sólo existe como color sólido (nunca contorno).
 * - [select] / [onSelect] la selección se marca con tinta sólida.
 * - [positive] único semántico del menú ("abierto"); [success] es su alias para paneles.
 */
@Immutable
data class AppColors(
    val canvas: Color, val surface: Color, val surfaceRaised: Color, val surfaceSunken: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color, val inkSubtle: Color,
    val primary: Color, val onPrimary: Color, val primarySoft: Color,
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    val successSoft: Color, val warningSoft: Color, val dangerSoft: Color, val infoSoft: Color,
    val scrim: Color, val isDark: Boolean,
    val select: Color = ink, val onSelect: Color = canvas,
    val positive: Color = success,
)

/**
 * Escala tipográfica de NOCHE: 40 · 32 · 20 · 16 · 15 · 13 · 11, interlíneas en múltiplos de 4.
 * Todo es Geist; las cifras (precios, conteos, horas) y las etiquetas de 11 van en Geist Mono.
 *
 * display 40/44 · h1 32/36 · h2 20/24 · h3 16/20 seminegrita · title 16/20 media · titleSm 15/20 media ·
 * body 16/24 · bodySm 13/20 · label 11/16 (sans) · monoLabel 11/16 (+0.06em, va en MAYÚSCULAS: usa `LabelText`) ·
 * mono 13/20 · monoMd 16/20 · monoLg 20/24.
 */
@Immutable
data class AppTypography(
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val title: TextStyle, val body: TextStyle, val bodySm: TextStyle, val label: TextStyle,
    val titleSm: TextStyle = title,
    val monoLabel: TextStyle = label,
    val mono: TextStyle = bodySm,
    val monoMd: TextStyle = title,
    val monoLg: TextStyle = h2,
)

/** Radios de NOCHE: xs 4 · sm 8 · md 12 · lg 16 (interior = exterior − 4). `control` = md; `pill` = sm (no hay cápsulas). */
@Immutable
data class AppShapes(val xs: Shape, val sm: Shape, val md: Shape, val lg: Shape, val control: Shape, val pill: Shape)

/** Retícula de 4 px. El margen de página es [gutter] (16) y el objetivo táctil mínimo [tap] (44). */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x14 = 56.dp
    val x18 = 72.dp

    /** Margen lateral de página. */
    val gutter = 16.dp

    /** Objetivo táctil mínimo. */
    val tap = 44.dp
}

/** Medidas fijas de los componentes de NOCHE (dp). */
object Sizes {
    /** Filete. */
    val hairline = 1.dp

    /** Ícono estándar (trazo ≈ 1.5 px a este tamaño). */
    val icon = 20.dp

    /** Cara visible de un control de fila (agregar, stepper chico). */
    val controlSm = 32.dp

    /** Control estándar: botón de ícono, segmentado, campo compacto. */
    val control = 44.dp

    /** Control alto: botón principal, campo de texto, fila de opción, stepper grande. */
    val controlLg = 48.dp

    /** Botón de 56 para pantallas de un solo paso (pagar, entrar). */
    val controlXl = 56.dp

    /** Barra inferior y navegación pegada. */
    val bar = 72.dp

    /** Miniatura de fila de platillo. */
    val thumb = 80.dp
}

object Motion {
    const val FAST = 120
    const val BASE = 220
    const val SLOW = 360
    const val HERO = 520

    /** cubic-bezier(.2,0,0,1) */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** cubic-bezier(.4,0,1,1) */
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 500f)
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 200f)

    /** NOCHE no rebota: se conserva por compatibilidad y equivale a un resorte firme sin sobrepaso notorio. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 600f)
}

/**
 * NOCHE es sólo oscuro: se conserva por compatibilidad y ya no cambia nada en pantalla.
 * `AppTheme.colors.isDark` siempre es `true`.
 */
object ThemeController {
    var darkOverride: Boolean? by mutableStateOf(null)

    @Suppress("UNUSED_PARAMETER")
    fun toggle(currentIsDark: Boolean) {
        // Sin efecto: el modo oscuro lo fija el sistema.
    }
}

/** Acepta "#RGB", "#RRGGBB" y "#AARRGGBB" (con o sin '#'). Devuelve null si no es válido. */
fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (raw.length) {
        3 -> "FF" + raw.map { "$it$it" }.joinToString("")
        6 -> "FF$raw"
        8 -> raw
        else -> return null
    }
    val value = full.toLongOrNull(16) ?: return null
    return Color(value.toInt())
}

enum class WindowSize { COMPACT, MEDIUM, EXPANDED }

val LocalWindowSize: ProvidableCompositionLocal<WindowSize> = compositionLocalOf { WindowSize.EXPANDED }

/** Color de contenido heredado (texto/íconos) dentro de superficies de color, p. ej. botones. */
val LocalContentColor: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Unspecified }

/**
 * Nivel de la superficie que contiene al contenido: 0 = fondo, 1 = superficie 1, 2 = superficie 2.
 * Los controles se pintan un nivel arriba de su contenedor (`AppTheme.controlFill`), así un botón
 * secundario o un stepper dentro de una tarjeta o de la barra inferior nunca se funde con ella.
 * Lo proveen `AppCard`, los diálogos, las hojas y `BottomBar`.
 */
val LocalSurfaceLevel: ProvidableCompositionLocal<Int> = compositionLocalOf { 0 }
