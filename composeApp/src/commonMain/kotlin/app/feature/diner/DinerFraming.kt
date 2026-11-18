package app.feature.diner

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment

/**
 * Encuadre de una fotografía (NOCHE: "la fotografía se cura, no se filtra").
 * [x], [y] es el punto focal (0..1): ese punto de la imagen queda en ese punto del marco;
 * [zoom] es el acercamiento alrededor de él (1 = la foto completa, recortada al marco).
 */
internal class Framing(val x: Float, val y: Float, val zoom: Float = 1f) {
    val alignment: Alignment get() = BiasAlignment(x * 2f - 1f, y * 2f - 1f)
}

/** Dónde se usa la foto: cada uso pide su propio encuadre. */
internal enum class PhotoUse {
    /** Portada del menú en teléfono (con la identidad encima). */
    COVER,

    /** Portada en pantallas anchas: un marco muy apaisado. */
    COVER_WIDE,

    /** Mosaico grande del bento y foto del platillo: enseñan la mesa. */
    TILE_LARGE,

    /** Mosaico chico del bento. */
    TILE_SMALL,

    /** Miniatura de fila: en macro, el fondo desaparece. */
    THUMB,
}

private val Whole = Framing(0.5f, 0.5f)

/** Una miniatura sin encuadre propio se acerca un poco al centro: casi toda foto de platillo trae aire alrededor. */
private val Macro = Framing(0.5f, 0.5f, 1.25f)

/** Portadas curadas de la biblioteca de demostración. */
private val covers: Map<String, Framing> = mapOf(
    "cover_brasa" to Framing(1f, 0.2f, 1.52f),      // la barra bajo las campanas de cobre
)

private val coversWide: Map<String, Framing> = mapOf(
    "cover_brasa" to Framing(1f, 0.25f, 1.35f),     // las campanas de cobre, sin los ventanales
)

private val largeTiles: Map<String, Framing> = mapOf(
    "arrachera_papas" to Framing(0.56f, 0.56f, 1.1f),
)

private val smallTiles: Map<String, Framing> = mapOf(
    "tacos_garbanzo_aguacate" to Framing(0.5f, 0.45f),
    "pastel_frambuesa" to Framing(0.73f, 0.45f, 1.2f),
    "ensalada_aguacate_garbanzo" to Framing(0.5f, 0.5f, 1.15f),
    "tacos_barbacoa" to Framing(0.5f, 0.6f, 1.1f),
)

private val thumbs: Map<String, Framing> = mapOf(
    "ensalada_aguacate_garbanzo" to Framing(0.52f, 0.46f, 1.6f),
    "albondigas_arugula" to Framing(0.30f, 0.50f, 1.9f),
    "camarones_caldillo_arroz" to Framing(0.52f, 0.62f, 1.9f),
    "tacos_barbacoa" to Framing(0.50f, 0.60f, 1.3f),
    "tacos_carnitas" to Framing(0.50f, 0.56f, 2.3f),
    "tacos_garbanzo_aguacate" to Framing(0.50f, 0.90f, 1.4f),
    "arrachera_papas" to Framing(0.83f, 0.83f, 1.43f),
    "costillas_bbq" to Framing(0.70f, 0.62f, 2f),
    "hamburguesa" to Framing(0.48f, 0.50f, 1.2f),
    "pastel_frambuesa" to Framing(0.63f, 0.44f, 1.5f),
    "pan_frances_platano" to Framing(0.50f, 0.35f, 1.3f),
    "margaritas" to Framing(0.54f, 0.46f, 1.7f),
    "te_helado_limon" to Framing(0.65f, 0.58f, 1.4f),
)

/** Nombre de archivo sin carpeta ni extensión: "res://demo/arrachera_papas.jpg" → "arrachera_papas". */
private fun photoKey(url: String?): String? =
    url?.substringBefore('?')?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

/**
 * Encuadre de [url] para [use]. Las fotos de la biblioteca de demostración traen el suyo; cualquier otra
 * (la de un restaurante real) va centrada y completa, y en miniatura con un acercamiento leve.
 */
internal fun framingFor(url: String?, use: PhotoUse): Framing {
    val key = photoKey(url)
    val curated = when (use) {
        PhotoUse.COVER -> covers[key]
        PhotoUse.COVER_WIDE -> coversWide[key]
        PhotoUse.TILE_LARGE -> largeTiles[key]
        PhotoUse.TILE_SMALL -> smallTiles[key]
        PhotoUse.THUMB -> thumbs[key]
    }
    return curated ?: if (use == PhotoUse.THUMB) Macro else Whole
}
