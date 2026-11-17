package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.shared.model.MetricPoint
import app.ui.theme.AppTheme

@Composable fun StatCard(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaPositive: Boolean = true, icon: ImageVector? = null, series: List<Double>? = null) { Box(modifier) }
@Composable fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) { Box(modifier) }
@Composable fun BarChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) { Box(modifier) }
@Composable fun LineChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) { Box(modifier) }
