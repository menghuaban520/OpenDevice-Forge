package dev.opendevice.node.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

internal val ForgeCyan = Color(0xFF64E6FF)
internal val ForgeGreen = Color(0xFF70F0B1)
internal val ForgeAmber = Color(0xFFFFD166)
internal val ForgeGraphite = Color(0xFF070B12)

private val ForgeColorScheme = darkColorScheme(
    primary = ForgeCyan,
    onPrimary = Color(0xFF002028),
    primaryContainer = Color(0xFF123A46),
    onPrimaryContainer = Color(0xFFB4F2FF),
    secondary = Color(0xFFAFC6FF),
    onSecondary = Color(0xFF102A55),
    secondaryContainer = Color(0xFF223659),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = ForgeGreen,
    onTertiary = Color(0xFF003823),
    tertiaryContainer = Color(0xFF115238),
    onTertiaryContainer = Color(0xFFA7F6CE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4B1117),
    onErrorContainer = Color(0xFFFFDAD6),
    background = ForgeGraphite,
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF0A101A),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF1A2635),
    onSurfaceVariant = Color(0xFFB7C3D3),
    outline = Color(0xFF516072),
    outlineVariant = Color(0xFF293748),
    inverseSurface = Color(0xFFEAF2FF),
    inverseOnSurface = Color(0xFF17202B),
    inversePrimary = Color(0xFF00677A),
    surfaceDim = ForgeGraphite,
    surfaceBright = Color(0xFF243244),
    surfaceContainerLowest = Color(0xFF05080D),
    surfaceContainerLow = Color(0xFF0D1520),
    surfaceContainer = Color(0xFF111B28),
    surfaceContainerHigh = Color(0xFF172332),
    surfaceContainerHighest = Color(0xFF1D2B3B),
)

private val ForgeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun OpenDeviceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeColorScheme,
        typography = ForgeTypography,
        shapes = ForgeShapes,
        content = content,
    )
}
