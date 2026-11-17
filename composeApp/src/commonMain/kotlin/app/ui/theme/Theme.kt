package app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resources.Res
import app.resources.fraunces_bold
import app.resources.fraunces_regular
import app.resources.fraunces_semibold
import app.resources.inter_bold
import app.resources.inter_medium
import app.resources.inter_regular
import app.resources.inter_semibold
import app.resources.nunito_extrabold
import app.resources.nunito_regular
import app.resources.nunito_semibold
import app.resources.space_grotesk_bold
import app.resources.space_grotesk_medium
import app.resources.space_grotesk_regular
import app.shared.model.BrandTheme
import app.shared.model.FontChoice
import app.shared.model.RadiusStyle
import app.shared.model.ThemeMode
import org.jetbrains.compose.resources.Font

// ───────────────────────── Paletas base ─────────────────────────

private val InkLight = Color(0xFF0B0B0F)
private val InkDark = Color(0xFFF5F5F7)

private fun lightPalette(brandPrimary: Color?): AppColors {
    val surface = Color(0xFFFFFFFF)
    var primary = brandPrimary ?: Color(0xFFFF5B2E) // acento por defecto de la plataforma (Obsidiana)
    // Un color de marca casi blanco no se vería sobre papel: se oscurece lo justo.
    if (primary.luminance() > 0.78f) {
        var t = 0f
        while (t < 1f && lerp(primary, InkLight, t).luminance() > 0.6f) t += 0.05f
        primary = lerp(primary, InkLight, t)
    }
    val success = Color(0xFF0F9D6B)
    val warning = Color(0xFFB45309)
    val danger = Color(0xFFDC2626)
    val info = Color(0xFF2563EB)
    return AppColors(
        canvas = Color(0xFFF7F7F5), surface = surface, surfaceRaised = Color(0xFFFFFFFF), surfaceSunken = Color(0xFFEDEDEA),
        line = Color(0xFFE2E2DD), lineStrong = Color(0xFFC9C9C2),
        ink = InkLight, inkMuted = Color(0xFF4B4B57), inkSubtle = Color(0xFF6B6B78),
        primary = primary, onPrimary = contentColorOn(primary), primarySoft = primary.copy(alpha = 0.10f).compositeOver(surface),
        success = success, warning = warning, danger = danger, info = info,
        successSoft = success.copy(alpha = 0.11f).compositeOver(surface),
        warningSoft = warning.copy(alpha = 0.13f).compositeOver(surface),
        dangerSoft = danger.copy(alpha = 0.10f).compositeOver(surface),
        infoSoft = info.copy(alpha = 0.10f).compositeOver(surface),
        scrim = InkLight.copy(alpha = 0.42f), isDark = false,
    )
}

private fun darkPalette(brandPrimary: Color?): AppColors {
    val surface = Color(0xFF0E0E13)
    var primary = brandPrimary ?: Color(0xFFFF5B2E) // acento por defecto de la plataforma (Obsidiana)
    // Un color de marca demasiado oscuro se pierde sobre fondos casi negros: se aclara hasta ser visible.
    if (primary.luminance() < 0.2f) {
        var t = 0f
        while (t < 1f && lerp(primary, Color.White, t).luminance() < 0.2f) t += 0.04f
        primary = lerp(primary, Color.White, t)
    }
    val success = Color(0xFF34D399)
    val warning = Color(0xFFFBBF24)
    val danger = Color(0xFFF87171)
    val info = Color(0xFF60A5FA)
    return AppColors(
        canvas = Color(0xFF07070A), surface = surface, surfaceRaised = Color(0xFF15151C), surfaceSunken = Color(0xFF050507),
        line = Color(0xFF262631), lineStrong = Color(0xFF3A3A48),
        ink = InkDark, inkMuted = Color(0xFFB4B4C2), inkSubtle = Color(0xFF7C7C8E),
        primary = primary, onPrimary = contentColorOn(primary), primarySoft = primary.copy(alpha = 0.16f).compositeOver(surface),
        success = success, warning = warning, danger = danger, info = info,
        successSoft = success.copy(alpha = 0.16f).compositeOver(surface),
        warningSoft = warning.copy(alpha = 0.16f).compositeOver(surface),
        dangerSoft = danger.copy(alpha = 0.16f).compositeOver(surface),
        infoSoft = info.copy(alpha = 0.16f).compositeOver(surface),
        scrim = Color.Black.copy(alpha = 0.62f), isDark = true,
    )
}

/** Blanco o casi negro según contraste (blanco mientras cumpla ≥ 3:1, texto en negritas / UI). */
internal fun contentColorOn(background: Color): Color {
    val contrastWithWhite = 1.05f / (background.luminance() + 0.05f)
    return if (contrastWithWhite >= 4.5f) Color.White else InkLight
}

// ───────────────────────── Tipografía ─────────────────────────

@Composable
private fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.INTER -> {
        val r = Font(Res.font.inter_regular, FontWeight.Normal)
        val m = Font(Res.font.inter_medium, FontWeight.Medium)
        val s = Font(Res.font.inter_semibold, FontWeight.SemiBold)
        val b = Font(Res.font.inter_bold, FontWeight.Bold)
        remember(r, m, s, b) { FontFamily(r, m, s, b) }
    }
    FontChoice.FRAUNCES -> {
        val r = Font(Res.font.fraunces_regular, FontWeight.Normal)
        val m = Font(Res.font.fraunces_semibold, FontWeight.Medium)
        val s = Font(Res.font.fraunces_semibold, FontWeight.SemiBold)
        val b = Font(Res.font.fraunces_bold, FontWeight.Bold)
        remember(r, m, s, b) { FontFamily(r, m, s, b) }
    }
    FontChoice.SPACE_GROTESK -> {
        val r = Font(Res.font.space_grotesk_regular, FontWeight.Normal)
        val m = Font(Res.font.space_grotesk_medium, FontWeight.Medium)
        val s = Font(Res.font.space_grotesk_bold, FontWeight.SemiBold)
        val b = Font(Res.font.space_grotesk_bold, FontWeight.Bold)
        remember(r, m, s, b) { FontFamily(r, m, s, b) }
    }
    FontChoice.NUNITO -> {
        val r = Font(Res.font.nunito_regular, FontWeight.Normal)
        val m = Font(Res.font.nunito_semibold, FontWeight.Medium)
        val s = Font(Res.font.nunito_semibold, FontWeight.SemiBold)
        val b = Font(Res.font.nunito_extrabold, FontWeight.Bold)
        val x = Font(Res.font.nunito_extrabold, FontWeight.ExtraBold)
        remember(r, m, s, b, x) { FontFamily(r, m, s, b, x) }
    }
}

private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun buildTypography(display: FontFamily, body: FontFamily, displayChoice: FontChoice): AppTypography {
    // La serif se ve más fina en semibold; las sans piden bold para titulares.
    val heavy = if (displayChoice == FontChoice.FRAUNCES) FontWeight.SemiBold else FontWeight.Bold
    val tracking = if (displayChoice == FontChoice.FRAUNCES) 0.6f else 1f
    fun displayStyle(size: Int, line: Int, weight: FontWeight, letter: Float) = TextStyle(
        fontFamily = display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = (letter * tracking).sp, lineHeightStyle = CenteredLines,
    )
    fun bodyStyle(size: Int, line: Int, weight: FontWeight, letter: Float) = TextStyle(
        fontFamily = body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = letter.sp, lineHeightStyle = CenteredLines,
        fontFeatureSettings = "tnum", // cifras tabulares: precios y totales alineados
    )
    return AppTypography(
        display = displayStyle(44, 48, heavy, -1.1f),
        h1 = displayStyle(32, 38, heavy, -0.7f),
        h2 = displayStyle(24, 30, FontWeight.SemiBold, -0.4f),
        h3 = displayStyle(20, 26, FontWeight.SemiBold, -0.25f),
        title = bodyStyle(17, 24, FontWeight.SemiBold, -0.15f),
        body = bodyStyle(15, 22, FontWeight.Normal, 0f),
        bodySm = bodyStyle(13, 18, FontWeight.Normal, 0f),
        label = bodyStyle(12, 16, FontWeight.Medium, 0.1f),
    )
}

// ───────────────────────── Formas ─────────────────────────

private class RadiusSet(val xs: Dp, val sm: Dp, val md: Dp, val lg: Dp, val control: Dp?)

private fun radiusSetFor(style: RadiusStyle): RadiusSet = when (style) {
    RadiusStyle.SHARP -> RadiusSet(2.dp, 4.dp, 6.dp, 8.dp, 4.dp)
    RadiusStyle.SOFT -> RadiusSet(6.dp, 10.dp, 14.dp, 18.dp, 10.dp)
    RadiusStyle.ROUND -> RadiusSet(10.dp, 16.dp, 22.dp, 28.dp, 14.dp)
    RadiusStyle.PILL -> RadiusSet(14.dp, 22.dp, 30.dp, 30.dp, null)
}

private fun buildShapes(r: RadiusSet): AppShapes = AppShapes(
    xs = RoundedCornerShape(r.xs),
    sm = RoundedCornerShape(r.sm),
    md = RoundedCornerShape(r.md),
    lg = RoundedCornerShape(r.lg),
    control = if (r.control != null) RoundedCornerShape(r.control) else RoundedCornerShape(percent = 50),
    pill = RoundedCornerShape(percent = 50),
)

// ───────────────────────── Locals ─────────────────────────

private val NeutralLight = lightPalette(null)

private val FallbackTypography = buildTypography(FontFamily.Default, FontFamily.Default, FontChoice.INTER)
private val FallbackShapes = buildShapes(radiusSetFor(RadiusStyle.ROUND))

internal val LocalAppColors = staticCompositionLocalOf { NeutralLight }
internal val LocalAppTypography = staticCompositionLocalOf { FallbackTypography }
internal val LocalAppShapes = staticCompositionLocalOf { FallbackShapes }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalAppShapes.current
}

// ───────────────────────── AppTheme ─────────────────────────

@Composable
private fun animated(target: AppColors): AppColors {
    val spec = remember { tween<Color>(durationMillis = Motion.SLOW, easing = Motion.standard) }

    @Composable
    fun a(c: Color): Color = animateColorAsState(c, spec).value
    return AppColors(
        canvas = a(target.canvas), surface = a(target.surface), surfaceRaised = a(target.surfaceRaised),
        surfaceSunken = a(target.surfaceSunken), line = a(target.line), lineStrong = a(target.lineStrong),
        ink = a(target.ink), inkMuted = a(target.inkMuted), inkSubtle = a(target.inkSubtle),
        primary = a(target.primary), onPrimary = a(target.onPrimary), primarySoft = a(target.primarySoft),
        success = a(target.success), warning = a(target.warning), danger = a(target.danger), info = a(target.info),
        successSoft = a(target.successSoft), warningSoft = a(target.warningSoft),
        dangerSoft = a(target.dangerSoft), infoSoft = a(target.infoSoft),
        scrim = a(target.scrim), isDark = target.isDark,
    )
}

/**
 * Tema del sistema de diseño. `brand == null` = neutro de plataforma (tinta sobre papel).
 * No pinta fondo: la raíz de cada pantalla pinta `AppTheme.colors.canvas`.
 */
@Composable
fun AppTheme(brand: BrandTheme? = null, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = ThemeController.darkOverride ?: when (brand?.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> if (brand == null) true else systemDark // Obsidiana es oscuro primero
    }
    val brandPrimary = remember(brand?.primary) { parseHexColor(brand?.primary) }
    val target = remember(dark, brandPrimary) { if (dark) darkPalette(brandPrimary) else lightPalette(brandPrimary) }
    val colors = animated(target)

    val displayChoice = brand?.fontDisplay ?: FontChoice.INTER
    val bodyChoice = brand?.fontBody ?: FontChoice.INTER
    val displayFamily = fontFamilyFor(displayChoice)
    val bodyFamily = fontFamilyFor(bodyChoice)
    val type = remember(displayFamily, bodyFamily, displayChoice) { buildTypography(displayFamily, bodyFamily, displayChoice) }

    val radius = brand?.radius ?: RadiusStyle.ROUND
    val radiusSet = remember(radius) { radiusSetFor(radius) }
    val shapes = remember(radiusSet) { buildShapes(radiusSet) }

    // Puente a Material 3 para que cualquier componente Material se vea coherente con los tokens.
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primarySoft, onPrimaryContainer = colors.ink,
            secondary = colors.inkMuted, onSecondary = colors.canvas,
            secondaryContainer = colors.surfaceRaised, onSecondaryContainer = colors.ink,
            tertiary = colors.info, onTertiary = colors.canvas,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.inkMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.ink, inverseOnSurface = colors.canvas,
            error = colors.danger, onError = contentColorOn(colors.danger),
            errorContainer = colors.dangerSoft, onErrorContainer = colors.danger,
            outline = colors.lineStrong, outlineVariant = colors.line, scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primarySoft, onPrimaryContainer = colors.ink,
            secondary = colors.inkMuted, onSecondary = colors.surface,
            secondaryContainer = colors.surfaceSunken, onSecondaryContainer = colors.ink,
            tertiary = colors.info, onTertiary = colors.surface,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.ink, inverseOnSurface = colors.canvas,
            error = colors.danger, onError = contentColorOn(colors.danger),
            errorContainer = colors.dangerSoft, onErrorContainer = colors.danger,
            outline = colors.lineStrong, outlineVariant = colors.line, scrim = colors.scrim,
        )
    }
    val materialType = remember(type) {
        Typography(
            displayLarge = type.display, displayMedium = type.display, displaySmall = type.h1,
            headlineLarge = type.h1, headlineMedium = type.h2, headlineSmall = type.h3,
            titleLarge = type.h3, titleMedium = type.title, titleSmall = type.body.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.bodySm,
            labelLarge = type.body.copy(fontWeight = FontWeight.SemiBold), labelMedium = type.label, labelSmall = type.label,
        )
    }
    val materialShapes = remember(radiusSet) {
        Shapes(
            extraSmall = RoundedCornerShape(radiusSet.xs),
            small = RoundedCornerShape(radiusSet.sm),
            medium = RoundedCornerShape(radiusSet.md),
            large = RoundedCornerShape(radiusSet.lg),
            extraLarge = RoundedCornerShape(radiusSet.lg),
        )
    }
    val selection = TextSelectionColors(
        handleColor = colors.primary,
        backgroundColor = colors.primary.copy(alpha = if (colors.isDark) 0.36f else 0.22f),
    )

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
