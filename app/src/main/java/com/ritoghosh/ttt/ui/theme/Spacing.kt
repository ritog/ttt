/**
 * Spacing — the spacing scale (4, 8, 12, 16, 24, 32 dp), corner radius and touch-target size (SPEC 15.2).
 *
 * Depends on: Compose ui-unit only.
 * Must not: grow new in-between values. If a layout needs one, pick the nearest step.
 */
package com.ritoghosh.ttt.ui.theme

import androidx.compose.ui.unit.dp

/** Immutable spacing tokens. Use these instead of raw dp values in screens. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radius for sheets and chips. */
val CornerRadius = 12.dp

/** Minimum touch target (accessibility, SPEC 15.2). */
val MinTouchTarget = 48.dp

/** Height of a Todo list row (SPEC 15.3). */
val ListRowHeight = 56.dp
