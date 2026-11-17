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

/** Paleta completa del sistema "Lienzo": papel y tinta neutros + un solo color de marca. */
@Immutable
data class AppColors(
    val canvas: Color, val surface: Color, val surfaceRaised: Color, val surfaceSunken: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color, val inkSubtle: Color,
    val primary: Color, val onPrimary: Color, val primarySoft: Color,
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    val successSoft: Color, val warningSoft: Color, val dangerSoft: Color, val infoSoft: Color,
    val scrim: Color, val isDark: Boolean,
)

/** Escala tipográfica; display/h1/h2/h3 usan la fuente display del tenant. */
@Immutable
data class AppTypography(
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val title: TextStyle, val body: TextStyle, val bodySm: TextStyle, val label: TextStyle,
)

@Immutable
data class AppShapes(val xs: Shape, val sm: Shape, val md: Shape, val lg: Shape, val control: Shape, val pill: Shape)

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
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 600f)
}

/** Preferencia de modo oscuro del usuario; `null` = seguir al tenant / sistema. */
object ThemeController {
    var darkOverride: Boolean? by mutableStateOf(null)

    fun toggle(currentIsDark: Boolean) {
        darkOverride = !currentIsDark
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
