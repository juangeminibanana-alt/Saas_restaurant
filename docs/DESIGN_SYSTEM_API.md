# Sistema de diseño — contrato de API (Compose Multiplatform)

Fuente de verdad visual: **NOCHE** ("oscuro de precisión"). Reglas, tokens y estados en
[`docs/diseno/noche/SISTEMA.md`](diseno/noche/SISTEMA.md); maquetas de referencia en `docs/diseno/noche/`
(`style.css`, `menu.html`, `platillo.html` y sus capturas).
Este documento es el **contrato**: quien implementa el sistema de diseño respeta estas firmas exactas;
quien construye pantallas programa contra ellas. Si necesitas algo que no está aquí, créalo dentro de tu
propio paquete de feature (no edites archivos de otro dueño) usando tokens, nunca colores o medidas sueltas.

Reglas para TODO el código en `composeApp/src/commonMain`: Kotlin común puro (nada de `java.*`,
`String.format`, `System.*`, `synchronized`, `Dispatchers.IO`); hora con `app.shared.util.nowMillis()`;
dinero con `app.shared.util.formatMoney(cents)`; textos de UI en español de México; cero nombres o logos
de plataforma (marca blanca). Áreas táctiles ≥ 44dp, contraste AA.

Las cinco reglas de NOCHE que el código debe cumplir:

1. La interfaz es negra, plana y exacta; la fotografía es lo único que brilla.
2. La brasa (`colors.primary`) significa "tu pedido" y sólo existe como color **sólido**: la acción principal,
   la cantidad agregada y el aviso de una elección pendiente. Nunca como contorno, texto o ícono.
3. La selección se marca con tinta sólida (`colors.select` / `colors.onSelect`), no con la brasa.
4. Sin sombras, sin resplandores, sin desenfoques: la profundidad es un filete de 1 px (`Modifier.ring`).
   El único degradado es el velo bajo texto que va sobre una fotografía (`Modifier.photoScrim`).
5. Marca blanca: de un restaurante sólo entran su color de acento y su fotografía. Tipografía, radios y modo
   oscuro los fija el sistema.

## Tokens — `package app.ui.theme`

```kotlin
@Composable fun AppTheme(brand: BrandTheme? = null, content: @Composable () -> Unit)
// Del tenant sólo se lee brand.primary (acento; por defecto #FF6A3D). fontDisplay, fontBody, radius y mode se ignoran.
object AppTheme {                      // accesores @Composable @ReadOnlyComposable
    val colors: AppColors
    val type: AppTypography
    val shapes: AppShapes
    val controlFill: Color             // relleno de un control según su contenedor (ver LocalSurfaceLevel)
}
val DefaultAccent: Color               // #FF6A3D

@Immutable data class AppColors(
    val canvas: Color, val surface: Color, val surfaceRaised: Color, val surfaceSunken: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color, val inkSubtle: Color,
    val primary: Color, val onPrimary: Color, val primarySoft: Color,
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    val successSoft: Color, val warningSoft: Color, val dangerSoft: Color, val infoSoft: Color,
    val scrim: Color, val isDark: Boolean,          // isDark siempre true
    val select: Color, val onSelect: Color,         // NUEVO: selección = tinta sólida
    val positive: Color,                            // NUEVO: "abierto" (= success)
)
@Immutable data class AppTypography(
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val title: TextStyle, val body: TextStyle, val bodySm: TextStyle, val label: TextStyle,
    val titleSm: TextStyle,                         // NUEVO: 15/20 media (nombre en mosaico chico, navegación)
    val monoLabel: TextStyle,                       // NUEVO: Geist Mono 11/16 +0.06em — usar con LabelText (MAYÚSCULAS)
    val mono: TextStyle,                            // NUEVO: Geist Mono 13/20 — precios, conteos, tiempos
    val monoMd: TextStyle,                          // NUEVO: Geist Mono 16/20 — precio en mosaico, cantidad grande
    val monoLg: TextStyle,                          // NUEVO: Geist Mono 20/24 — total, precio del platillo, métrica
)
@Immutable data class AppShapes(val xs: Shape, val sm: Shape, val md: Shape, val lg: Shape, val control: Shape, val pill: Shape)

object Space {   // retícula de 4
    val x1 = 4.dp; val x2 = 8.dp; val x3 = 12.dp; val x4 = 16.dp; val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp
    val x10 = 40.dp; val x14 = 56.dp; val x18 = 72.dp
    val gutter = 16.dp   // NUEVO: margen lateral de página
    val tap = 44.dp      // NUEVO: objetivo táctil mínimo
}
object Sizes {   // NUEVO: medidas fijas de componentes
    val hairline = 1.dp; val icon = 20.dp
    val controlSm = 32.dp   // cara de un control de fila (agregar, stepper chico)
    val control = 44.dp     // botón de ícono, segmentado
    val controlLg = 48.dp   // botón, campo, fila de opción, stepper grande
    val controlXl = 56.dp   // botón LG
    val bar = 72.dp         // barra inferior / navegación pegada
    val thumb = 80.dp       // miniatura de fila de platillo
}
object Motion {
    const val FAST = 120; const val BASE = 220; const val SLOW = 360; const val HERO = 520   // ms
    val standard: Easing            // cubic-bezier(.2,0,0,1)
    val exit: Easing                // cubic-bezier(.4,0,1,1)
    fun <T> snappy(): SpringSpec<T> // stiffness 500, damping .8
    fun <T> gentle(): SpringSpec<T> // stiffness 200, damping .85
    fun <T> bouncy(): SpringSpec<T> // se conserva; en NOCHE ya no rebota (stiffness 600, damping .9)
}
object ThemeController { var darkOverride: Boolean?; fun toggle(currentIsDark: Boolean) }   // sin efecto: NOCHE es sólo oscuro
fun parseHexColor(hex: String?): Color?
enum class WindowSize { COMPACT, MEDIUM, EXPANDED }             // <600dp, <1024dp, resto
val LocalWindowSize: ProvidableCompositionLocal<WindowSize>     // lo provee App() en la raíz
val LocalContentColor: ProvidableCompositionLocal<Color>        // color heredado dentro de superficies sólidas
val LocalSurfaceLevel: ProvidableCompositionLocal<Int>          // NUEVO: 0 fondo · 1 superficie 1 · 2 superficie 2
```

Valores de color (fijos; sólo `primary` cambia por restaurante):

| Token Kotlin | NOCHE (`style.css`) | Valor | Uso |
|---|---|---|---|
| `canvas` | `--bg` | `#0B0B0C` | fondo |
| `surface` | `--surface-1` | `#141416` | controles, mosaicos, barras |
| `surfaceRaised` | `--surface-2` | `#1B1B1E` | control sobre superficie 1 |
| `surfaceSunken` | — | `#1B1B1E` | alias heredado de superficie 2 (no hay hundidos) |
| `line` | `--line` | blanco 8 % | filete |
| `lineStrong` | `--line-strong` | blanco 14 % | filete sobre fotografía / énfasis |
| `ink` | `--ink` | `#F2F2F3` | texto principal (17.6 : 1) |
| `inkMuted` | `--muted` | `#A1A1A8` | texto secundario (7.6 : 1) |
| `inkSubtle` | `--faint` | `#8A8A92` | etiquetas (5.6 : 1) |
| `select` / `onSelect` | `--select` / `--on-select` | `#F2F2F3` / `#0B0B0C` | selección: tinta sólida |
| `primary` / `onPrimary` | `--accent` / `--on-accent` | tenant (def. `#FF6A3D`) / por contraste | "tu pedido", sólo sólido |
| `primarySoft` | — | acento 14 % sobre superficie 1, opaco | sólo si de verdad hace falta |
| `positive` = `success` | `--positive` | `#3ECF8E` | abierto / correcto |
| `warning` · `danger` · `info` | — | `#F2B84B` · `#F2555A` · `#6CB4FF` | sólo paneles: punto o texto |
| `*Soft` | — | tono 14 % sobre superficie 1, opaco | avisos de panel |
| `scrim` | — | negro 60 % | velo de diálogos y hojas |

Tipos (Geist; `px/interlínea`, peso, tracking): display 40/44 600 −0.04em · h1 32/36 600 −0.035em ·
h2 20/24 600 −0.02em · h3 16/20 600 −0.011em · title 16/20 500 −0.011em · titleSm 15/20 500 −0.015em ·
body 16/24 400 −0.011em · bodySm 13/20 400 · label 11/16 500. Geist Mono: monoLabel 11/16 500 +0.06em ·
mono 13/20 500 · monoMd 16/20 500 −0.02em · monoLg 20/24 500 −0.02em. Pesos disponibles: 400/500/600 (sans) y
500/600 (mono); `FontWeight.Bold` cae en 600.
Radios: xs 4 · sm 8 · md 12 · lg 16 · control = md · pill = sm (no hay cápsulas).

## Componentes — `package app.ui.components`

```kotlin
@Composable fun AppText(text: String, modifier: Modifier = Modifier, style: TextStyle = AppTheme.type.body, color: Color = AppTheme.colors.ink, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null)
@Composable fun LabelText(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.inkSubtle, maxLines: Int = 1, textAlign: TextAlign? = null, dot: Color? = null)   // NUEVO: etiqueta mono en MAYÚSCULAS; dot = punto sólido de 6 px antes del texto
@Composable fun StatusDot(color: Color, modifier: Modifier = Modifier)   // NUEVO: punto sólido de 6 px

enum class ButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }
enum class ButtonSize { SM, MD, LG }   // 40 · 48 · 56
@Composable fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: ButtonVariant = ButtonVariant.PRIMARY, size: ButtonSize = ButtonSize.MD, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, fullWidth: Boolean = false,
    trailingIcon: ImageVector? = null,   // NUEVO: "Ver pedido →"
    mono: String? = null)                // NUEVO: dato en mono tras un punto medio: "Agregar · $345"
@Composable fun AppIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = AppTheme.colors.ink, filled: Boolean = false, badge: Int? = null,
    plain: Boolean = false)              // NUEVO: sin superficie ni filete (acción dentro de fila o encabezado)
@Composable fun CountChip(count: Int, modifier: Modifier = Modifier, size: Dp = 20.dp)   // NUEVO: ficha sólida de brasa con conteo en mono
fun Modifier.pressable(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier   // click + escala al presionar, sin ripple

fun Modifier.ring(color: Color, shape: Shape, width: Dp = 1.dp): Modifier        // NUEVO: filete interior
fun Modifier.edgeTop(color: Color, width: Dp = 1.dp): Modifier                   // NUEVO: filete arriba
fun Modifier.edgeBottom(color: Color, width: Dp = 1.dp): Modifier                // NUEVO: filete abajo
fun Modifier.flatSurface(fill: Color, line: Color, shape: Shape): Modifier       // NUEVO: recorte + relleno + filete
fun Modifier.photoScrim(color: Color): Modifier                                  // NUEVO: velo bajo texto sobre foto (sólido a 32 dp del pie, transparente a 208)

@Composable fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = Space.x4, elevated: Boolean = false, content: @Composable ColumnScope.() -> Unit)
@Composable fun AppDivider(modifier: Modifier = Modifier)
@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null,
    label: String? = null)               // NUEVO: etiqueta mono a la derecha, alineada a la línea base del título

@Composable fun AppTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String = "", leadingIcon: ImageVector? = null, singleLine: Boolean = true, isPassword: Boolean = false, error: String? = null, keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, enabled: Boolean = true)
@Composable fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
@Composable fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    large: Boolean = false)              // NUEVO: 48 de alto y cuerpo 16 (elecciones del platillo)
@Composable fun CategoryTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)   // riel segmentado con scroll horizontal
@Composable fun AppChip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, icon: ImageVector? = null)

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, DANGER, INFO }
@Composable fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL, dot: Boolean = true)
@Composable fun TagBadge(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL)

@Composable fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, multiple: Boolean = false)   // NUEVO: radio / casilla de 20 px en tinta
@Composable fun OptionList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)       // NUEVO: bloque superficie + filete, radio 12
@Composable fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, multiple: Boolean = false, first: Boolean = false, enabled: Boolean = true)   // NUEVO: fila de 48; first = sin filete superior

@Composable fun DishImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null, shape: Shape = AppTheme.shapes.md, contentScale: ContentScale = ContentScale.Crop,
    ring: Boolean = true,                         // NUEVO: filete interior de 1 px (lineStrong)
    alignment: Alignment = Alignment.Center,      // NUEVO: punto focal (BiasAlignment para un punto libre)
    zoom: Float = 1f)                             // NUEVO: acercamiento alrededor del punto focal
@Composable fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = 99, compact: Boolean = false,
    inOrder: Boolean = compact && quantity > 0)   // NUEVO: la cantidad es una ficha sólida de brasa
@Composable fun AddToCartButton(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier)   // "+" de 32 que se vuelve stepper
@Composable fun Skeleton(modifier: Modifier = Modifier, shape: Shape = AppTheme.shapes.sm)
fun Modifier.shimmer(): Modifier   // pulso plano (ya no hay barrido de brillo)

@Composable fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 36.dp, imageUrl: String? = null)
@Composable fun BrandMark(name: String, logoUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp)   // logo del tenant o monograma en tinta

@Composable fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit)           // NUEVO: barra inferior sólida con filete
@Composable fun BottomBarSummary(label: String, value: String, modifier: Modifier = Modifier)               // NUEVO: "3 PLATILLOS" + total en mono, 120 de ancho

@Composable fun AppDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, maxWidth: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit)
@Composable fun AppBottomSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, fullHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit)   // overlay animado; en EXPANDED se centra como diálogo. Llamar dentro de un Box que llene la pantalla.
object Toasts { fun show(message: String, tone: Tone = Tone.NEUTRAL) }
@Composable fun ToastHost(modifier: Modifier = Modifier)   // lo coloca App() una sola vez

@Composable fun LoadingState(modifier: Modifier = Modifier, message: String? = null)
@Composable fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null)
@Composable fun EmptyState(title: String, modifier: Modifier = Modifier, message: String = "", icon: ImageVector? = null, actionText: String? = null, onAction: (() -> Unit)? = null)

@Composable fun StatCard(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaPositive: Boolean = true, icon: ImageVector? = null, series: List<Double>? = null)
@Composable fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)   // color = sólo el último valor
@Composable fun BarChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)   // color = sólo la barra más alta
@Composable fun LineChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)  // color = sólo el último valor

@Composable fun OrderStatusTimeline(order: Order, modifier: Modifier = Modifier)   // Pagado → En preparación → Listo → Entregado
@Composable fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 168.dp)   // QR real, siempre negro sobre blanco

data class NavItem(val id: String, val label: String, val icon: ImageVector, val badge: Int? = null,
    val section: String? = null)         // NUEVO: nombre de grupo; se dibuja como etiqueta mono al cambiar
@Composable fun AdminScaffold(   // barra lateral en EXPANDED, riel en MEDIUM, barra inferior en COMPACT
    title: String, navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, brand: @Composable () -> Unit = {}, userName: String? = null, userRole: String? = null,
    onLogout: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit,
)
@Composable fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    label: String? = null,               // NUEVO: antetítulo en mono MAYÚSCULAS
    actions: @Composable RowScope.() -> Unit = {})

object AppIcons {   // ImageVector, trazo único (1.5 px a 20dp) redondeado, viewport 24
    val Add; val Remove; val Close; val Back; val Forward; val Check; val Search; val Cart; val Bag; val Clock; val Flame; val Star; val Heart
    val Menu; val Settings; val Edit; val Delete; val Home; val Store; val Receipt; val Chart; val Users; val CreditCard; val Palette; val Link
    val Qr; val Bell; val Moon; val Sun; val Logout; val Phone; val Location; val Info; val Warning; val ChevronRight; val ChevronDown; val Bike
    val Chef; val Plug; val Dashboard; val Tag; val Eye; val Refresh; val Image; val Cash; val Bank; val Wallet; val Sparkle; val Filter
    val MoreVert; val ExternalLink; val Copy; val Lock; val Mail; val Person; val Table; val Globe
}
```

### Cómo se ven (resumen; el detalle y los estados están en `SISTEMA.md`)

- **Botones**: PRIMARY brasa sólida con texto `onPrimary`; SECONDARY superficie + filete; GHOST sólo texto;
  DANGER superficie + filete con texto rojo. Radio 12, alto 40/48/56. Deshabilitado: superficie + texto gris.
- **Botón de ícono**: 44 × 44, radio 12, superficie + filete; `filled` (sobre foto) fondo + filete fuerte; `plain` sin caja.
- **Tarjeta**: superficie 1 + filete, radio 16. Dentro de otra superficie sube sola a superficie 2.
- **Campo**: etiqueta mono arriba, superficie + filete, radio 12, alto 48; foco = filete en tinta; error = filete rojo.
- **Segmentado / categorías**: riel superficie + filete (radio 12, 44) con pastilla en tinta sólida 4 px adentro (radio 8).
- **Chip**: superficie + filete, radio 8, alto 36; seleccionado = tinta sólida.
- **StatusPill / TagBadge**: fichas planas de radio 4 con etiqueta mono; el color del tono vive en el punto o en el texto.
  `Tone.PRIMARY` en `TagBadge` es tinta sólida (destacado), nunca brasa.
- **Stepper / agregar**: filete siempre neutro; la cantidad que ya está en tu pedido es una ficha sólida de brasa.
- **Foto**: sin filtros, filete interior de 1 px; marco plano oscuro mientras carga o si falla.
- **Diálogo / hoja / aviso**: superficie + filete, sin sombra ni desenfoque, sobre velo negro al 60 %.
- **Gráficas**: series en tinta, ejes y cifras en mono; la brasa sólo en el valor destacado.
- **Línea de tiempo**: hecho = tinta sólida, actual = brasa sólida (sin halo), pendiente = filete.
- **Armazón de panel**: todo sobre el fondo, separado por filetes; entrada activa = superficie 2 con texto en tinta.

Nivel de superficie: `AppCard`, `AppDialog`, `AppBottomSheet`, `BottomBar`, `OptionList` y la navegación inferior
proveen `LocalSurfaceLevel`; los controles leen `AppTheme.controlFill` y se pintan un nivel arriba de su
contenedor. Si construyes un contenedor propio en superficie 1, envuélvelo en
`CompositionLocalProvider(LocalSurfaceLevel provides 1) { … }`.

## Shell — `package app.shell`

```kotlin
val LocalRepo: ProvidableCompositionLocal<AppRepository>      // app.shared.data.AppRepository
interface Navigator { val route: String; fun navigate(route: String); fun back() }
val LocalNavigator: ProvidableCompositionLocal<Navigator>
@Composable fun LoginGate(   // muestra login (con cuentas demo de un toque) hasta tener sesión con un rol permitido
    allowedRoles: Set<Role>, title: String, subtitle: String, demoAccounts: List<Pair<String, String>> /* etiqueta → email */,
    onExit: () -> Unit, content: @Composable (UserSession) -> Unit,
)
```

Rutas (web: `#/ruta`; escritorio: argumento): `/` sitio de venta · `/r/{slug}` menú del comensal · `/cocina` · `/panel` · `/operador` · `/demo` índice de la demo.
Raíces de cada app (un archivo por dueño): `app.feature.diner.DinerApp(slug, onExit)`, `app.feature.kitchen.KitchenApp(onExit)`,
`app.feature.panel.PanelApp(onExit, onOpenDiner)`, `app.feature.operator.OperatorApp(onExit)`, `app.feature.site.SiteApp(onNavigate)`.
Cada raíz envuelve su contenido en `AppTheme(brand)`: el comensal, la cocina y el panel con el acento del tenant; operador y sitio con el acento por defecto.
