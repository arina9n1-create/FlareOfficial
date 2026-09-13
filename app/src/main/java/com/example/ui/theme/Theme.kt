package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FlareBlack,
    onPrimary = FlareWhite,
    secondary = FlareCameraBlue,
    background = FlareWhite,
    surface = FlareWhite,
    onBackground = FlareBlack,
    onSurface = FlareBlack,
    outline = FlareBorder,
    surfaceVariant = FlareButtonBg,
    onSurfaceVariant = FlareBlack
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

