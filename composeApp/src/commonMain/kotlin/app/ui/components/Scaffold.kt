package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(val id: String, val label: String, val icon: ImageVector, val badge: Int? = null)

@Composable
fun AdminScaffold(
    title: String, navItems: List<NavItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, brand: @Composable () -> Unit = {}, userName: String? = null, userRole: String? = null,
    onLogout: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) { Row { brand(); actions() }; Box(Modifier.weight(1f)) { content() } }
}

@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier) { actions() }
}
