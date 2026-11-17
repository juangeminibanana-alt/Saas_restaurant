package app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.theme.AppTheme

private fun avatarInitials(name: String, max: Int): String =
    name.trim().split(' ', '-', '·', '.').filter { it.isNotBlank() }.take(max)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "·" }

/** Avatar circular con iniciales (o foto). */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 36.dp, imageUrl: String? = null) {
    val colors = AppTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.surfaceSunken).border(1.dp, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            DishImage(imageUrl, Modifier.fillMaxSize(), contentDescription = name, shape = CircleShape)
        } else {
            AppText(
                avatarInitials(name, 2),
                style = AppTheme.type.label.copy(fontSize = (size.value * 0.36f).sp, lineHeight = (size.value * 0.44f).sp, fontWeight = FontWeight.SemiBold),
                color = colors.inkMuted,
            )
        }
    }
}

/** Logo del restaurante, o monograma en su color de marca con la fuente display. */
@Composable
fun BrandMark(name: String, logoUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.sm
    Box(modifier.size(size).clip(shape).background(colors.primary), contentAlignment = Alignment.Center) {
        if (!logoUrl.isNullOrBlank()) {
            DishImage(logoUrl, Modifier.fillMaxSize(), contentDescription = name, shape = shape)
        } else {
            AppText(
                avatarInitials(name, 1),
                style = AppTheme.type.h2.copy(fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.56f).sp),
                color = colors.onPrimary,
            )
        }
    }
}
