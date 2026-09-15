package com.example.snapstoneprinter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SlateBluePrimaryDark,
    onPrimary = OnSlateBluePrimaryDark,
    primaryContainer = SlateBluePrimaryContainerDark,
    onPrimaryContainer = OnSlateBluePrimaryContainerDark,
    secondary = SlateGraySecondaryDark,
    onSecondary = OnSlateGraySecondaryDark,
    secondaryContainer = SlateGraySecondaryContainerDark,
    onSecondaryContainer = OnSlateGraySecondaryContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = OnSageTertiaryDark,
    tertiaryContainer = SageTertiaryContainerDark,
    onTertiaryContainer = OnSageTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = SlateBluePrimaryLight,
    onPrimary = OnSlateBluePrimaryLight,
    primaryContainer = SlateBluePrimaryContainerLight,
    onPrimaryContainer = OnSlateBluePrimaryContainerLight,
    secondary = SlateGraySecondaryLight,
    onSecondary = OnSlateGraySecondaryLight,
    secondaryContainer = SlateGraySecondaryContainerLight,
    onSecondaryContainer = OnSlateGraySecondaryContainerLight,
    tertiary = SageTertiaryLight,
    onTertiary = OnSageTertiaryLight,
    tertiaryContainer = SageTertiaryContainerLight,
    onTertiaryContainer = OnSageTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

@Composable
fun SnapstonePrinterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // A deliberate, fixed neutral palette (see Color.kt), not the device wallpaper.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}