package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable fun LoadingState(modifier: Modifier = Modifier, message: String? = null) { Box(modifier) }
@Composable fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) { Box(modifier) }
@Composable fun EmptyState(title: String, modifier: Modifier = Modifier, message: String = "", icon: ImageVector? = null, actionText: String? = null, onAction: (() -> Unit)? = null) { Box(modifier) }
