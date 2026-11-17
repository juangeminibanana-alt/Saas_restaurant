# Sistema de diseño — contrato de API (Compose Multiplatform)

Fuente de verdad visual: la línea de diseño "Lienzo" (papel y tinta neutros + UN color de marca por tenant).
Este documento es el **contrato**: quien implementa el sistema de diseño respeta estas firmas exactas;
quien construye pantallas programa contra ellas. Si necesitas algo que no está aquí, créalo dentro de tu
propio paquete de feature (no edites archivos de otro dueño).

Reglas para TODO el código en `composeApp/src/commonMain`: Kotlin común puro (nada de `java.*`,
`String.format`, `System.*`, `synchronized`, `Dispatchers.IO`); hora con `app.shared.util.nowMillis()`;
dinero con `app.shared.util.formatMoney(cents)`; textos de UI en español de México; cero nombres o logos
de plataforma (marca blanca). Áreas táctiles ≥ 44dp, contraste AA.

## Tokens — `package app.ui.theme`

```kotlin
@Composable fun AppTheme(brand: BrandTheme? = null, content: @Composable () -> Unit)   // null = neutro de plataforma
object AppTheme {                      // accesores @Composable @ReadOnlyComposable
    val colors: AppColors
    val type: AppTypography
    val shapes: AppShapes
}
@Immutable data class AppColors(
    val canvas: Color, val surface: Color, val surfaceRaised: Color, val surfaceSunken: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color, val inkSubtle: Color,
    val primary: Color, val onPrimary: Color, val primarySoft: Color,
    val success: Color, val warning: Color, val danger: Color, val info: Color,
    val successSoft: Color, val warningSoft: Color, val dangerSoft: Color, val infoSoft: Color,
    val scrim: Color, val isDark: Boolean,
)
@Immutable data class AppTypography(   // TextStyle; display/h1/h2/h3 usan la fuente display del tenant
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val title: TextStyle, val body: TextStyle, val bodySm: TextStyle, val label: TextStyle,
)
@Immutable data class AppShapes(val xs: Shape, val sm: Shape, val md: Shape, val lg: Shape, val control: Shape, val pill: Shape)
object Space { val x1 = 4.dp; val x2 = 8.dp; val x3 = 12.dp; val x4 = 16.dp; val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp; val x10 = 40.dp; val x14 = 56.dp; val x18 = 72.dp }
object Motion {
    const val FAST = 120; const val BASE = 220; const val SLOW = 360; const val HERO = 520   // ms
    val standard: Easing            // cubic-bezier(.2,0,0,1)
    val exit: Easing                // cubic-bezier(.4,0,1,1)
    fun <T> snappy(): SpringSpec<T> // stiffness 500, damping .8
    fun <T> gentle(): SpringSpec<T> // stiffness 200, damping .85
    fun <T> bouncy(): SpringSpec<T> // stiffness 600, damping .5
}
object ThemeController { var darkOverride: Boolean?   /* estado observable; null = seguir tenant/sistema */; fun toggle(currentIsDark: Boolean) }
fun parseHexColor(hex: String?): Color?
enum class WindowSize { COMPACT, MEDIUM, EXPANDED }             // <600dp, <1024dp, resto
val LocalWindowSize: ProvidableCompositionLocal<WindowSize>     // lo provee App() en la raíz
```

Valores: claro canvas #FAF9F6, surface #FFFFFF, surfaceRaised #FFFFFF, surfaceSunken #F2F1EC, line #E7E5DF,
lineStrong #D3D0C7, ink #15140F, inkMuted #57554D, inkSubtle #8C897F. Oscuro canvas #0D0D0B, surface #161613,
surfaceRaised #1E1E1A, surfaceSunken #0A0A08, line #2A2924, lineStrong #3A3933, ink #F5F4EF, inkMuted #B5B2A8,
inkSubtle #7E7B72. Semánticos claro/oscuro: success #1E8E5A/#4CC38A, warning #B7791F/#E3A94A, danger #C4372B/#F0736A,
info #2B6CB0/#6BA6E8. primary = color del tenant (neutro: ink), onPrimary por contraste, primarySoft ≈ primary 10% sobre surface.
Tipos (sp/line-height): display 44/48, h1 32/38, h2 24/30, h3 20/26, title 17/24 semibold, body 15/22, bodySm 13/18, label 12/16 medium.
Radios xs/sm/md/lg por estilo: SHARP 2/4/6/8 · SOFT 6/10/14/18 · ROUND 10/16/22/28 · PILL 14/22/30/30 (control = cápsula).

## Componentes — `package app.ui.components`

```kotlin
@Composable fun AppText(text: String, modifier: Modifier = Modifier, style: TextStyle = AppTheme.type.body, color: Color = AppTheme.colors.ink, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null)

enum class ButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }
enum class ButtonSize { SM, MD, LG }
@Composable fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: ButtonVariant = ButtonVariant.PRIMARY, size: ButtonSize = ButtonSize.MD, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, fullWidth: Boolean = false)
@Composable fun AppIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = AppTheme.colors.ink, filled: Boolean = false, badge: Int? = null)
fun Modifier.pressable(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier   // click + escala al presionar, sin ripple

@Composable fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = Space.x4, elevated: Boolean = false, content: @Composable ColumnScope.() -> Unit)
@Composable fun AppDivider(modifier: Modifier = Modifier)
@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null)

@Composable fun AppTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String = "", leadingIcon: ImageVector? = null, singleLine: Boolean = true, isPassword: Boolean = false, error: String? = null, keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, enabled: Boolean = true)
@Composable fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
@Composable fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)
@Composable fun CategoryTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)   // scroll horizontal + indicador animado
@Composable fun AppChip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, icon: ImageVector? = null)

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, DANGER, INFO }
@Composable fun StatusPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL, dot: Boolean = true)
@Composable fun TagBadge(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL)

@Composable fun DishImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null, shape: Shape = AppTheme.shapes.md, contentScale: ContentScale = ContentScale.Crop)   // resuelve "res://demo/x.jpg" y http(s); shimmer al cargar; degradado si falla
@Composable fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = 99, compact: Boolean = false)
@Composable fun AddToCartButton(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier)   // "+" que se transforma en stepper
@Composable fun Skeleton(modifier: Modifier = Modifier, shape: Shape = AppTheme.shapes.sm)
fun Modifier.shimmer(): Modifier

@Composable fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 36.dp, imageUrl: String? = null)
@Composable fun BrandMark(name: String, logoUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp)   // logo del tenant o monograma en color de marca

@Composable fun AppDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, maxWidth: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit)
@Composable fun AppBottomSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, fullHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit)   // overlay animado; en EXPANDED se centra como diálogo. Llamar dentro de un Box que llene la pantalla.
object Toasts { fun show(message: String, tone: Tone = Tone.NEUTRAL) }
@Composable fun ToastHost(modifier: Modifier = Modifier)   // lo coloca App() una sola vez

@Composable fun LoadingState(modifier: Modifier = Modifier, message: String? = null)
@Composable fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null)
@Composable fun EmptyState(title: String, modifier: Modifier = Modifier, message: String = "", icon: ImageVector? = null, actionText: String? = null, onAction: (() -> Unit)? = null)

@Composable fun StatCard(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaPositive: Boolean = true, icon: ImageVector? = null, series: List<Double>? = null)
@Composable fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)
@Composable fun BarChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)
@Composable fun LineChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary)   // área suave animada

@Composable fun OrderStatusTimeline(order: Order, modifier: Modifier = Modifier)   // Pagado → En preparación → Listo → Entregado, paso actual pulsando
@Composable fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 168.dp)   // QR real, generado en Kotlin puro

data class NavItem(val id: String, val label: String, val icon: ImageVector, val badge: Int? = null)
@Composable fun AdminScaffold(   // barra lateral en EXPANDED, riel en MEDIUM, barra inferior en COMPACT
    title: String, navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, brand: @Composable () -> Unit = {}, userName: String? = null, userRole: String? = null,
    onLogout: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit,
)
@Composable fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {})

object AppIcons {   // ImageVector, trazo 1.75 redondeado, 24dp
    val Add; val Remove; val Close; val Back; val Forward; val Check; val Search; val Cart; val Bag; val Clock; val Flame; val Star; val Heart
    val Menu; val Settings; val Edit; val Delete; val Home; val Store; val Receipt; val Chart; val Users; val CreditCard; val Palette; val Link
    val Qr; val Bell; val Moon; val Sun; val Logout; val Phone; val Location; val Info; val Warning; val ChevronRight; val ChevronDown; val Bike
    val Chef; val Plug; val Dashboard; val Tag; val Eye; val Refresh; val Image; val Cash; val Bank; val Wallet; val Sparkle; val Filter
    val MoreVert; val ExternalLink; val Copy; val Lock; val Mail; val Person; val Table; val Globe
}
```

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
Cada raíz envuelve su contenido en `AppTheme(brand)` (el comensal y la cocina con el tema del tenant; operador y sitio en neutro).
