/**
 * Theme — wraps the app in Material 3, restyled with our colour, type and shape tokens.
 *
 * Depends on: Color.kt, Type.kt, Spacing.kt, Compose material3.
 * Must not: use dynamic (wallpaper) colour. The app should look the same on every phone.
 *
 * MainActivity passes darkTheme from the Settings choice (system / light / dark).
 */
package com.ritoghosh.ttt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors: ColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccentSoft,
    onPrimaryContainer = LightText,
    secondaryContainer = LightAccentSoft,
    onSecondaryContainer = LightText,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurfaceRaised,
    surfaceContainerLow = LightSurfaceRaised,
    surfaceContainerHigh = LightSurfaceRaised,
    outline = LightHairline,
    outlineVariant = LightHairline,
    error = LightError,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkText,
    secondaryContainer = DarkAccentSoft,
    onSecondaryContainer = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurfaceRaised,
    surfaceContainerLow = DarkSurfaceRaised,
    surfaceContainerHigh = DarkSurfaceRaised,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    error = DarkError,
)

// One radius for everything rounded (sheets, chips, cards).
private val AppShapes = Shapes(
    small = RoundedCornerShape(CornerRadius),
    medium = RoundedCornerShape(CornerRadius),
    large = RoundedCornerShape(CornerRadius),
)

/** Applies the ttt look to [content]. */
@Composable
fun TttTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
