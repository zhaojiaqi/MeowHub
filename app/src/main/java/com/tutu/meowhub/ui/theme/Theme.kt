package com.tutu.meowhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MeowLightScheme = lightColorScheme(
    primary = MeowGold,
    onPrimary = Color.White,
    primaryContainer = MeowGoldLight,
    onPrimaryContainer = MeowOnSurfaceLight,

    secondary = MeowCreamDark,
    onSecondary = MeowOnSurfaceLight,
    secondaryContainer = MeowCream,
    onSecondaryContainer = MeowOnSurfaceLight,

    tertiary = MeowOrange,
    onTertiary = Color.White,
    tertiaryContainer = MeowOrangeLight,
    onTertiaryContainer = MeowOnSurfaceLight,

    background = MeowSurfaceLight,
    onBackground = MeowOnSurfaceLight,

    surface = MeowSurfaceLight,
    onSurface = MeowOnSurfaceLight,
    surfaceVariant = MeowSurfaceContainerLight,
    onSurfaceVariant = MeowOnSurfaceVariantLight,

    error = MeowRed,
    onError = Color.White,
    errorContainer = MeowRedContainer,
    onErrorContainer = MeowRed,

    outline = MeowCreamDark,
    outlineVariant = MeowCream,
    inverseSurface = MeowSurfaceDark,
    inverseOnSurface = MeowOnSurfaceDark,
)

private val MeowDarkScheme = darkColorScheme(
    primary = MeowGoldLight,
    onPrimary = MeowOnSurfaceLight,
    primaryContainer = MeowGoldDark,
    onPrimaryContainer = MeowOnSurfaceDark,

    secondary = MeowCreamDark,
    onSecondary = MeowOnSurfaceLight,
    secondaryContainer = MeowCardDark,
    onSecondaryContainer = MeowOnSurfaceDark,

    tertiary = MeowOrangeLight,
    onTertiary = MeowOnSurfaceLight,
    tertiaryContainer = MeowOrange,
    onTertiaryContainer = MeowOnSurfaceDark,

    background = MeowSurfaceDark,
    onBackground = MeowOnSurfaceDark,

    surface = MeowSurfaceDark,
    onSurface = MeowOnSurfaceDark,
    surfaceVariant = MeowSurfaceContainerDark,
    onSurfaceVariant = MeowOnSurfaceVariantDark,

    error = MeowRed,
    onError = Color.White,
    errorContainer = MeowRedContainerDark,
    onErrorContainer = MeowRed,

    outline = MeowOnSurfaceVariantDark,
    outlineVariant = MeowCardDark,
    inverseSurface = MeowSurfaceLight,
    inverseOnSurface = MeowOnSurfaceLight,
)

private val MeowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MeowHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MeowDarkScheme else MeowLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeowTypography,
        shapes = MeowShapes,
        content = content
    )
}
