package app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import app.ui.theme.AppColors
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay

// ───────────────────────── Superficie flotante (compartida) ─────────────────────────

/** Lo que flota en NOCHE no proyecta sombra ni desenfoca: superficie 1 con filete, sobre un velo negro al 60 %. */
private fun Modifier.floatingSurface(colors: AppColors, shape: Shape): Modifier =
    this.clip(shape).background(colors.surface).border(1.dp, colors.lineStrong, shape)

/** Consume los toques para que no lleguen al velo que está detrás. */
private fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

// ───────────────────────── Diálogo ─────────────────────────

@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    maxWidth: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { dismiss() },
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        val colors = AppTheme.colors
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.BASE, easing = Motion.standard)) }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                    .background(colors.scrim)
                    .pointerInput(Unit) { detectTapGestures { dismiss() } },
            )
            Column(
                Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Space.x4)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Entrada corta y sin rebote: fundido + 8 px de recorrido.
                        val p = appear.value
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                        translationY = 8.dp.toPx() * (1f - p)
                    }
                    .floatingSurface(colors, AppTheme.shapes.lg)
                    .swallowTaps()
                    .then(modifier),
            ) {
                CompositionLocalProvider(LocalSurfaceLevel provides 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Space.x6, end = Space.x3, top = Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (title != null) AppText(title, style = AppTheme.type.h2, maxLines = 2)
                        }
                        AppIconButton(AppIcons.Close, onClick = { dismiss() }, contentDescription = "Cerrar", tint = colors.inkMuted, plain = true)
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = Space.x6, end = Space.x6, top = Space.x2, bottom = Space.x6),
                        content = content,
                    )
                }
            }
        }
    }
}

// ───────────────────────── Hoja inferior ─────────────────────────

/**
 * Overlay animado dentro del árbol: llamar dentro de un Box que llene la pantalla.
 * En COMPACT/MEDIUM sube desde abajo (se puede arrastrar del asa para cerrar); en EXPANDED se centra como diálogo.
 * El contenido NO hace scroll por sí solo: usa `Modifier.weight(1f).verticalScroll(...)` o un LazyColumn dentro.
 */
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(visible, label = "sheet")
    if (!transition.currentState && !transition.targetState) return

    val dismiss by rememberUpdatedState(onDismiss)
    app.platform.PlatformBackHandler(enabled = visible) { dismiss() }
    val colors = AppTheme.colors
    val centered = LocalWindowSize.current == WindowSize.EXPANDED

    val scrimAlpha by transition.animateFloat(
        transitionSpec = { tween(if (targetState) Motion.BASE else Motion.BASE, easing = if (targetState) Motion.standard else Motion.exit) },
        label = "scrim",
    ) { if (it) 1f else 0f }
    val hidden by transition.animateFloat(
        transitionSpec = { if (targetState) tween(Motion.SLOW, easing = Motion.standard) else tween(Motion.BASE, easing = Motion.exit) },
        label = "offset",
    ) { if (it) 0f else 1f }

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(24f)) {
        val availableHeight = maxHeight
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = scrimAlpha.coerceIn(0f, 1f) }
                .background(colors.scrim)
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )

        if (centered) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Space.x8)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .then(if (fullHeight) Modifier.fillMaxHeight(0.92f) else Modifier.heightIn(max = availableHeight * 0.88f))
                    .graphicsLayer {
                        val p = 1f - hidden
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                        translationY = 8.dp.toPx() * hidden
                    }
                    .floatingSurface(colors, AppTheme.shapes.lg)
                    .swallowTaps()
                    .then(modifier),
            ) {
                CompositionLocalProvider(LocalSurfaceLevel provides 1) { content() }
            }
        } else {
            val tail = 48.dp // cola bajo el borde inferior: la hoja nunca deja ver un hueco al asentarse
            val baseShape = AppTheme.shapes.lg
            val shape: Shape = (baseShape as? CornerBasedShape)
                ?.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                ?: RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            var dragPx by remember { mutableFloatStateOf(0f) }
            val dismissThreshold = with(LocalDensity.current) { 96.dp.toPx() }
            LaunchedEffect(visible) { if (visible) dragPx = 0f }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .then(
                        if (fullHeight) Modifier.height(availableHeight * 0.92f + tail)
                        else Modifier.heightIn(max = availableHeight * 0.92f + tail),
                    )
                    .graphicsLayer { translationY = tail.toPx() + hidden * size.height + dragPx }
                    .floatingSurface(colors, shape)
                    .swallowTaps()
                    .then(modifier)
                    .padding(bottom = tail)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
                            onDragStopped = { velocity ->
                                if (dragPx > dismissThreshold || velocity > 1400f) {
                                    dismiss()
                                } else {
                                    animate(dragPx, 0f, animationSpec = Motion.snappy()) { value, _ -> dragPx = value }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).background(colors.lineStrong, AppTheme.shapes.xs))
                }
                CompositionLocalProvider(LocalSurfaceLevel provides 1) { content() }
            }
        }
    }
}

// ───────────────────────── Avisos (toasts) ─────────────────────────

internal class ToastEntry(val id: Long, val message: String, val tone: Tone) {
    val visibility = MutableTransitionState(false)
}

object Toasts {
    internal val entries = mutableStateListOf<ToastEntry>()
    private var counter = 0L

    fun show(message: String, tone: Tone = Tone.NEUTRAL) {
        if (message.isBlank()) return
        counter += 1
        entries.add(ToastEntry(counter, message, tone))
        while (entries.size > 3) entries.removeAt(0)
    }
}

/** Lo coloca App() una sola vez, encima de todo. No intercepta toques fuera de los avisos. */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .zIndex(40f)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.x4, vertical = Space.x6),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            Toasts.entries.forEach { entry ->
                key(entry.id) { ToastItem(entry) }
            }
        }
    }
}

@Composable
private fun ToastItem(entry: ToastEntry) {
    val colors = AppTheme.colors
    LaunchedEffect(entry.id) {
        entry.visibility.targetState = true
        delay(2600)
        entry.visibility.targetState = false
        delay(Motion.BASE + 120L)
        Toasts.entries.remove(entry)
    }
    AnimatedVisibility(
        visibleState = entry.visibility,
        enter = slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { it / 2 } + fadeIn(tween(Motion.BASE)),
        exit = slideOutVertically(tween(Motion.BASE, easing = Motion.exit)) { it / 2 } + fadeOut(tween(Motion.BASE, easing = Motion.exit)),
    ) {
        // Aviso plano: superficie 2 con filete, punto sólido del tono y texto en tinta. Sin sombra.
        val shape = AppTheme.shapes.md
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .clip(shape)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.lineStrong, shape)
                .padding(horizontal = Space.x4, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.tone != Tone.NEUTRAL) {
                StatusDot(toneColor(entry.tone))
                Spacer(Modifier.width(Space.x3))
            }
            AppText(entry.message, style = AppTheme.type.bodySm, color = colors.ink, maxLines = 3)
        }
    }
}
