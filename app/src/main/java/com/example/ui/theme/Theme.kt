package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VynBlack,
    onPrimary = VynWhite,
    secondary = VynCameraBlue,
    background = VynWhite,
    surface = VynWhite,
    onBackground = VynBlack,
    onSurface = VynBlack,
    outline = VynBorder,
    surfaceVariant = VynButtonBg,
    onSurfaceVariant = VynBlack
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

