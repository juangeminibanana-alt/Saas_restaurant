package app.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shared.api.UpdateBrandRequest
import app.shared.model.BrandTheme
import app.shared.model.Menu
import app.shared.model.TenantPublic
import app.shell.LocalRepo
import app.shell.MenuPreviewPhone
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import app.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Acentos sugeridos: colores luminosos que se leen sobre el negro de NOCHE. El restaurante puede escribir
 * cualquier otro; si es muy oscuro, el sistema lo aclara lo justo para que se vea.
 */
private val brandSwatches = listOf(
    "#FF6A3D", "#FF4D5E", "#FF8A1F", "#E8B86B", "#F5D547",
    "#7CF5C4", "#3ECF8E", "#6CB4FF", "#9B8CFF", "#FF7AB6",
)

/** Ancho de la columna de vista previa en pantallas anchas. */
private val PreviewColumn = 360.dp

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

/**
 * Borrador de la marca. En NOCHE la marca de un restaurante es su nombre, sus datos, su fotografía (logotipo y
 * portada) y UN color de acento: la tipografía, los radios y el modo oscuro los fija el sistema, así que del
 * tema guardado sólo se editan `primary`, `logoUrl` y `coverUrl` (lo demás se conserva tal cual).
 */
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
    var logoUrl by mutableStateOf(source.theme.logoUrl.orEmpty())
    var coverUrl by mutableStateOf(source.theme.coverUrl.orEmpty())

    private val initial: UpdateBrandRequest = toRequest()

    val nameError: String? get() = if (name.isBlank()) "Escribe el nombre de tu restaurante" else null
    val hexError: String? get() = if (hexText.isBlank() || normalizeHex(hexText) != null) null else "Usa el formato #RRGGBB, por ejemplo #FF6A3D"
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
        get() = source.theme.copy(
            primary = primary,
            logoUrl = logoUrl.trim().ifBlank { null },
            coverUrl = coverUrl.trim().ifBlank { null },
        )

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

private const val BrandSubtitle = "Tu nombre, tu fotografía y tu color. El resto ya está diseñado."

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
            PageHeader("Marca", subtitle = BrandSubtitle)
            ErrorState(state.message, onRetry = { brandLoader.reload() })
        }
        Load.Loading -> PanelPage {
            PageHeader("Marca", subtitle = BrandSubtitle)
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

    val preview: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            LabelText("Vista previa en vivo", dot = AppTheme.colors.positive)
            // Colores y foto del borrador: lo que se escribe a la izquierda se ve aquí al instante.
            CompositionLocalProvider(LocalSurfaceLevel provides 0) {
                MenuPreviewPhone(
                    name = draft.name.ifBlank { "Tu restaurante" },
                    tagline = draft.tagline,
                    theme = draft.theme,
                    etaMinutes = source.etaMinutes,
                    menu = menu,
                    rating = source.rating,
                    isOpen = source.isOpen,
                    scale = 0.74f,
                )
            }
            AppText("Así verán tu menú tus clientes", style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle)
        }
    }

    when (size) {
        WindowSize.EXPANDED -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                PageHeader("Marca", subtitle = BrandSubtitle) {
                    // "Ver mi menú" ya está en la barra superior: aquí sólo queda la acción de esta página.
                    if (draft.isDirty) StatusPill("Sin guardar", tone = Tone.WARNING)
                    AppButton("Guardar cambios", onClick = save, loading = saving, enabled = draft.isDirty || saving)
                }
                BrandForm(draft)
                Spacer(Modifier.height(Space.x10))
            }
            Box(
                Modifier.width(PreviewColumn).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = Space.x8),
                contentAlignment = Alignment.TopCenter,
            ) { preview() }
        }

        else -> Column(Modifier.fillMaxSize()) {
            // El formulario se desplaza; "Guardar cambios" queda fijo abajo, a todo el ancho, siempre a la mano.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PanelPage(maxWidth = 760.dp) {
                    PageHeader("Marca", subtitle = BrandSubtitle)
                    BrandForm(draft)
                    Box(Modifier.fillMaxWidth().padding(top = Space.x4), contentAlignment = Alignment.Center) { preview() }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.canvas).edgeTop(AppTheme.colors.line).padding(vertical = Space.x3),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(AppIcons.Eye, onClick = { onOpenDiner(source.slug) }, contentDescription = "Ver mi menú")
                AppButton(
                    if (draft.isDirty || saving) "Guardar cambios" else "Sin cambios",
                    onClick = save, modifier = Modifier.weight(1f), loading = saving, enabled = draft.isDirty || saving,
                )
            }
        }
    }
}

// ───────────────────────── Formulario ─────────────────────────

@Composable
private fun BrandForm(draft: BrandDraft) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Datos del restaurante", label = if (compact) null else "Lo que ven tus clientes")
            if (compact) {
                AppTextField(draft.name, { draft.name = it }, Modifier.fillMaxWidth(), label = "Nombre", placeholder = "Nombre de tu restaurante", error = draft.nameError)
                AppTextField(draft.tagline, { draft.tagline = it }, Modifier.fillMaxWidth(), label = "Frase corta", placeholder = "Parrilla mexicana contemporánea")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    AppTextField(draft.name, { draft.name = it }, Modifier.weight(1f), label = "Nombre", placeholder = "Nombre de tu restaurante", error = draft.nameError)
                    AppTextField(draft.tagline, { draft.tagline = it }, Modifier.weight(1f), label = "Frase corta", placeholder = "Parrilla mexicana contemporánea")
                }
            }
            AppTextField(draft.description, { draft.description = it }, Modifier.fillMaxWidth(), label = "Descripción", placeholder = "Cuenta en dos líneas qué hace especial a tu cocina", singleLine = false, minLines = 3)
            if (compact) {
                AppTextField(draft.address, { draft.address = it }, Modifier.fillMaxWidth(), label = "Dirección", placeholder = "Calle, número, colonia y ciudad", leadingIcon = AppIcons.Location)
                AppTextField(draft.phone, { draft.phone = it }, Modifier.fillMaxWidth(), label = "Teléfono", placeholder = "55 0000 0000", leadingIcon = AppIcons.Phone, keyboardType = KeyboardType.Phone)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    AppTextField(draft.address, { draft.address = it }, Modifier.weight(1.6f), label = "Dirección", placeholder = "Calle, número, colonia y ciudad", leadingIcon = AppIcons.Location)
                    AppTextField(draft.phone, { draft.phone = it }, Modifier.weight(1f), label = "Teléfono", placeholder = "55 0000 0000", leadingIcon = AppIcons.Phone, keyboardType = KeyboardType.Phone)
                }
            }
            AppTextField(draft.domain, { draft.domain = it }, Modifier.fillMaxWidth(), label = "Dominio propio (opcional)", placeholder = "menu.mirestaurante.mx", leadingIcon = AppIcons.Globe, error = draft.domainError, keyboardType = KeyboardType.Uri)
            AppText("Apunta tu dominio a tu menú y tus clientes nunca verán otra marca que no sea la tuya.", style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle)
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Color de marca", label = if (compact) null else "Tu única decisión de diseño")
            AppText(
                "Es el color de \"tu pedido\": el botón principal y las cantidades agregadas. Tipografía, formas y fondo oscuro son del sistema y se ven igual de bien con cualquier acento.",
                style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted,
            )
            SimpleGrid(items = brandSwatches, columns = if (compact) 5 else 10) { hex ->
                ColorSwatch(hex = hex, selected = draft.primary == hex, onClick = { draft.pick(hex) })
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                // La muestra enseña el acento tal como quedará en el menú (aclarado si hace falta) con su texto encima.
                AppTheme(brand = draft.theme) {
                    val accent = AppTheme.colors
                    Box(
                        Modifier.padding(top = Space.x6).size(Sizes.controlLg).background(accent.primary, AppTheme.shapes.md),
                        contentAlignment = Alignment.Center,
                    ) { AppText("Aa", style = AppTheme.type.h3, color = accent.onPrimary) }
                }
                AppTextField(
                    value = draft.hexText,
                    onValueChange = { draft.onHexChange(it) },
                    modifier = Modifier.weight(1f),
                    label = "Código hexadecimal",
                    placeholder = "#FF6A3D",
                    error = draft.hexError,
                )
            }
            if (draft.primary != null) {
                AppButton("Usar el color del sistema", onClick = { draft.pick(null) }, variant = ButtonVariant.SECONDARY, size = ButtonSize.SM)
            }
            // Sin la vista previa al lado (teléfono y tableta), el acento se prueba aquí mismo, en la barra del menú.
            if (LocalWindowSize.current != WindowSize.EXPANDED) AccentSample(draft.theme)
        }
    }

    AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            SectionHeader("Logotipo y portada", label = if (compact) null else "Tu fotografía")
            AppText(
                "La portada abre tu menú y es lo único que brilla en pantalla. Pega el enlace de cada imagen.",
                style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.Top) {
                BrandMark(name = draft.name.ifBlank { "Tu restaurante" }, logoUrl = draft.logoUrl.trim().ifBlank { null }, modifier = Modifier.padding(top = Space.x6), size = Sizes.controlLg)
                AppTextField(draft.logoUrl, { draft.logoUrl = it }, Modifier.weight(1f), label = "Logotipo (opcional)", placeholder = "https://… imagen cuadrada", leadingIcon = AppIcons.Image, keyboardType = KeyboardType.Uri)
            }
            AppTextField(draft.coverUrl, { draft.coverUrl = it }, Modifier.fillMaxWidth(), label = "Portada", placeholder = "https://… foto horizontal de tu restaurante", leadingIcon = AppIcons.Image, keyboardType = KeyboardType.Uri)
            DishImage(
                url = draft.coverUrl.trim().ifBlank { null },
                modifier = Modifier.fillMaxWidth().aspectRatio(if (compact) 16f / 9f else 21f / 9f),
                contentDescription = "Portada",
                shape = AppTheme.shapes.md,
            )
        }
    }
}

/** Muestra de color: cuadro sólido de radio 8. Elegida, la rodea un filete en tinta y lleva la palomita. */
@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val color = parseHexColor(hex) ?: c.ink
    Box(
        Modifier
            .fillMaxWidth()
            .height(Sizes.control)
            .then(if (selected) Modifier.ring(c.select, AppTheme.shapes.md, 2.dp) else Modifier)
            .pressable(onClick = onClick)
            .padding(Space.x1),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(color, AppTheme.shapes.sm), contentAlignment = Alignment.Center) {
            if (selected) PanelIcon(AppIcons.Check, tint = onColor(color), size = 16.dp)
        }
    }
}

/** Tinta legible sobre un color sólido: oscura sobre un color luminoso, clara sobre uno profundo. */
@Composable
private fun onColor(color: Color): Color {
    val c = AppTheme.colors
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.5f) c.canvas else c.ink
}

/**
 * Muestra del acento en su único lugar: la barra inferior del menú (conteo, total y "Ver pedido"), pintada con el
 * acento del borrador tal como lo ajusta el sistema. No es interactiva.
 */
@Composable
private fun AccentSample(theme: BrandTheme) {
    AppTheme(brand = theme) {
        val c = AppTheme.colors
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            LabelText("Así se ve en tu menú")
            Row(
                Modifier.fillMaxWidth().flatSurface(c.canvas, c.line, AppTheme.shapes.md).padding(Space.x3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                Column(Modifier.weight(1f)) {
                    LabelText("2 platillos")
                    AppText("$274", style = AppTheme.type.monoLg, maxLines = 1)
                }
                Row(
                    Modifier.height(Sizes.controlLg).background(c.primary, AppTheme.shapes.md).padding(horizontal = Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    AppText("Ver pedido", style = AppTheme.type.h3, color = c.onPrimary, maxLines = 1)
                    PanelIcon(AppIcons.Forward, tint = c.onPrimary, size = Sizes.icon)
                }
            }
        }
    }
}
