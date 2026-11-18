package app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.platform.defaultApiBaseUrl
import app.resources.Res
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val RES_PREFIX = "res://demo/"

/** Bytes de las fotos empaquetadas ya leídas (pocas y pequeñas): evita releer al hacer scroll. */
private val bundledBytes = HashMap<String, ByteArray>()

/** Configura una sola vez el cargador de imágenes (red vía Ktor + crossfade). Lo llama App() en la raíz. */
@Composable
fun InstallImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
}

/**
 * Foto de platillo / portada. Resuelve "res://demo/x.jpg" (recurso empaquetado) y URLs http(s) o relativas
 * al backend. La fotografía es lo único que brilla en NOCHE: va sin filtros, con un filete interior de 1 px
 * ([ring]) que la asienta sobre el negro. Mientras carga, o si no hay imagen, queda un marco plano oscuro
 * (sin degradado ni barrido de brillo).
 *
 * Encuadre por foto: [alignment] es el punto focal y [zoom] el acercamiento alrededor de él (1 = la foto
 * completa). Las miniaturas van en macro; sólo los mosaicos y las portadas enseñan la mesa.
 * Radio según el uso: miniatura `md` (12), mosaico `lg` (16), portada a sangre `RectangleShape` con `ring = false`.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun DishImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = AppTheme.shapes.md,
    contentScale: ContentScale = ContentScale.Crop,
    ring: Boolean = true,
    alignment: Alignment = Alignment.Center,
    zoom: Float = 1f,
) {
    val colors = AppTheme.colors
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    var loaded by remember(url) { mutableStateOf(false) }

    val data: Any? = when {
        url.isNullOrBlank() -> null
        url.startsWith(RES_PREFIX) -> {
            val path = "files/demo/" + url.removePrefix(RES_PREFIX)
            val bytes by produceState(bundledBytes[path], path) {
                if (value == null) {
                    value = try {
                        Res.readBytes(path).also { bundledBytes[path] = it }
                    } catch (e: Throwable) {
                        failed = true
                        null
                    }
                }
            }
            bytes
        }
        url.startsWith("/") -> defaultApiBaseUrl() + url
        else -> url
    }

    Box(modifier.clip(shape).background(colors.surfaceRaised)) {
        if (failed) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(AppIcons.Utensils, colors.inkSubtle, size = 24.dp)
            }
        } else if (data != null) {
            val context = LocalPlatformContext.current
            val request = remember(url, data) {
                ImageRequest.Builder(context).data(data).memoryCacheKey(url).build()
            }
            val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(Motion.BASE, easing = Motion.standard), label = "img-alpha")
            val origin = remember(alignment) { focalOrigin(alignment) }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    this.alpha = alpha
                    if (zoom != 1f) {
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = origin
                    }
                },
                alignment = alignment,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { failed = true },
            )
        }
        if (ring) Box(Modifier.matchParentSize().border(1.dp, colors.lineStrong, shape))
    }
}

/** Punto focal de una [Alignment] como origen de transformación (0..1 en cada eje). */
private fun focalOrigin(alignment: Alignment): TransformOrigin {
    val probe = alignment.align(IntSize.Zero, IntSize(1000, 1000), LayoutDirection.Ltr)
    return TransformOrigin(probe.x / 1000f, probe.y / 1000f)
}
