package pt.standupportugal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ComedyDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB703),
    onPrimary = Color(0xFF1A1128),
    secondary = Color(0xFFFB8500),
    onSecondary = Color(0xFF1A1128),
    background = Color(0xFF120D1D),
    onBackground = Color(0xFFF5F3F7),
    surface = Color(0xFF1E172E),
    onSurface = Color(0xFFF5F3F7),
    surfaceVariant = Color(0xFF2B2240),
    onSurfaceVariant = Color(0xFFC9C2D6),
    outline = Color(0xFF3E3356)
)

@Composable
fun StandUpPortugalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ComedyDarkScheme,
        content = content
    )
}
