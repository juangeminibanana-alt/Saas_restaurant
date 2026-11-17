package app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
 * al backend. Muestra shimmer mientras carga y un degradado cálido con ícono si no hay imagen o falla.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun DishImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = AppTheme.shapes.md,
    contentScale: ContentScale = ContentScale.Crop,
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

    Box(modifier.clip(shape).background(colors.surfaceSunken)) {
        if (failed) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(listOf(colors.primarySoft, colors.surfaceSunken, colors.line)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(AppIcons.Utensils, colors.inkSubtle, size = 28.dp)
            }
        } else {
            if (!loaded) Box(Modifier.matchParentSize().shimmer())
            if (data != null) {
                val context = LocalPlatformContext.current
                val request = remember(url, data) {
                    ImageRequest.Builder(context).data(data).memoryCacheKey(url).build()
                }
                val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(Motion.SLOW, easing = Motion.standard), label = "img-alpha")
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha },
                    contentScale = contentScale,
                    onSuccess = { loaded = true },
                    onError = { failed = true },
                )
            }
        }
    }
}
