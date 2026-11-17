package app.feature.operator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shared.model.Role
import app.shared.model.TenantStatus
import app.shared.model.UserSession
import app.shared.util.label
import app.shell.LocalRepo
import app.shell.LoginGate
import app.ui.components.AdminScaffold
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.NavItem
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.ThemeController
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch

/** Consola del operador (super admin del SaaS). Marca blanca: tema neutro, sin nombre ni logo de plataforma. */
@Composable
fun OperatorApp(onExit: () -> Unit) {
    AppTheme {
        LoginGate(
            allowedRoles = setOf(Role.OPERATOR),
            title = "Consola del operador",
            subtitle = "Administra restaurantes, planes y suscripciones",
            demoAccounts = listOf("Operador" to "operador@demo.mx"),
            onExit = onExit,
        ) { session ->
            OperatorConsole(session)
        }
    }
}

@Composable
private fun OperatorConsole(session: UserSession) {
    val repo = LocalRepo.current
    val store = remember(repo) { OperatorStore(repo) }
    val scope = rememberCoroutineScope()
    var sectionId by remember { mutableStateOf(OpSection.OVERVIEW.id) }
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    LaunchedEffect(store) { store.load() }

    val reload: () -> Unit = { scope.launch { store.load() } }
    val pastDue = store.tenants.count { it.status == TenantStatus.PAST_DUE }
    val navItems = listOf(
        NavItem(OpSection.OVERVIEW.id, OpSection.OVERVIEW.label, AppIcons.Dashboard),
        NavItem(OpSection.TENANTS.id, OpSection.TENANTS.label, AppIcons.Store),
        NavItem(OpSection.PLANS.id, OpSection.PLANS.label, AppIcons.Tag),
        NavItem(OpSection.SUBSCRIPTIONS.id, OpSection.SUBSCRIPTIONS.label, AppIcons.CreditCard, badge = pastDue.takeIf { it > 0 }),
        NavItem(OpSection.SUPPORT.id, OpSection.SUPPORT.label, AppIcons.Mail, badge = openTicketCount().takeIf { it > 0 }),
        NavItem(
            OpSection.CREDENTIALS.id,
            if (compact) OpSection.CREDENTIALS.shortLabel else OpSection.CREDENTIALS.label,
            AppIcons.Lock,
        ),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = if (constraints.hasBoundedHeight) maxHeight else 720.dp
        CompositionLocalProvider(LocalOpViewportHeight provides viewport) {
            AdminScaffold(
                title = "Consola",
                navItems = navItems,
                selectedId = sectionId,
                onSelect = { sectionId = it },
                userName = session.name,
                userRole = session.role.label(),
                onLogout = { repo.logout() },
                actions = {
                    AppIconButton(
                        AppIcons.Refresh,
                        onClick = reload,
                        contentDescription = "Actualizar datos",
                        tint = AppTheme.colors.inkMuted,
                    )
                    val dark = AppTheme.colors.isDark
                    AppIconButton(
                        if (dark) AppIcons.Sun else AppIcons.Moon,
                        onClick = { ThemeController.toggle(dark) },
                        contentDescription = if (dark) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                        tint = AppTheme.colors.inkMuted,
                    )
                },
            ) {
                AnimatedContent(
                    targetState = OpSection.of(sectionId),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(Motion.BASE, easing = Motion.standard)) +
                            slideInVertically(tween(Motion.SLOW, easing = Motion.standard)) { it / 28 }) togetherWith
                            fadeOut(tween(Motion.FAST, easing = Motion.exit))
                    },
                    label = "operator-section",
                ) { section ->
                    when (section) {
                        OpSection.OVERVIEW -> OverviewSection(store, onReload = reload, onOpenTenants = { sectionId = OpSection.TENANTS.id })
                        OpSection.TENANTS -> TenantsSection(store, onReload = reload)
                        OpSection.PLANS -> PlansSection(store, onReload = reload)
                        OpSection.SUBSCRIPTIONS -> SubscriptionsSection(store, onReload = reload)
                        OpSection.SUPPORT -> SupportSection()
                        OpSection.CREDENTIALS -> CredentialsSection(store)
                    }
                }
            }
        }
    }
}
