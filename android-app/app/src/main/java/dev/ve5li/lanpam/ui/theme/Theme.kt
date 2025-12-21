package dev.ve5li.lanpam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SynthwaveColorScheme = darkColorScheme(
    primary = BackgroundDark,
    onPrimary = TextPrimary,

    surface = BackgroundDark,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,

    error = StatusError,
    onError = TextPrimary,

    tertiary = StatusSuccess,
    onTertiary = TextPrimary,

    secondary = StatusNeutral,
    onSecondary = TextPrimary
)

@Composable
fun LanPamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SynthwaveColorScheme,
        typography = Typography,
        content = content
    )
}
