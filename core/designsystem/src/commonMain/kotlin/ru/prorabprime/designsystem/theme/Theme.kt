package ru.prorabprime.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A site-safety orange for actions on neutral surfaces: readable outdoors. Every role is set,
// so no Material baseline purple shows through in containers or surfaces.
private val LightColors = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E2E8),
    onSecondaryContainer = Color(0xFF0F1D23),
    tertiary = Color(0xFF5D5F3B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E4B5),
    onTertiaryContainer = Color(0xFF1A1C02),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEFE3DC),
    onSurfaceVariant = Color(0xFF52443D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE4E4E4),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF5A1C00),
    primaryContainer = Color(0xFF7F2B00),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFB9C6CC),
    onSecondary = Color(0xFF243238),
    secondaryContainer = Color(0xFF3B484E),
    onSecondaryContainer = Color(0xFFD5E2E8),
    tertiary = Color(0xFFC7C89B),
    onTertiary = Color(0xFF2F3111),
    tertiaryContainer = Color(0xFF454726),
    onTertiaryContainer = Color(0xFFE3E4B5),
    background = Color(0xFF141414),
    onBackground = Color(0xFFE4E2E1),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFE4E2E1),
    surfaceVariant = Color(0xFF52443D),
    onSurfaceVariant = Color(0xFFD7C2B9),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353535),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF52443D),
)

@Composable
fun ProrabTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
