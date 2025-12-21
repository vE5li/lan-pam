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
        headlineMedium = headlineMedium.merge(baseMonospaceStyle),
        titleMedium = titleMedium.merge(baseMonospaceStyle),
        bodyMedium = bodyMedium.merge(baseMonospaceStyle),
        bodySmall = bodySmall.merge(baseMonospaceStyle),
        labelSmall = labelSmall.merge(baseMonospaceStyle)
    )
}