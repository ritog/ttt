/**
 * Color — the app's named colour tokens for light and dark themes (SPEC 15.2).
 *
 * Depends on: Compose ui-graphics only.
 * Must not: be referenced directly from screens. Screens read colours from
 * MaterialTheme.colorScheme, which Theme.kt fills from these tokens.
 *
 * One accent colour only, used for the running timer, primary buttons and the current-time line.
 * Text/background pairs are chosen to meet WCAG AA contrast. Tune by eye, keep contrast.
 */
package com.ritoghosh.ttt.ui.theme

import androidx.compose.ui.graphics.Color

// Light palette: warm neutral background, near-black text.
val LightBackground = Color(0xFFFAFAF8)
val LightSurface = Color(0xFFFAFAF8)
val LightSurfaceRaised = Color(0xFFF2F1EE) // sheets, chips, drawer
val LightText = Color(0xFF1A1A1A)
val LightTextSecondary = Color(0xFF5F5E5A) // quiet secondary lines (contrast ~6:1)
val LightHairline = Color(0xFFE4E2DD) // dividers
val LightAccent = Color(0xFF0F5E5C) // deep teal
val LightOnAccent = Color(0xFFFFFFFF)
val LightAccentSoft = Color(0xFFD7E8E6) // selected tab indicator
val LightError = Color(0xFFB3261E)

// Dark palette: near-black background, soft off-white text.
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceRaised = Color(0xFF1E1E1E)
val DarkText = Color(0xFFECECEC)
val DarkTextSecondary = Color(0xFFA8A7A3)
val DarkHairline = Color(0xFF2C2C2A)
val DarkAccent = Color(0xFF6CBFB8) // lighter teal so it reads on dark
val DarkOnAccent = Color(0xFF00201E)
val DarkAccentSoft = Color(0xFF1F3A38)
val DarkError = Color(0xFFF2B8B5)
