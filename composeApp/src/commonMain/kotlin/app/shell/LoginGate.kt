package app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.shared.model.Role
import app.shared.model.UserSession

@Composable
fun LoginGate(
    allowedRoles: Set<Role>, title: String, subtitle: String, demoAccounts: List<Pair<String, String>>,
    onExit: () -> Unit, content: @Composable (UserSession) -> Unit,
) {
    val repo = LocalRepo.current
    val session by repo.session.collectAsState()
    val s = session
    if (s != null && s.role in allowedRoles) content(s)
}
