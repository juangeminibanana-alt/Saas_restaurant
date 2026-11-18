package app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme

/** Dibuja un ícono de [AppIcons] teñido con un color del tema. */
@Composable
internal fun IconGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.ink,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

// Trazo único de NOCHE: 1.5 px al tamaño estándar de 20 dp (1.8 en el viewport de 24).
private const val STROKE = 1.8f

/** Ícono de contorno: viewport 24, trazo único con remates y uniones redondeadas, sin relleno. */
private fun icon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    )
    for (d in paths) {
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

/** Círculo como datos de trazo SVG. */
private fun circle(cx: Float, cy: Float, r: Float): String {
    val d = r * 2
    return "M${cx - r} $cy a$r $r 0 1 0 $d 0 a$r $r 0 1 0 ${-d} 0"
}

/** Rectángulo redondeado como datos de trazo SVG. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f): String =
    "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r h${-(w - 2 * r)} " +
        "a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r} z"

object AppIcons {
    val Add: ImageVector by lazy { icon("Add", "M12 5v14", "M5 12h14") }
    val Remove: ImageVector by lazy { icon("Remove", "M5 12h14") }
    val Close: ImageVector by lazy { icon("Close", "M18 6L6 18", "M6 6l12 12") }
    val Back: ImageVector by lazy { icon("Back", "M19 12H5", "M12 19l-7-7 7-7") }
    val Forward: ImageVector by lazy { icon("Forward", "M5 12h14", "M12 5l7 7-7 7") }
    val Check: ImageVector by lazy { icon("Check", "M20 6L9 17l-5-5") }
    val Search: ImageVector by lazy { icon("Search", circle(11f, 11f, 7f), "M20.5 20.5l-4.5-4.5") }
    val Cart: ImageVector by lazy {
        icon(
            "Cart", circle(9f, 20f, 1.2f), circle(18f, 20f, 1.2f),
            "M2 3h2.6l2.5 11.6a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 1.95 -1.55L21.2 7.5H5.6",
        )
    }
    val Bag: ImageVector by lazy {
        icon("Bag", "M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2 -2V6l-3 -4z", "M3 6h18", "M16 10a4 4 0 0 1 -8 0")
    }
    val Clock: ImageVector by lazy { icon("Clock", circle(12f, 12f, 9.5f), "M12 6.5V12l3.5 2") }
    val Flame: ImageVector by lazy {
        icon(
            "Flame",
            "M8.5 14.5A2.5 2.5 0 0 0 11 12c0 -1.38 -.5 -2 -1 -3 -1.07 -2.14 -.22 -4.05 2 -6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1 -14 0c0 -1.15 .43 -2.29 1 -3a2.5 2.5 0 0 0 2.5 2.5z",
        )
    }
    val Star: ImageVector by lazy {
        icon("Star", "M12 2.5l2.94 5.96 6.56 .96 -4.75 4.63 1.12 6.54L12 17.5l-5.87 3.09 1.12 -6.54L2.5 9.42l6.56 -.96L12 2.5z")
    }
    val Heart: ImageVector by lazy {
        icon(
            "Heart",
            "M20.84 4.61a5.5 5.5 0 0 0 -7.78 0L12 5.67l-1.06 -1.06a5.5 5.5 0 0 0 -7.78 7.78l1.06 1.06L12 21.23l7.78 -7.78 1.06 -1.06a5.5 5.5 0 0 0 0 -7.78z",
        )
    }
    val Menu: ImageVector by lazy { icon("Menu", "M3.5 6h17", "M3.5 12h17", "M3.5 18h17") }
    val Settings: ImageVector by lazy {
        icon(
            "Settings", circle(12f, 12f, 3f),
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06 .06a2 2 0 0 1 0 2.83 2 2 0 0 1 -2.83 0l-.06 -.06a1.65 1.65 0 0 0 -1.82 -.33 1.65 1.65 0 0 0 -1 1.51V21a2 2 0 0 1 -2 2 2 2 0 0 1 -2 -2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0 -1.82 .33l-.06 .06a2 2 0 0 1 -2.83 0 2 2 0 0 1 0 -2.83l.06 -.06a1.65 1.65 0 0 0 .33 -1.82 1.65 1.65 0 0 0 -1.51 -1H3a2 2 0 0 1 -2 -2 2 2 0 0 1 2 -2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0 -.33 -1.82l-.06 -.06a2 2 0 0 1 0 -2.83 2 2 0 0 1 2.83 0l.06 .06a1.65 1.65 0 0 0 1.82 .33H9a1.65 1.65 0 0 0 1 -1.51V3a2 2 0 0 1 2 -2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82 -.33l.06 -.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06 .06a1.65 1.65 0 0 0 -.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1 -2 2h-.09a1.65 1.65 0 0 0 -1.51 1z",
        )
    }
    val Edit: ImageVector by lazy { icon("Edit", "M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5 -5.5L17 3z", "M15 5l4 4") }
    val Delete: ImageVector by lazy {
        icon(
            "Delete", "M3 6h18", "M19 6v14a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V6",
            "M8 6V4a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2", "M10 11v6", "M14 11v6",
        )
    }
    val Home: ImageVector by lazy {
        icon("Home", "M3 9.5l9 -7 9 7V20a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z", "M9 22V12h6v10")
    }
    val Store: ImageVector by lazy {
        icon(
            "Store", "M4 10.5V20a1 1 0 0 0 1 1h14a1 1 0 0 0 1 -1v-9.5", "M3 8l1.6 -5h14.8L21 8",
            "M3 8a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0", "M9.5 21v-6h5v6",
        )
    }
    val Receipt: ImageVector by lazy {
        icon(
            "Receipt",
            "M5 3v18l2.5 -1.5L10 21l2 -1.5 2 1.5 2.5 -1.5L19 21V3l-2.5 1.5L14 3l-2 1.5L10 3 7.5 4.5 5 3z",
            "M9 9h6", "M9 13h6",
        )
    }
    val Chart: ImageVector by lazy { icon("Chart", "M3 3v18h18", "M8 17v-5", "M13 17V7", "M18 17v-8") }
    val Users: ImageVector by lazy {
        icon(
            "Users", "M17 21v-2a4 4 0 0 0 -4 -4H5a4 4 0 0 0 -4 4v2", circle(9f, 7f, 4f),
            "M23 21v-2a4 4 0 0 0 -3 -3.87", "M16 3.13a4 4 0 0 1 0 7.75",
        )
    }
    val CreditCard: ImageVector by lazy { icon("CreditCard", rect(1.5f, 4.5f, 21f, 15f, 2.5f), "M1.5 10h21", "M5.5 15h3") }
    val Palette: ImageVector by lazy {
        icon(
            "Palette",
            "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.93 0 1.65 -.75 1.65 -1.69 0 -.44 -.18 -.84 -.44 -1.13 -.29 -.29 -.44 -.65 -.44 -1.13a1.64 1.64 0 0 1 1.67 -1.67h2c3.05 0 5.56 -2.5 5.56 -5.55C21.97 6.01 17.46 2 12 2z",
            circle(13.5f, 6.5f, 0.6f), circle(17.5f, 10.5f, 0.6f), circle(8.5f, 7.5f, 0.6f), circle(6.5f, 12.5f, 0.6f),
        )
    }
    val Link: ImageVector by lazy {
        icon(
            "Link", "M10 13a5 5 0 0 0 7.54 .54l3 -3a5 5 0 0 0 -7.07 -7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0 -7.54 -.54l-3 3a5 5 0 0 0 7.07 7.07l1.71 -1.71",
        )
    }
    val Qr: ImageVector by lazy {
        icon(
            "Qr", rect(3f, 3f, 7f, 7f, 1f), rect(14f, 3f, 7f, 7f, 1f), rect(3f, 14f, 7f, 7f, 1f),
            "M14 14h3v3", "M21 14v3h-1", "M14 20.5V21h3", "M21 20v1",
        )
    }
    val Bell: ImageVector by lazy {
        icon("Bell", "M18 8A6 6 0 0 0 6 8c0 7 -3 9 -3 9h18s-3 -2 -3 -9", "M13.73 21a2 2 0 0 1 -3.46 0")
    }
    val Moon: ImageVector by lazy { icon("Moon", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z") }
    val Sun: ImageVector by lazy {
        icon(
            "Sun", circle(12f, 12f, 4.5f), "M12 1.5v2", "M12 20.5v2", "M4.6 4.6l1.4 1.4", "M18 18l1.4 1.4",
            "M1.5 12h2", "M20.5 12h2", "M4.6 19.4l1.4 -1.4", "M18 6l1.4 -1.4",
        )
    }
    val Logout: ImageVector by lazy {
        icon("Logout", "M9 21H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2h4", "M16 17l5 -5 -5 -5", "M21 12H9")
    }
    val Phone: ImageVector by lazy {
        icon(
            "Phone",
            "M22 16.92v3a2 2 0 0 1 -2.18 2 19.79 19.79 0 0 1 -8.63 -3.07 19.5 19.5 0 0 1 -6 -6 19.79 19.79 0 0 1 -3.07 -8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1 -.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27 -1.27a2 2 0 0 1 2.11 -.45 12.84 12.84 0 0 0 2.81 .7A2 2 0 0 1 22 16.92z",
        )
    }
    val Location: ImageVector by lazy {
        icon("Location", "M20.5 10c0 6.5 -8.5 12.5 -8.5 12.5S3.5 16.5 3.5 10a8.5 8.5 0 0 1 17 0z", circle(12f, 10f, 3f))
    }
    val Info: ImageVector by lazy { icon("Info", circle(12f, 12f, 9.5f), "M12 16.5V11.5", "M12 7.75v.01") }
    val Warning: ImageVector by lazy {
        icon(
            "Warning",
            "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71 -3L13.71 3.86a2 2 0 0 0 -3.42 0z",
            "M12 9v4", "M12 17v.01",
        )
    }
    val ChevronRight: ImageVector by lazy { icon("ChevronRight", "M9 18l6 -6 -6 -6") }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M6 9l6 6 6 -6") }
    val Bike: ImageVector by lazy {
        icon(
            "Bike", circle(5.5f, 17.5f, 3.5f), circle(18.5f, 17.5f, 3.5f), circle(15f, 5f, 1f),
            "M12 17.5V14l-3 -3 4 -3 2 3h3",
        )
    }
    val Chef: ImageVector by lazy {
        icon(
            "Chef",
            "M6 13.87A4 4 0 0 1 7.41 6a5.11 5.11 0 0 1 1.05 -1.54 5 5 0 0 1 7.08 0A5.11 5.11 0 0 1 16.59 6 4 4 0 0 1 18 13.87V21H6z",
            "M6 17h12",
        )
    }
    val Plug: ImageVector by lazy {
        icon("Plug", "M12 22v-5", "M9 8V2", "M15 8V2", "M18 8v5a4 4 0 0 1 -4 4h-4a4 4 0 0 1 -4 -4V8z")
    }
    val Dashboard: ImageVector by lazy {
        icon(
            "Dashboard", rect(3f, 3f, 7.5f, 9.5f, 1.5f), rect(13.5f, 3f, 7.5f, 5.5f, 1.5f),
            rect(13.5f, 11.5f, 7.5f, 9.5f, 1.5f), rect(3f, 15.5f, 7.5f, 5.5f, 1.5f),
        )
    }
    val Tag: ImageVector by lazy {
        icon(
            "Tag", "M20.59 13.41l-7.17 7.17a2 2 0 0 1 -2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z",
            "M7 7v.01",
        )
    }
    val Eye: ImageVector by lazy {
        icon("Eye", "M1.5 12s4 -7.5 10.5 -7.5S22.5 12 22.5 12s-4 7.5 -10.5 7.5S1.5 12 1.5 12z", circle(12f, 12f, 3f))
    }
    val Refresh: ImageVector by lazy {
        icon(
            "Refresh", "M23 4v6h-6", "M1 20v-6h6",
            "M3.51 9a9 9 0 0 1 14.85 -3.36L23 10", "M1 14l4.64 4.36A9 9 0 0 0 20.49 15",
        )
    }
    val Image: ImageVector by lazy {
        icon("Image", rect(3f, 3f, 18f, 18f, 2.5f), circle(8.5f, 8.5f, 1.5f), "M21 15l-5 -5L5 21")
    }
    val Cash: ImageVector by lazy {
        icon("Cash", rect(2f, 6f, 20f, 12f, 2f), circle(12f, 12f, 2.5f), "M6 12v.01", "M18 12v.01")
    }
    val Bank: ImageVector by lazy {
        icon(
            "Bank", "M3 21h18", "M12 3l8.5 6h-17z", "M4 18h16", "M6 9v9", "M10 9v9", "M14 9v9", "M18 9v9",
        )
    }
    val Wallet: ImageVector by lazy {
        icon(
            "Wallet", "M21 12V7H5a2 2 0 0 1 0 -4h14v4", "M3 5v14a2 2 0 0 0 2 2h16v-5",
            "M18 12a2 2 0 0 0 0 4h4v-4z",
        )
    }
    val Sparkle: ImageVector by lazy {
        icon(
            "Sparkle",
            "M11 4l1.7 5.1a2 2 0 0 0 1.2 1.2L19 12l-5.1 1.7a2 2 0 0 0 -1.2 1.2L11 20l-1.7 -5.1a2 2 0 0 0 -1.2 -1.2L3 12l5.1 -1.7a2 2 0 0 0 1.2 -1.2z",
            "M19 2.5v4", "M17 4.5h4",
        )
    }
    val Filter: ImageVector by lazy { icon("Filter", "M22 3H2l8 9.46V19l4 2v-8.54L22 3z") }
    val MoreVert: ImageVector by lazy {
        icon("MoreVert", circle(12f, 5f, 0.9f), circle(12f, 12f, 0.9f), circle(12f, 19f, 0.9f))
    }
    val ExternalLink: ImageVector by lazy {
        icon("ExternalLink", "M18 13v6a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2h6", "M15 3h6v6", "M10 14L21 3")
    }
    val Copy: ImageVector by lazy {
        icon("Copy", rect(9f, 9f, 13f, 13f, 2f), "M5 15H4a2 2 0 0 1 -2 -2V4a2 2 0 0 1 2 -2h9a2 2 0 0 1 2 2v1")
    }
    val Lock: ImageVector by lazy { icon("Lock", rect(3.5f, 11f, 17f, 11f, 2f), "M7 11V7a5 5 0 0 1 10 0v4") }
    val Mail: ImageVector by lazy { icon("Mail", rect(2f, 4f, 20f, 16f, 2f), "M22 6.5l-10 7 -10 -7") }
    val Person: ImageVector by lazy {
        icon("Person", "M20 21v-2a4 4 0 0 0 -4 -4H8a4 4 0 0 0 -4 4v2", circle(12f, 7f, 4f))
    }
    val Table: ImageVector by lazy {
        icon("Table", rect(2.5f, 6f, 19f, 4f, 1.2f), "M6 10v9", "M18 10v9", "M6 14.5h12")
    }
    val Globe: ImageVector by lazy {
        icon(
            "Globe", circle(12f, 12f, 9.5f), "M2.5 12h19",
            "M12 2.5a14.5 14.5 0 0 1 3.8 9.5 14.5 14.5 0 0 1 -3.8 9.5 14.5 14.5 0 0 1 -3.8 -9.5A14.5 14.5 0 0 1 12 2.5z",
        )
    }

    /** Uso interno: marcador de platillo sin foto. */
    internal val Utensils: ImageVector by lazy {
        icon(
            "Utensils", "M3 2v7c0 1.1 .9 2 2 2h4a2 2 0 0 0 2 -2V2", "M7 2v20",
            "M21 15V2a5 5 0 0 0 -5 5v6c0 1.1 .9 2 2 2h3z", "M21 15v7",
        )
    }
}
