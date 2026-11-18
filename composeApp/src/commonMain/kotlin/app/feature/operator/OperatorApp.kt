package app.feature.operator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.ui.components.AppText
import app.ui.components.LabelText
import app.ui.components.NavItem
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
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
    val allItems = listOf(
        NavItem(OpSection.OVERVIEW.id, OpSection.OVERVIEW.label, AppIcons.Dashboard),
        NavItem(OpSection.TENANTS.id, OpSection.TENANTS.label, AppIcons.Store),
        NavItem(OpSection.PLANS.id, OpSection.PLANS.label, AppIcons.Tag),
        NavItem(OpSection.SUBSCRIPTIONS.id, if (compact) OpSection.SUBSCRIPTIONS.shortLabel else OpSection.SUBSCRIPTIONS.label, AppIcons.CreditCard, badge = pastDue.takeIf { it > 0 }),
        NavItem(OpSection.SUPPORT.id, OpSection.SUPPORT.label, AppIcons.Mail, badge = openTicketCount().takeIf { it > 0 }),
        NavItem(OpSection.CREDENTIALS.id, OpSection.CREDENTIALS.label, AppIcons.Lock),
    )
    // En teléfono la barra inferior lleva cinco destinos, a partes iguales y sin recortes; las credenciales
    // maestras, que se consultan poco, pasan a un botón de la barra superior.
    val navItems = if (compact) allItems.filter { it.id != OpSection.CREDENTIALS.id } else allItems

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = if (constraints.hasBoundedHeight) maxHeight else 720.dp
        CompositionLocalProvider(LocalOpViewportHeight provides viewport) {
            AdminScaffold(
                title = OpSection.of(sectionId).label,
                navItems = navItems,
                selectedId = sectionId,
                onSelect = { sectionId = it },
                userName = session.name,
                userRole = session.role.label(),
                onLogout = { repo.logout() },
                brand = {
                    // Marca blanca: la consola no lleva nombre ni logo de plataforma, sólo su función.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        Box(
                            Modifier.size(36.dp).flatSurface(AppTheme.colors.surfaceRaised, AppTheme.colors.lineStrong, AppTheme.shapes.sm),
                            contentAlignment = Alignment.Center,
                        ) { OpIcon(AppIcons.Dashboard, tint = AppTheme.colors.ink, size = Sizes.icon) }
                        Column {
                            AppText("Consola", style = AppTheme.type.title, maxLines = 1)
                            LabelText("Operador")
                        }
                    }
                },
                actions = {
                    if (compact) {
                        val onCredentials = sectionId == OpSection.CREDENTIALS.id
                        AppIconButton(
                            AppIcons.Lock,
                            onClick = { sectionId = OpSection.CREDENTIALS.id },
                            contentDescription = OpSection.CREDENTIALS.label,
                            tint = if (onCredentials) AppTheme.colors.ink else AppTheme.colors.inkMuted,
                            plain = !onCredentials,
                        )
                    }
                    AppIconButton(
                        AppIcons.Refresh,
                        onClick = reload,
                        contentDescription = "Actualizar datos",
                        tint = AppTheme.colors.inkMuted,
                        plain = true,
                    )
                },
            ) {
                AnimatedContent(
                    targetState = OpSection.of(sectionId),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(Motion.BASE, easing = Motion.standard)) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.exit))
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
