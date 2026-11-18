package app.feature.diner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.shared.model.MenuItem
import app.shared.model.ModifierGroup
import app.shared.model.ModifierOption
import app.shared.util.formatMoney
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.BottomBar
import app.ui.components.DishImage
import app.ui.components.LabelText
import app.ui.components.OptionList
import app.ui.components.OptionRow
import app.ui.components.QuantityStepper
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.components.edgeBottom
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import androidx.compose.foundation.focusable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key as keyCode
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Foto del platillo: toda la altura que el resto deja libre (texto + dos grupos + barra + aire = 532). */
private val DishHeroReserve = 532.dp
private val DishHeroMin = 280.dp
private val DishHeroMax = 420.dp

/** En pantallas anchas el platillo es un diálogo centrado de este ancho, con la foto a 16:10. */
private val DishDialogWidth = 480.dp
private val DishDialogHero = 300.dp

/** Debajo de este ancho el nombre del platillo baja un paso (28/32) para seguir compartiendo renglón con su precio. */
private val NarrowScreen = 375.dp

/**
 * Detalle del platillo. En teléfono es una página completa que sube sobre el menú (foto a sangre arriba,
 * barra sólida abajo); en pantallas anchas, el mismo contenido en un diálogo centrado sobre un velo.
 * Vive dentro del árbol: llamar dentro de un Box que llene la pantalla.
 */
@Composable
fun DishSheet(state: DinerState, viewportHeight: Dp) {
    val transition = updateTransition(state.sheetVisible, label = "dish")
    if (!transition.currentState && !transition.targetState) return

    app.platform.PlatformBackHandler(enabled = state.sheetVisible) { state.closeSheet() }
    val colors = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val hidden by transition.animateFloat(
        transitionSpec = { if (targetState) tween(Motion.SLOW, easing = Motion.standard) else tween(Motion.BASE, easing = Motion.exit) },
        label = "dish-offset",
    ) { if (it) 0f else 1f }
    val item = state.sheetItem
    // Escape cierra el platillo en web y escritorio (en Android lo hace "atrás").
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.sheetVisible) {
        if (state.sheetVisible) {
            try {
                focus.requestFocus()
            } catch (e: IllegalStateException) {
                // Aún sin nodo de foco en este cuadro: el botón de cerrar sigue disponible.
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(24f)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.keyCode == Key.Escape && state.sheetVisible) {
                    state.closeSheet()
                    true
                } else {
                    false
                }
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (1f - hidden).coerceIn(0f, 1f) }
                .background(colors.scrim)
                .pointerInput(Unit) { detectTapGestures { state.closeSheet() } },
        )
        // El platillo se compone sobre el fondo: sus controles van en superficie 1, como en el menú.
        CompositionLocalProvider(LocalSurfaceLevel provides 0) {
            if (compact) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = hidden * size.height }
                        .background(colors.canvas)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    if (item != null) {
                        key(item.id, state.sheetSession) {
                            DishDetail(
                                item = item,
                                state = state,
                                heroHeight = (viewportHeight - DishHeroReserve).coerceIn(DishHeroMin, DishHeroMax),
                                fill = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                val shape = AppTheme.shapes.lg
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .padding(Space.x8)
                        .widthIn(max = DishDialogWidth)
                        .fillMaxWidth()
                        .heightIn(max = viewportHeight * 0.9f)
                        .graphicsLayer {
                            alpha = ((1f - hidden) * 1.6f).coerceIn(0f, 1f)
                            translationY = Space.x2.toPx() * hidden
                        }
                        .clip(shape)
                        .background(colors.canvas)
                        .border(Sizes.hairline, colors.lineStrong, shape)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    if (item != null) {
                        key(item.id, state.sheetSession) {
                            DishDetail(item = item, state = state, heroHeight = DishDialogHero, fill = false, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/**
 * La guarnición "viene incluida": un grupo obligatorio de una sola elección cuya primera opción no cuesta y
 * alguna otra sí (las demás son mejoras). Esa primera opción ya llega elegida; cambiarla es opcional.
 * Un grupo obligatorio sin esa forma (el término de la carne) no tiene valor por defecto: hay que elegir.
 */
private fun includedDefault(group: ModifierGroup): ModifierOption? {
    if (group.minSelect != 1 || group.maxSelect > 1) return null
    val first = group.options.firstOrNull() ?: return null
    return if (first.priceDeltaCents == 0L && group.options.any { it.priceDeltaCents > 0L }) first else null
}

/** Elección corta: obligatoria, de una sola opción, hasta cuatro etiquetas breves y ninguna con costo. */
private fun fitsSegments(group: ModifierGroup): Boolean =
    group.minSelect >= 1 && group.maxSelect <= 1 && group.options.size in 2..4 &&
        group.options.all { it.priceDeltaCents == 0L && it.name.length <= 14 } &&
        group.options.sumOf { it.name.length } <= 34

/** La regla del grupo, para su etiqueta en mono: "Elige 1", "Elige 1 · incluida", "Opcional · hasta 3". */
private fun groupRule(group: ModifierGroup): String = when {
    group.minSelect > 0 && group.maxSelect <= 1 -> if (includedDefault(group) != null) "Elige 1 · incluida" else "Elige 1"
    group.minSelect > 0 && group.minSelect == group.maxSelect -> "Elige ${group.minSelect}"
    group.minSelect > 0 -> "Elige de ${group.minSelect} a ${group.maxSelect}"
    group.maxSelect <= 1 -> "Opcional"
    else -> "Opcional · hasta ${group.maxSelect}"
}

private fun deltaText(option: ModifierOption): String? = when {
    option.priceDeltaCents > 0L -> "+" + formatMoney(option.priceDeltaCents)
    option.priceDeltaCents < 0L -> formatMoney(option.priceDeltaCents)
    else -> null
}

@Composable
private fun DishDetail(item: MenuItem, state: DinerState, heroHeight: Dp, fill: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val selected = remember {
        mutableStateMapOf<String, List<String>>().apply {
            item.modifierGroups.forEach { g -> includedDefault(g)?.let { put(g.id, listOf(it.id)) } }
        }
    }
    var quantity by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Dónde empieza cada grupo dentro del contenido desplazable: "Agregar" lleva al primero que falte.
    val groupTops = remember { mutableStateMapOf<String, Int>() }
    // El grupo queda bajo la franja del botón de cerrar (72), no debajo de él.
    val revealGap = with(LocalDensity.current) { (Sizes.bar - Space.x6).roundToPx() }

    val chosen: List<ModifierOption> = item.modifierGroups.flatMap { g ->
        val ids = selected[g.id] ?: emptyList()
        g.options.filter { it.id in ids }
    }
    val unitCents = item.priceCents + chosen.sumOf { it.priceDeltaCents }
    val totalCents = unitCents * quantity
    val missing = item.modifierGroups.filter { (selected[it.id]?.size ?: 0) < it.minSelect }
    val category = remember(item.categoryId, state.menu) { state.menu.categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty() }
    // Antetítulo: la categoría y, si las hay, las señas del platillo (vegano, picante…).
    val eyebrow = remember(category, item.tags) {
        (listOf(category) + item.tags.filterNot { it.equals("popular", ignoreCase = true) }.map { tagLabel(it) })
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f, fill = fill).fillMaxWidth()) {
            val nameStyle = if (maxWidth < NarrowScreen) AppTheme.type.h1.copy(fontSize = 28.sp, lineHeight = 32.sp) else AppTheme.type.h1
            Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                // La foto es el producto: entera, a sangre, con un filete al pie.
                DishImage(
                    item.imageUrl,
                    modifier = Modifier.fillMaxWidth().height(heroHeight).edgeBottom(colors.lineStrong),
                    contentDescription = item.name,
                    shape = RectangleShape,
                    ring = false,
                )
                Column(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.x5)) {
                    if (eyebrow.isNotBlank()) {
                        LabelText(eyebrow)
                        Spacer(Modifier.height(Space.x1))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        AppText(
                            item.name,
                            modifier = Modifier.weight(1f).alignByBaseline().offset(x = -Sizes.hairline),
                            style = nameStyle,
                        )
                        AppText(
                            formatMoney(item.priceCents),
                            modifier = Modifier.padding(start = Space.x4).alignByBaseline(),
                            style = AppTheme.type.monoLg,
                            maxLines = 1,
                        )
                    }
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(Space.x2))
                        AppText(item.description, style = AppTheme.type.body, color = colors.inkMuted)
                    }
                }
                item.modifierGroups.forEach { group ->
                    ModifierGroupSection(
                        group = group,
                        selected = selected[group.id] ?: emptyList(),
                        onToggle = { option ->
                            val current = selected[group.id] ?: emptyList()
                            selected[group.id] = when {
                                group.maxSelect <= 1 -> if (option.id in current && group.minSelect == 0) emptyList() else listOf(option.id)
                                option.id in current -> current - option.id
                                current.size < group.maxSelect -> current + option.id
                                else -> current
                            }
                        },
                        modifier = Modifier
                            .onPlaced { groupTops[group.id] = it.positionInParent().y.roundToInt() }
                            .padding(start = Space.gutter, end = Space.gutter, top = Space.x6),
                    )
                }
                AppTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 140) notes = it },
                    modifier = Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.x6),
                    label = "Notas para cocina",
                    placeholder = "Sin cebolla, salsa aparte…",
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(Space.x6))
            }
            AppIconButton(
                AppIcons.Close,
                onClick = { state.closeSheet() },
                modifier = Modifier.align(Alignment.TopStart).padding(Space.gutter),
                contentDescription = "Cerrar y volver al menú",
                filled = true,
            )
        }
        BottomBar {
            QuantityStepper(quantity = quantity, onChange = { quantity = it.coerceIn(1, 20) }, min = 1, max = 20)
            AppButton(
                text = if (item.available) "Agregar" else "Agotado",
                onClick = {
                    if (missing.isNotEmpty()) {
                        Toasts.show("Elige una opción en: " + missing.joinToString(", ") { it.name }, Tone.WARNING)
                        // Lleva la vista al primer grupo pendiente (su etiqueta ya avisa con el punto de brasa).
                        groupTops[missing.first().id]?.let { top ->
                            scope.launch { scroll.animateScrollTo((top - revealGap).coerceAtLeast(0), tween(Motion.BASE, easing = Motion.standard)) }
                        }
                    } else {
                        state.add(item, quantity, chosen.map { it.id }, notes.trim())
                        state.closeSheet()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = item.available,
                mono = if (item.available) formatMoney(totalCents) else null,
            )
        }
    }
}

/**
 * Un grupo de opciones: título 16/20 y su regla en mono. Mientras falta una elección obligatoria la regla va
 * en tinta con un punto de brasa; resuelto el grupo, vuelve a ser una etiqueta más.
 * Elección corta (el término): control segmentado. Lo demás: lista con radio o casilla y el costo en mono.
 */
@Composable
private fun ModifierGroupSection(group: ModifierGroup, selected: List<String>, onToggle: (ModifierOption) -> Unit, modifier: Modifier = Modifier) {
    val single = group.maxSelect <= 1
    val pending = selected.size < group.minSelect
    Column(modifier.fillMaxWidth()) {
        GroupHead(group.name, label = groupRule(group), pending = pending)
        if (fitsSegments(group)) {
            ChoiceSegments(
                options = group.options.map { it.name },
                selectedIndex = group.options.indexOfFirst { it.id in selected },
                onSelect = { index -> group.options.getOrNull(index)?.let(onToggle) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OptionList {
                group.options.forEachIndexed { index, option ->
                    val checked = option.id in selected
                    OptionRow(
                        text = option.name,
                        selected = checked,
                        onClick = { onToggle(option) },
                        trailing = deltaText(option),
                        multiple = !single,
                        first = index == 0,
                        enabled = single || checked || selected.size < group.maxSelect,
                    )
                }
            }
        }
    }
}

/**
 * Control segmentado del platillo (48 de alto, cuerpo 16): como el del sistema, pero admite "nada elegido"
 * ([selectedIndex] = −1) — un grupo obligatorio sin valor por defecto abre sin pastilla, y la pastilla de tinta
 * aparece con la primera elección. Cada segmento mide su texto en seminegrita y el sobrante se reparte.
 */
@Composable
internal fun ChoiceSegments(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val pillShape = AppTheme.shapes.sm
    val pillColor = colors.select
    val regular = AppTheme.type.title
    val strong = AppTheme.type.h3
    val height = Sizes.controlLg

    // índice -> (x, ancho) en px dentro del riel; la pastilla se desliza hasta el segmento activo
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = if (selectedIndex in options.indices) bounds[selectedIndex] else null
    val pillAlpha by animateFloatAsState(if (target != null) 1f else 0f, tween(Motion.FAST), label = "pill-alpha")
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (!placed) {
            left.snapTo(t.first)
            width.snapTo(t.second)
            placed = true
        } else {
            launch { left.animateTo(t.first, Motion.snappy()) }
            launch { width.animateTo(t.second, Motion.snappy()) }
        }
    }

    Layout(
        modifier = modifier
            .height(height)
            .background(AppTheme.controlFill, shape)
            .border(Sizes.hairline, colors.line, shape)
            .drawBehind {
                if (width.value > 0f && pillAlpha > 0f) {
                    val pad = Space.x1.toPx()
                    val pill = Size((width.value - pad * 2).coerceAtLeast(0f), size.height - pad * 2)
                    val outline = pillShape.createOutline(pill, layoutDirection, this)
                    translate(left.value + pad, pad) { drawOutline(outline, pillColor, alpha = pillAlpha) }
                }
            },
        content = {
            options.forEachIndexed { index, option ->
                val isOn = index == selectedIndex
                val textColor by animateColorAsState(if (isOn) colors.onSelect else colors.inkMuted, tween(Motion.FAST), label = "segment-text")
                Box(
                    modifier = Modifier
                        .onPlaced { coords ->
                            val next = coords.positionInParent().x to coords.size.width.toFloat()
                            if (bounds[index] != next) bounds[index] = next
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .selectable(
                            selected = isOn,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                        ) { if (!isOn) onSelect(index) }
                        .padding(horizontal = Space.x1),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(option, modifier = Modifier.alpha(0f).clearAndSetSemantics { }, style = strong, maxLines = 1)
                    AppText(option, style = if (isOn) strong else regular, color = textColor, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        },
    ) { measurables, constraints ->
        val n = measurables.size
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else height.roundToPx()
        if (n == 0) return@Layout layout(constraints.minWidth, h) { }
        // Como `flex: 1 0 auto`: base = ancho del contenido; el sobrante se reparte por igual.
        val natural = IntArray(n) { measurables[it].maxIntrinsicWidth(h) }
        val total = natural.sum()
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else total.coerceAtLeast(constraints.minWidth)
        val widths = IntArray(n)
        var used = 0
        for (i in 0 until n) {
            widths[i] = if (total <= w) natural[i] + (w - total) / n else (natural[i].toLong() * w / total.coerceAtLeast(1)).toInt()
            used += widths[i]
        }
        widths[n - 1] += w - used
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i].coerceAtLeast(0), h)) }
        layout(w, h) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, 0)
                x += p.width
            }
        }
    }
}
