package ru.prorabprime.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A site-safety orange for actions, on neutral surfaces: readable outdoors, in gloves.
private val Orange = Color(0xFFE65100)
private val OrangeLight = Color(0xFFFFB68C)
private val Slate = Color(0xFF455A64)
private val SlateLight = Color(0xFFB0BEC5)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Slate,
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Color(0xFF4E1A00),
    secondary = SlateLight,
    onSecondary = Color(0xFF1C313A),
)

@Composable
fun ProrabTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
