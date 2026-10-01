package ru.prorabprime.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Dark pine and graphite: heavy, quiet colors for a working tool. Every role is set, so no
// Material baseline purple shows through in containers or surfaces.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4A38),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4DA),
    onPrimaryContainer = Color(0xFF0A2419),
    secondary = Color(0xFF46524C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE5E1),
    onSecondaryContainer = Color(0xFF131C17),
    tertiary = Color(0xFF5E5A45),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E2CB),
    onTertiaryContainer = Color(0xFF1B1807),
    background = Color(0xFFF2F4F2),
    onBackground = Color(0xFF161A18),
    surface = Color(0xFFF2F4F2),
    onSurface = Color(0xFF161A18),
    surfaceVariant = Color(0xFFDCE3DE),
    onSurfaceVariant = Color(0xFF3E4943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F7),
    surfaceContainer = Color(0xFFECEFEC),
    surfaceContainerHigh = Color(0xFFE5E9E6),
    surfaceContainerHighest = Color(0xFFDFE4E0),
    outline = Color(0xFF6C7872),
    outlineVariant = Color(0xFFBCC7C0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC5A9),
    onPrimary = Color(0xFF0B3323),
    primaryContainer = Color(0xFF245A43),
    onPrimaryContainer = Color(0xFFCFE9DA),
    secondary = Color(0xFFB5C2BB),
    onSecondary = Color(0xFF1F2B25),
    secondaryContainer = Color(0xFF36423C),
    onSecondaryContainer = Color(0xFFD5E3DB),
    tertiary = Color(0xFFCCC7A5),
    onTertiary = Color(0xFF312E17),
    tertiaryContainer = Color(0xFF484429),
    onTertiaryContainer = Color(0xFFE8E3C2),
    background = Color(0xFF0E1411),
    onBackground = Color(0xFFE1E6E3),
    surface = Color(0xFF0E1411),
    onSurface = Color(0xFFE1E6E3),
    surfaceVariant = Color(0xFF3C4742),
    onSurfaceVariant = Color(0xFFBCC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF151C18),
    surfaceContainer = Color(0xFF19211D),
    surfaceContainerHigh = Color(0xFF232B27),
    surfaceContainerHighest = Color(0xFF2D3631),
    outline = Color(0xFF86938B),
    outlineVariant = Color(0xFF3C4742),
)

/** One corner radius for everything that has corners, so a card, a button and a field match. */
object Corners {
    val m = 8.dp
    val l = 12.dp
}

private val BaseTypography = Typography()

/** Headings and buttons a weight heavier than the default, so a screen's structure is easy to see. */
private val ProrabTypography = Typography(
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val ProrabShapes = Shapes(
    extraSmall = RoundedCornerShape(Corners.m),
    small = RoundedCornerShape(Corners.m),
    medium = RoundedCornerShape(Corners.m),
    large = RoundedCornerShape(Corners.l),
    extraLarge = RoundedCornerShape(Corners.l),
)

@Composable
fun ProrabTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ProrabShapes,
        typography = ProrabTypography,
        content = content,
    )
}
