package ltd.softvinax.qrkit.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Background = Color(0xFF0B1020)
internal val SurfaceColor = Color(0xFF151B2E)
internal val Accent = Color(0xFF4ADE80)
internal val MutedText = Color(0xFF9AA3B8)
internal val CodeInk = Color(0xFF0B1020)
internal val ErrorText = Color(0xFFFCA5A5)

@Composable
fun QrKitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF04140A),
            background = Background,
            onBackground = Color(0xFFF5F7FB),
            surface = SurfaceColor,
            onSurface = Color(0xFFE7EAF3),
            surfaceVariant = Color(0xFF1E2740),
            onSurfaceVariant = Color(0xFFB9C0D4),
        ),
        content = content,
    )
}
