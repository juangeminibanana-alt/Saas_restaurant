package app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.feature.diner.DinerApp
import app.feature.kitchen.KitchenApp
import app.feature.operator.OperatorApp
import app.feature.panel.PanelApp
import app.feature.site.SiteApp
import app.platform.pushRouteToPlatform
import app.ui.theme.Motion

/** Rutas de primer nivel. `/r/{slug}` conserva todo lo que sigue a "/r/" (incluido "?mesa=4"). */
object Routes {
    const val SITE = "/"
    const val KITCHEN = "/cocina"
    const val PANEL = "/panel"
    const val OPERATOR = "/operador"
    const val DEMO = "/demo"
    const val DINER_PREFIX = "/r/"

    fun diner(slug: String, table: String? = null): String =
        DINER_PREFIX + slug + (if (table.isNullOrBlank()) "" else "?mesa=$table")

    /** Normaliza cualquier entrada ("#/panel", "panel/", "/x?y") a una ruta conocida; lo desconocido va a "/". */
    fun normalize(raw: String?): String {
        var r = (raw ?: "").trim()
        while (r.startsWith("#")) r = r.substring(1)
        if (!r.startsWith("/")) r = "/$r"
        if (r.startsWith(DINER_PREFIX)) {
            val rest = r.substring(DINER_PREFIX.length).trim('/')
            val slug = rest.substringBefore('?').substringBefore('/')
            return if (slug.isBlank()) SITE else DINER_PREFIX + rest
        }
        val path = r.substringBefore('?').substringBefore('#').trimEnd('/').lowercase()
        return when (path) {
            KITCHEN, PANEL, OPERATOR, DEMO -> path
            else -> SITE
        }
    }

    /** Lo que recibe DinerApp como `slug` (p. ej. "brasa-demo?mesa=4"); null si la ruta no es del comensal. */
    fun dinerArgument(route: String): String? =
        if (route.startsWith(DINER_PREFIX)) route.substring(DINER_PREFIX.length) else null
}

/** Navegador con pila de regreso; refleja cada cambio en la plataforma (hash en web). */
class AppNavigator(initialRoute: String, standalone: Boolean = false) : Navigator {
    /** En una app independiente no existe el índice de la demo: "salir" regresa al inicio de esa app. */
    private val homeRoute: String? = if (standalone) Routes.normalize(initialRoute) else null

    private val backStack = ArrayList<String>()

    override var route: String by mutableStateOf(Routes.normalize(initialRoute))
        private set

    override fun navigate(route: String) {
        val normalized = Routes.normalize(route)
        val target = if (homeRoute != null && normalized == Routes.DEMO) homeRoute else normalized
        if (target == this.route) return
        backStack.add(this.route)
        if (backStack.size > 50) backStack.removeAt(0)
        this.route = target
        pushRouteToPlatform(target)
    }

    override fun back() {
        val previous = if (backStack.isEmpty()) null else backStack.removeAt(backStack.size - 1)
        val target = previous ?: if (route == Routes.SITE) return else Routes.SITE
        route = target
        pushRouteToPlatform(target)
    }

    val canGoBack: Boolean get() = backStack.isNotEmpty()
}

/** Transición entre apps: desvanecido con un deslizamiento vertical leve. */
@Composable
fun Router(navigator: AppNavigator, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = navigator.route,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(Motion.SLOW, delayMillis = 60, easing = Motion.standard)) +
                slideInVertically(tween(Motion.SLOW, easing = Motion.standard)) { it / 32 }) togetherWith
                (fadeOut(tween(Motion.BASE, easing = Motion.exit)) +
                    slideOutVertically(tween(Motion.BASE, easing = Motion.exit)) { -it / 48 })
        },
        label = "router",
    ) { route ->
        Box(Modifier.fillMaxSize()) {
            val dinerArg = Routes.dinerArgument(route)
            when {
                dinerArg != null -> DinerApp(slug = dinerArg, onExit = { navigator.navigate(Routes.DEMO) })
                route == Routes.KITCHEN -> KitchenApp(onExit = { navigator.navigate(Routes.DEMO) })
                route == Routes.PANEL -> PanelApp(
                    onExit = { navigator.navigate(Routes.DEMO) },
                    onOpenDiner = { navigator.navigate(Routes.DINER_PREFIX + it) },
                )
                route == Routes.OPERATOR -> OperatorApp(onExit = { navigator.navigate(Routes.DEMO) })
                route == Routes.DEMO -> DemoHub(onOpen = navigator::navigate)
                else -> SiteApp(onNavigate = navigator::navigate)
            }
        }
    }
}
