package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 168.dp) { Box(modifier.size(size)) }
