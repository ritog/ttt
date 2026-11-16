/**
 * Type — the small type scale (12 / 14 / 16 / 20 / 28 / 56) mapped onto Material 3 roles (SPEC 15.2).
 *
 * Depends on: Compose ui-text and material3 Typography.
 * Must not: add sizes outside the scale. Screens use MaterialTheme.typography roles
 * or TimerTextStyle, never raw font sizes.
 *
 * The font is the system default for now; bundling Inter is a recorded decision
 * (docs/DECISIONS.md). To switch, put the font in res/font and change AppFontFamily only.
 */
package com.ritoghosh.ttt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The one typeface used everywhere. */
val AppFontFamily: FontFamily = FontFamily.Default

/** OpenType feature for tabular (fixed-width) digits, so times don't jiggle as they change. */
const val TABULAR_NUMERALS = "tnum"

private fun style(sizeSp: Int, lineHeightSp: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = weight,
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp.sp,
    )

/** The big countdown on the Track screen (56 sp, tabular digits). */
val TimerTextStyle: TextStyle =
    style(56, 64, FontWeight.Light).copy(fontFeatureSettings = TABULAR_NUMERALS)

/** Times in lists and the timeline (14 sp, tabular digits). */
val TimeLabelTextStyle: TextStyle =
    style(14, 20).copy(fontFeatureSettings = TABULAR_NUMERALS)

val AppTypography = Typography(
    headlineMedium = style(28, 36, FontWeight.Medium),
    titleLarge = style(20, 28, FontWeight.Medium),
    titleMedium = style(16, 24, FontWeight.Medium),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 16),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium),
)
