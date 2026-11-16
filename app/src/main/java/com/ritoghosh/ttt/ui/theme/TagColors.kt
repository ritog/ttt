/**
 * TagColors — a tag's tint, derived from its name (SPEC 15.2: users never pick colours).
 *
 * Depends on: Compose ui-graphics, MaterialTheme.
 * Must not: store colours anywhere; the same name always gives the same colour.
 *
 * A hash of the name picks the hue; saturation and lightness are fixed per theme so every
 * tint is soft and keeps text readable (dark text on light tints, light text on dark tints).
 */
package com.ritoghosh.ttt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val HUE_STEPS = 360
private const val LIGHT_SATURATION = 0.45f
private const val LIGHT_LIGHTNESS = 0.90f
private const val DARK_SATURATION = 0.22f // M8 dark pass: 0.30 / 0.24 looked heavy on large blocks
private const val DARK_LIGHTNESS = 0.20f

/** Hue (0..359) for a tag name. String.hashCode is fixed by the Java spec, so this is stable. */
fun tagHue(name: String): Float = Math.floorMod(name.hashCode(), HUE_STEPS).toFloat()

/** Background tint for a tag in the current theme. */
@Composable
fun tagColor(name: String): Color {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDarkTheme) {
        Color.hsl(tagHue(name), DARK_SATURATION, DARK_LIGHTNESS)
    } else {
        Color.hsl(tagHue(name), LIGHT_SATURATION, LIGHT_LIGHTNESS)
    }
}
