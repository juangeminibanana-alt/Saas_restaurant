package app.feature.panel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shared.api.UpdateBrandRequest
import app.shared.model.BrandTheme
import app.shared.model.FontChoice
import app.shared.model.Menu
import app.shared.model.MenuItem
import app.shared.model.RadiusStyle
import app.shared.model.TenantPublic
import app.shared.model.ThemeMode
import app.shared.util.formatMoney
import app.shell.LocalRepo
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import app.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val brandSwatches = listOf(
    "#B4411F", "#D9363E", "#E07A1F", "#C8932B", "#2F6B4F",
    "#1F7A8C", "#2B5BD7", "#5B3FD6", "#C2357B", "#1F1E1A",
)

/** "#RRGGBB" en mayúsculas a partir de "#rgb", "rrggbb", etc.; null si no es un color válido. */
internal fun normalizeHex(input: String): String? {
    val s = input.trim().removePrefix("#")
    if (s.isEmpty() || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return when (s.length) {
        6 -> "#" + s.uppercase()
        3 -> "#" + s.map { "$it$it" }.joinToString("").uppercase()
        else -> null
    }
}

private fun FontChoice.displayName(): String = when (this) {
    FontChoice.INTER -> "Inter"
    FontChoice.FRAUNCES -> "Fraunces"
    FontChoice.SPACE_GROTESK -> "Space Grotesk"
    FontChoice.NUNITO -> "Nunito"
}

private fun RadiusStyle.displayName(): String = when (this) {
    RadiusStyle.SHARP -> "Recto"
    RadiusStyle.SOFT -> "Suave"
    RadiusStyle.ROUND -> "Redondo"
    RadiusStyle.PILL -> "Cápsula"
}

@Stable
private class BrandDraft(val source: TenantPublic) {
    var name by mutableStateOf(source.name)
    var tagline by mutableStateOf(source.tagline)
    var description by mutableStateOf(source.description)
    var address by mutableStateOf(source.address)
    var phone by mutableStateOf(source.phone)
    var domain by mutableStateOf(source.customDomain.orEmpty())
    var hexText by mutableStateOf(source.theme.primary.orEmpty())
    var primary by mutableStateOf<String?>(source.theme.primary?.let { normalizeHex(it) })
    var fontDisplay by mutableStateOf(source.theme.fontDisplay)
    var fontBody by mutableStateOf(source.theme.fontBody)
    var radius by mutableStateOf(source.theme.radius)
    var mode by mutableStateOf(source.theme.mode)

    private val initial: UpdateBrandRequest = toRequest()

    val nameError: String? get() = if (name.isBlank()) "Escribe el nombre de tu restaurante" else null
    val hexError: String? get() = if (hexText.isBlank() || normalizeHex(hexText) != null) null else "Usa el formato #RRGGBB, por ejemplo #B4411F"
    val domainError: String?
        get() {
            val d = domain.trim()
            return when {
                d.isEmpty() -> null
                d.contains(" ") || d.contains("/") || !d.contains(".") || d.startsWith(".") || d.endsWith(".") -> "Escribe solo el dominio, por ejemplo menu.mirestaurante.mx"
                else -> null
            }
        }
    val isValid: Boolean get() = nameError == null && hexError == null && domainError == null
    val isDirty: Boolean get() = toRequest() != initial

    val theme: BrandTheme
        get() = source.theme.copy(primary = primary, fontDisplay = fontDisplay, fontBody = fontBody, radius = radius, mode = mode)

    fun onHexChange(text: String) {
        hexText = text.take(7)
        val normalized = normalizeHex(hexText)
        if (normalized != null) primary = normalized else if (hexText.isBlank()) primary = null
    }

    fun pick(hex: String?) {
        hexText = hex.orEmpty()
        primary = hex
    }

    fun toRequest(): UpdateBrandRequest = UpdateBrandRequest(
        name = name.trim(),
        tagline = tagline.trim(),
        description = description.trim(),
        address = address.trim(),
        phone = phone.trim(),
        theme = theme,
        customDomain = domain.trim().lowercase().ifBlank { null },
    )
}

@Composable
internal fun BrandSection(onSaved: (TenantPublic) -> Unit, onOpenDiner: (String) -> Unit) {
    val repo = LocalRepo.current
    val brandLoader = rememberLoader { repo.getBrand() }
    val menuLoader = rememberLoader { repo.getAdminMenu() }
    when (val state = brandLoader.state) {
        is Load.Ready -> BrandEditor(
            source = state.value,
            menu = menuLoader.value,
            onSaved = { brandLoader.set(it); onSaved(it) },
            onOpenDiner = onOpenDiner,
        )
        is Load.Failed -> PanelPage {
            PageHeader("Marca", subtitle = "Tu menú con tu identidad, sin marcas ajenas")
            ErrorState(state.message, onRetry = { brandLoader.reload() })
        }
        Load.Loading -> PanelPage {
            PageHeader("Marca", subtitle = "Tu menú con tu identidad, sin marcas ajenas")
            SkeletonRows(rows = 4)
            SkeletonRows(rows = 3)
        }
    }
}

@Composable
private fun BrandEditor(source: TenantPublic, menu: Menu?, onSaved: (TenantPublic) -> Unit, onOpenDiner: (String) -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val draft = remember(source) { BrandDraft(source) }
    var saving by remember { mutableStateOf(false) }
    val size = LocalWindowSize.current

    val save: () -> Unit = {
        if (!draft.isValid) {
            Toasts.show("Revisa los campos marcados antes de guardar", Tone.WARNING)
        } else if (!saving) {
            scope.launch {
                saving = true
                try {
                    val saved = repo.updateBrand(draft.toRequest())
                    onSaved(saved)
                    Toasts.show("Tu marca se actualizó. Tus clientes ya la ven así.", Tone.SUCCESS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Toasts.show(errorText(e, "No pudimos guardar los cambios"), Tone.DANGER)
                } finally {
                    saving = false
                }
            }
        }
    }

    val header: @Composable () -> Unit = {
        if (size == WindowSize.COMPACT) {
            PageHeader("Marca", subtitle = "Tu menú con tu identidad, sin marcas ajenas")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppButton("Ver mi menú", onClick = { onOpenDiner(source.slug) }, modifier = Modifier.weight(1f), variant = ButtonVariant.SECONDARY, icon = AppIcons.Eye, fullWidth = true)
                AppButton("Guardar cambios", onClick = save, modifier = Modifier.weight(1f), loading = saving, enabled = draft.isDirty || saving, fullWidth = true)
            }
        } else {
            PageHeader("Marca", subtitle = "Tu menú con tu identidad, sin marcas ajenas") {
                if (draft.isDirty) StatusPill("Cambios sin guardar", tone = Tone.WARNING)
                AppButton("Ver mi menú", onClick = { onOpenDiner(source.slug) }, variant = ButtonVariant.SECONDARY, icon = AppIcons.Eye)
                AppButton("Guardar cambios", onClick = save, icon = AppIcons.Check, loading = saving, enabled = draft.isDirty || saving)
            }
        }
    }

    val preview: @Composable () -> Unit = {
        PhonePreview(
            name = draft.name.ifBlank { "Tu restaurante" },
            tagline = draft.tagline,
            theme = draft.theme,
            etaMinutes = source.etaMinutes,
            menu = menu,
        )
    }

    if (size == WindowSize.EXPANDED) {
        val pad = pagePadding()
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(pad),
                verticalArrangement = Arrangement.spacedBy(Space.x5),
            ) {
                header()
                BrandForm(draft)
                Spacer(Modifier.height(Space.x6))
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(AppTheme.colors.line))
            Box(
                Modifier.width(388.dp).fillMaxHeight().background(AppTheme.colors.surfaceSunken)
                    .verticalScroll(rememberScrollState()).padding(vertical = Space.x6),
                contentAlignment = Alignment.TopCenter,
            ) {
                preview()
            }
        }
    } else {
        PanelPage(maxWidth = 760.dp) {
            header()
            BrandForm(draft)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { preview() }
        }
    }
}

// ───────────────────────── Formulario ─────────────────────────

@Composable
private fun BrandForm(draft: BrandDraft) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Datos del restaurante", subtitle = "Lo que ven tus clientes al abrir tu menú")
            if (compact) {
                AppTextField(draft.name, { draft.name = it }, Modifier.fillMaxWidth(), label = "Nombre", placeholder = "Nombre de tu restaurante", leadingIcon = AppIcons.Store, error = draft.nameError)
                AppTextField(draft.tagline, { draft.tagline = it }, Modifier.fillMaxWidth(), label = "Frase corta", placeholder = "Parrilla mexicana contemporánea")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    AppTextField(draft.name, { draft.name = it }, Modifier.weight(1f), label = "Nombre", placeholder = "Nombre de tu restaurante", leadingIcon = AppIcons.Store, error = draft.nameError)
                    AppTextField(draft.tagline, { draft.tagline = it }, Modifier.weight(1f), label = "Frase corta", placeholder = "Parrilla mexicana contemporánea")
                }
            }
            AppTextField(draft.description, { draft.description = it }, Modifier.fillMaxWidth(), label = "Descripción", placeholder = "Cuenta en dos líneas qué hace especial a tu cocina", singleLine = false, minLines = 3)
            if (compact) {
                AppTextField(draft.address, { draft.address = it }, Modifier.fillMaxWidth(), label = "Dirección", placeholder = "Calle, número, colonia y ciudad", leadingIcon = AppIcons.Location)
                AppTextField(draft.phone, { draft.phone = it }, Modifier.fillMaxWidth(), label = "Teléfono", placeholder = "55 0000 0000", leadingIcon = AppIcons.Phone, keyboardType = KeyboardType.Phone)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    AppTextField(draft.address, { draft.address = it }, Modifier.weight(1.6f), label = "Dirección", placeholder = "Calle, número, colonia y ciudad", leadingIcon = AppIcons.Location)
                    AppTextField(draft.phone, { draft.phone = it }, Modifier.weight(1f), label = "Teléfono", placeholder = "55 0000 0000", leadingIcon = AppIcons.Phone, keyboardType = KeyboardType.Phone)
                }
            }
            AppTextField(draft.domain, { draft.domain = it }, Modifier.fillMaxWidth(), label = "Dominio propio (opcional)", placeholder = "menu.mirestaurante.mx", leadingIcon = AppIcons.Globe, error = draft.domainError, keyboardType = KeyboardType.Uri)
            AppText("Apunta tu dominio a tu menú y tus clientes nunca verán otra marca que no sea la tuya.", style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle)
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Color de marca", subtitle = "Un solo color que da vida a botones, precios y detalles")
            SimpleGrid(items = brandSwatches, columns = if (compact) 5 else 10, spacing = Space.x2) { hex ->
                ColorSwatch(hex = hex, selected = draft.primary == hex, onClick = { draft.pick(hex) })
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                val current = parseHexColor(draft.primary) ?: AppTheme.colors.ink
                val animated by animateColorAsState(current, tween(Motion.SLOW), label = "brand-dot")
                Box(Modifier.padding(top = Space.x5).size(44.dp).clip(AppTheme.shapes.sm).background(animated).border(1.dp, AppTheme.colors.line, AppTheme.shapes.sm))
                AppTextField(
                    value = draft.hexText,
                    onValueChange = { draft.onHexChange(it) },
                    modifier = Modifier.weight(1f),
                    label = "Código hexadecimal",
                    placeholder = "#B4411F",
                    leadingIcon = AppIcons.Palette,
                    error = draft.hexError,
                )
            }
            if (draft.primary != null) {
                AppButton("Usar tinta neutra (sin color)", onClick = { draft.pick(null) }, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
            }
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Tipografía", subtitle = "Una fuente para títulos y otra para el texto")
            FieldLabel("Títulos")
            SimpleGrid(items = FontChoice.entries.toList(), columns = if (compact) 2 else 4, spacing = Space.x3) { font ->
                FontOption(font = font, selected = draft.fontDisplay == font, onClick = { draft.fontDisplay = font })
            }
            FieldLabel("Texto")
            SimpleGrid(items = FontChoice.entries.toList(), columns = if (compact) 2 else 4, spacing = Space.x3) { font ->
                FontOption(font = font, selected = draft.fontBody == font, onClick = { draft.fontBody = font })
            }
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Forma", subtitle = "Qué tan redondeados se ven botones, tarjetas y fotos")
            SimpleGrid(items = RadiusStyle.entries.toList(), columns = if (compact) 2 else 4, spacing = Space.x3) { radius ->
                RadiusOption(radius = radius, selected = draft.radius == radius, onClick = { draft.radius = radius })
            }
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Apariencia", subtitle = "Automático sigue la preferencia del teléfono de cada cliente")
            val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
            SegmentedControl(
                options = listOf("Claro", "Oscuro", "Automático"),
                selectedIndex = modes.indexOf(draft.mode).coerceAtLeast(0),
                onSelect = { draft.mode = modes[it] },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val color = parseHexColor(hex) ?: c.ink
    val ring by animateColorAsState(if (selected) c.ink else Color.Transparent, tween(Motion.BASE), label = "swatch-ring")
    Box(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).border(2.dp, ring, CircleShape).padding(5.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (selected) PanelIcon(AppIcons.Check, tint = if (color.luminance() > 0.55f) Color.Black else Color.White, size = 16.dp)
        }
    }
}

@Composable
private fun FontOption(font: FontChoice, selected: Boolean, onClick: () -> Unit) {
    val outer = AppTheme.colors
    ChoiceCard(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        // Tema anidado: solo se usa su tipografía para dibujar cada opción con su propia fuente.
        AppTheme(brand = BrandTheme(fontDisplay = font, fontBody = font)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText("Aa", style = AppTheme.type.h2, color = outer.ink)
                AppText(font.displayName(), style = AppTheme.type.bodySm, color = outer.inkMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RadiusOption(radius: RadiusStyle, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val corner = when (radius) {
        RadiusStyle.SHARP -> 2.dp
        RadiusStyle.SOFT -> 7.dp
        RadiusStyle.ROUND -> 12.dp
        RadiusStyle.PILL -> 18.dp
    }
    ChoiceCard(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.padding(vertical = Space.x1).size(width = 60.dp, height = 36.dp)
                .clip(RoundedCornerShape(corner))
                .background(if (selected) c.primary else c.surfaceSunken)
                .border(1.5.dp, if (selected) c.primary else c.lineStrong, RoundedCornerShape(corner)),
        )
        AppText(radius.displayName(), style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
    }
}

// ───────────────────────── Vista previa en teléfono ─────────────────────────

@Composable
private fun animated(color: Color): Color =
    animateColorAsState(color, tween(Motion.SLOW, easing = Motion.standard), label = "preview-color").value

@Composable
private fun PhonePreview(name: String, tagline: String, theme: BrandTheme, etaMinutes: Int, menu: Menu?) {
    val outer = AppTheme.colors
    val bezel = if (outer.isDark) outer.lineStrong else outer.ink
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            PanelIcon(AppIcons.Eye, size = 16.dp)
            AppText("Vista previa en vivo", style = AppTheme.type.label, color = outer.inkMuted)
        }
        Box(Modifier.size(width = 300.dp, height = 610.dp).clip(RoundedCornerShape(46.dp)).background(bezel).padding(9.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(38.dp))) {
                CompositionLocalProvider(LocalWindowSize provides WindowSize.COMPACT) {
                    AppTheme(brand = theme) {
                        PhoneScreen(name = name, tagline = tagline, theme = theme, etaMinutes = etaMinutes, menu = menu)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = Space.x2).size(width = 84.dp, height = 22.dp).clip(CircleShape).background(bezel))
        }
        AppText("Así verán tu menú tus clientes", style = AppTheme.type.bodySm, color = outer.inkSubtle)
    }
}

@Composable
private fun PhoneScreen(name: String, tagline: String, theme: BrandTheme, etaMinutes: Int, menu: Menu?) {
    val c = AppTheme.colors
    val canvas = animated(c.canvas)
    val ink = animated(c.ink)
    val inkMuted = animated(c.inkMuted)
    val primary = animated(c.primary)
    val onPrimary = animated(c.onPrimary)

    val categories = remember(menu) { menu?.categories?.sortedBy { it.sortOrder }.orEmpty() }
    var tab by remember(menu) { mutableStateOf(0) }
    val safeTab = if (categories.isEmpty()) 0 else tab.coerceIn(0, categories.size - 1)
    val items: List<MenuItem> = remember(menu, safeTab) {
        if (menu == null) emptyList()
        else {
            val inCategory = if (categories.isEmpty()) menu.items else menu.items.filter { it.categoryId == categories[safeTab].id }
            inCategory.sortedBy { it.sortOrder }.take(2)
        }
    }
    val cover = theme.coverUrl ?: menu?.items?.firstOrNull { it.imageUrl != null }?.imageUrl

    Box(Modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(148.dp)) {
                DishImage(url = cover, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.scrim))))
                BrandMark(name = name, logoUrl = theme.logoUrl, modifier = Modifier.align(Alignment.BottomStart).padding(start = Space.x4, bottom = Space.x3), size = 46.dp)
            }
            Column(Modifier.padding(start = Space.x4, end = Space.x4, top = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                AppText(name, style = AppTheme.type.h3, color = ink, maxLines = 1)
                if (tagline.isNotBlank()) AppText(tagline, style = AppTheme.type.bodySm, color = inkMuted, maxLines = 1)
                Row(Modifier.padding(top = Space.x1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    StatusPill("Abierto", tone = Tone.SUCCESS)
                    PanelIcon(AppIcons.Clock, tint = inkMuted, size = 14.dp)
                    AppText("$etaMinutes min", style = AppTheme.type.label, color = inkMuted)
                }
            }
            if (categories.isNotEmpty()) {
                CategoryTabs(tabs = categories.map { it.name }, selectedIndex = safeTab, onSelect = { tab = it }, modifier = Modifier.padding(top = Space.x2))
            }
            Column(Modifier.padding(horizontal = Space.x3, vertical = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                if (menu == null) {
                    repeat(2) { Skeleton(Modifier.fillMaxWidth().height(96.dp), shape = AppTheme.shapes.md) }
                } else if (items.isEmpty()) {
                    AppText("Aún no hay platillos en esta categoría", style = AppTheme.type.bodySm, color = inkMuted)
                } else {
                    items.forEach { item -> PreviewDishCard(item) }
                }
            }
        }
        val total = items.sumOf { it.priceCents }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(Space.x3).fillMaxWidth().height(48.dp)
                .clip(AppTheme.shapes.control).background(primary).padding(horizontal = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            PanelIcon(AppIcons.Bag, tint = onPrimary, size = 18.dp)
            AppText("Ver pedido · ${items.size.coerceAtLeast(1)}", Modifier.weight(1f), style = AppTheme.type.title, color = onPrimary, maxLines = 1)
            AppText(formatMoney(if (total > 0L) total else 18500L), style = AppTheme.type.title, color = onPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun PreviewDishCard(item: MenuItem) {
    val c = AppTheme.colors
    val surface = animated(c.surface)
    val line = animated(c.line)
    val ink = animated(c.ink)
    val inkMuted = animated(c.inkMuted)
    val primary = animated(c.primary)
    val onPrimary = animated(c.onPrimary)
    Row(
        Modifier.fillMaxWidth().clip(AppTheme.shapes.md).background(surface).border(1.dp, line, AppTheme.shapes.md).padding(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(item.name, style = AppTheme.type.title, color = ink, maxLines = 1)
            if (item.description.isNotBlank()) AppText(item.description, style = AppTheme.type.bodySm, color = inkMuted, maxLines = 2)
            AppText(formatMoney(item.priceCents), style = AppTheme.type.title, color = primary, maxLines = 1)
        }
        Box {
            DishImage(url = item.imageUrl, modifier = Modifier.size(72.dp), shape = AppTheme.shapes.sm)
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(28.dp).clip(AppTheme.shapes.control).background(primary),
                contentAlignment = Alignment.Center,
            ) {
                PanelIcon(AppIcons.Add, tint = onPrimary, size = 16.dp)
            }
        }
    }
}
