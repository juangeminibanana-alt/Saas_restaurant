package app.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.platform.defaultApiBaseUrl
import app.shared.model.TenantPublic
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

@Suppress("DEPRECATION")
@Composable
internal fun LinksSection(brand: TenantPublic?, fallbackSlug: String, fallbackName: String, onOpenDiner: (String) -> Unit) {
    val slug = brand?.slug ?: fallbackSlug
    val name = brand?.name ?: fallbackName
    val size = LocalWindowSize.current
    val clipboard = LocalClipboardManager.current
    val link = remember(slug) { defaultApiBaseUrl().trimEnd('/') + "/#/r/" + slug }
    var tables by remember { mutableStateOf(8) }

    val copy: (String, String) -> Unit = { text, message ->
        clipboard.setText(AnnotatedString(text))
        Toasts.show(message, Tone.SUCCESS)
    }

    PanelPage {
        PageHeader("Enlaces y QR", subtitle = "Comparte tu menú con un enlace o con un código QR en cada mesa")


        if (slug.isBlank()) {
            ErrorState("No encontramos el enlace de tu restaurante. Vuelve a iniciar sesión e intenta de nuevo.")
            return@PanelPage
        }

        val linkCard: @Composable (Modifier) -> Unit = { modifier ->
            AppCard(modifier, padding = Space.x4) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                    SectionHeader("Enlace de tu menú", subtitle = "Funciona en cualquier teléfono, sin descargar nada")
                    CopyField(text = link, onCopy = { copy(link, "Enlace copiado") })
                    val domain = brand?.customDomain?.takeIf { it.isNotBlank() }
                    if (domain != null) {
                        FieldLabel("Tu dominio propio")
                        CopyField(text = "https://$domain", onCopy = { copy("https://$domain", "Dominio copiado") })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        val grow = if (size == WindowSize.COMPACT) Modifier.weight(1f) else Modifier
                        AppButton("Abrir menú", onClick = { onOpenDiner(slug) }, modifier = grow, trailingIcon = AppIcons.Forward)
                        AppButton("Copiar", onClick = { copy(link, "Enlace copiado") }, modifier = grow, variant = ButtonVariant.SECONDARY, icon = AppIcons.Copy)
                    }
                    AppDivider()
                    listOf(
                        "Ponlo en la biografía de tus redes sociales",
                        "Envíalo por mensaje a tus clientes frecuentes",
                        "Imprime el QR para tus mesas, tu mostrador y tus bolsas",
                    ).forEachIndexed { i, tip ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                            LabelText("0${i + 1}")
                            AppText(tip, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
                        }
                    }
                }
            }
        }

        val qrCard: @Composable (Modifier) -> Unit = { modifier ->
            AppCard(modifier, padding = Space.x4) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    LabelText("Escanea para ver el menú y pedir")
                    QrPlate(content = link, size = 220.dp)
                    AppText(name, style = AppTheme.type.h2, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }

        if (size == WindowSize.EXPANDED) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                linkCard(Modifier.weight(1.4f).fillMaxHeight())
                qrCard(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            linkCard(Modifier.fillMaxWidth())
            qrCard(Modifier.fillMaxWidth())
        }

        AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                SectionHeader(
                    "QR por mesa",
                    subtitle = "Cada código abre tu menú con la mesa ya elegida. Toca una tarjeta para copiar su enlace.",
                    // El número de mesas no es un pedido: la cifra va neutra, sin ficha de brasa.
                    action = { QuantityStepper(quantity = tables, onChange = { tables = it }, min = 1, max = 40, compact = true, inOrder = false) },
                )
                val columns = when (size) {
                    WindowSize.COMPACT -> 2
                    WindowSize.MEDIUM -> 4
                    WindowSize.EXPANDED -> 6
                }
                SimpleGrid(items = (1..tables).toList(), columns = columns) { n ->
                    val tableLink = "$link?mesa=$n"
                    TableQrCard(number = n, link = tableLink, onClick = { copy(tableLink, "Enlace de la Mesa $n copiado") })
                }
            }
        }
    }
}

@Composable
private fun CopyField(text: String, onCopy: () -> Unit) {
    // Un dato copiable: superficie + filete (radio 12, alto 48) con el enlace en mono.
    Row(
        Modifier.fillMaxWidth().height(Sizes.controlLg).flatSurface(AppTheme.controlFill, AppTheme.colors.line, AppTheme.shapes.md)
            .padding(start = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(text, Modifier.weight(1f), style = AppTheme.type.mono, maxLines = 1)
        AppIconButton(AppIcons.Copy, onClick = onCopy, contentDescription = "Copiar", tint = AppTheme.colors.inkMuted, plain = true)
    }
}

/** El QR siempre va sobre blanco para que cualquier cámara lo lea, también en modo oscuro. */
@Composable
private fun QrPlate(content: String, size: Dp) {
    Box(Modifier.clip(AppTheme.shapes.sm).background(Color.White).padding(Space.x2)) {
        QrCode(content = content, size = size)
    }
}

@Composable
private fun TableQrCard(number: Int, link: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().flatSurface(AppTheme.controlFill, AppTheme.colors.line, AppTheme.shapes.md)
            .pressable(onClick = onClick).padding(Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        QrPlate(content = link, size = 92.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            LabelText("Mesa")
            AppText(number.toString().padStart(2, '0'), style = AppTheme.type.monoMd, maxLines = 1)
        }
    }
}
