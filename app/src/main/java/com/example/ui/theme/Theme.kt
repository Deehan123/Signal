package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TacticalPrimary,
    onPrimary = Color.Black,
    secondary = TacticalSecondary,
    onSecondary = Color.Black,
    tertiary = TacticalTertiary,
    onTertiary = Color.Black,
    background = TacticalDarkBg,
    onBackground = TacticalPrimary,
    surface = TacticalSurface,
    onSurface = TacticalPrimary,
    surfaceVariant = TacticalBorder,
    onSurfaceVariant = TacticalMutedText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
