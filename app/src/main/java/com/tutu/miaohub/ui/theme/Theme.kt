package com.tutu.miaohub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MiaoLightScheme = lightColorScheme(
    primary = MiaoGold,
    onPrimary = Color.White,
    primaryContainer = MiaoGoldLight,
    onPrimaryContainer = MiaoOnSurfaceLight,

    secondary = MiaoCreamDark,
    onSecondary = MiaoOnSurfaceLight,
    secondaryContainer = MiaoCream,
    onSecondaryContainer = MiaoOnSurfaceLight,

    tertiary = MiaoOrange,
    onTertiary = Color.White,
    tertiaryContainer = MiaoOrangeLight,
    onTertiaryContainer = MiaoOnSurfaceLight,

    background = MiaoSurfaceLight,
    onBackground = MiaoOnSurfaceLight,

    surface = MiaoSurfaceLight,
    onSurface = MiaoOnSurfaceLight,
    surfaceVariant = MiaoSurfaceContainerLight,
    onSurfaceVariant = MiaoOnSurfaceVariantLight,

    error = MiaoRed,
    onError = Color.White,
    errorContainer = MiaoRedContainer,
    onErrorContainer = MiaoRed,

    outline = MiaoCreamDark,
    outlineVariant = MiaoCream,
    inverseSurface = MiaoSurfaceDark,
    inverseOnSurface = MiaoOnSurfaceDark,
)

private val MiaoDarkScheme = darkColorScheme(
    primary = MiaoGoldLight,
    onPrimary = MiaoOnSurfaceLight,
    primaryContainer = MiaoGoldDark,
    onPrimaryContainer = MiaoOnSurfaceDark,

    secondary = MiaoCreamDark,
    onSecondary = MiaoOnSurfaceLight,
    secondaryContainer = MiaoCardDark,
    onSecondaryContainer = MiaoOnSurfaceDark,

    tertiary = MiaoOrangeLight,
    onTertiary = MiaoOnSurfaceLight,
    tertiaryContainer = MiaoOrange,
    onTertiaryContainer = MiaoOnSurfaceDark,

    background = MiaoSurfaceDark,
    onBackground = MiaoOnSurfaceDark,

    surface = MiaoSurfaceDark,
    onSurface = MiaoOnSurfaceDark,
    surfaceVariant = MiaoSurfaceContainerDark,
    onSurfaceVariant = MiaoOnSurfaceVariantDark,

    error = MiaoRed,
    onError = Color.White,
    errorContainer = MiaoRedContainerDark,
    onErrorContainer = MiaoRed,

    outline = MiaoOnSurfaceVariantDark,
    outlineVariant = MiaoCardDark,
    inverseSurface = MiaoSurfaceLight,
    inverseOnSurface = MiaoOnSurfaceLight,
)

private val MiaoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MiaoHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MiaoDarkScheme else MiaoLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiaoTypography,
        shapes = MiaoShapes,
        content = content
    )
}
