package dev.ve5li.lanpam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// Create a base text style with monospace font
private val baseMonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace
)

// Set of Material typography styles with monospace font
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(baseMonospaceStyle),
        displayMedium = displayMedium.merge(baseMonospaceStyle),
        displaySmall = displaySmall.merge(baseMonospaceStyle),
        headlineLarge = headlineLarge.merge(baseMonospaceStyle),
        headlineMedium = headlineMedium.merge(baseMonospaceStyle),
        headlineSmall = headlineSmall.merge(baseMonospaceStyle),
        titleLarge = titleLarge.merge(baseMonospaceStyle),
        titleMedium = titleMedium.merge(baseMonospaceStyle),
        titleSmall = titleSmall.merge(baseMonospaceStyle),
        bodyLarge = bodyLarge.merge(baseMonospaceStyle),
        bodyMedium = bodyMedium.merge(baseMonospaceStyle),
        bodySmall = bodySmall.merge(baseMonospaceStyle),
        labelLarge = labelLarge.merge(baseMonospaceStyle),
        labelMedium = labelMedium.merge(baseMonospaceStyle),
        labelSmall = labelSmall.merge(baseMonospaceStyle)
    )
}