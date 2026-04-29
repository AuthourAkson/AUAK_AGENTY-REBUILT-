package com.auak.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AuakClawLightScheme = lightColorScheme(
    primary = AuakClawGold,
    onPrimary = Color.White,
    primaryContainer = AuakClawGoldLight,
    onPrimaryContainer = AuakClawOnSurfaceLight,

    secondary = AuakClawCreamDark,
    onSecondary = AuakClawOnSurfaceLight,
    secondaryContainer = AuakClawCream,
    onSecondaryContainer = AuakClawOnSurfaceLight,

    tertiary = AuakClawOrange,
    onTertiary = Color.White,
    tertiaryContainer = AuakClawOrangeLight,
    onTertiaryContainer = AuakClawOnSurfaceLight,

    background = AuakClawSurfaceLight,
    onBackground = AuakClawOnSurfaceLight,

    surface = AuakClawSurfaceLight,
    onSurface = AuakClawOnSurfaceLight,
    surfaceVariant = AuakClawSurfaceContainerLight,
    onSurfaceVariant = AuakClawOnSurfaceVariantLight,

    error = AuakClawRed,
    onError = Color.White,
    errorContainer = AuakClawRedContainer,
    onErrorContainer = AuakClawRed,

    outline = AuakClawCreamDark,
    outlineVariant = AuakClawCream,
    inverseSurface = AuakClawSurfaceDark,
    inverseOnSurface = AuakClawOnSurfaceDark,
)

private val AuakClawDarkScheme = darkColorScheme(
    primary = AuakClawGoldLight,
    onPrimary = AuakClawOnSurfaceLight,
    primaryContainer = AuakClawGoldDark,
    onPrimaryContainer = AuakClawOnSurfaceDark,

    secondary = AuakClawCreamDark,
    onSecondary = AuakClawOnSurfaceLight,
    secondaryContainer = AuakClawCardDark,
    onSecondaryContainer = AuakClawOnSurfaceDark,

    tertiary = AuakClawOrangeLight,
    onTertiary = AuakClawOnSurfaceLight,
    tertiaryContainer = AuakClawOrange,
    onTertiaryContainer = AuakClawOnSurfaceDark,

    background = AuakClawSurfaceDark,
    onBackground = AuakClawOnSurfaceDark,

    surface = AuakClawSurfaceDark,
    onSurface = AuakClawOnSurfaceDark,
    surfaceVariant = AuakClawSurfaceContainerDark,
    onSurfaceVariant = AuakClawOnSurfaceVariantDark,

    error = AuakClawRed,
    onError = Color.White,
    errorContainer = AuakClawRedContainerDark,
    onErrorContainer = AuakClawRed,

    outline = AuakClawOnSurfaceVariantDark,
    outlineVariant = AuakClawCardDark,
    inverseSurface = AuakClawSurfaceLight,
    inverseOnSurface = AuakClawOnSurfaceLight,
)

private val AuakClawShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun auak agentyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuakClawDarkScheme else AuakClawLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuakClawTypography,
        shapes = AuakClawShapes,
        content = content
    )
}
