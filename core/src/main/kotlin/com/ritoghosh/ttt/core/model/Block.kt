/**
 * Block — one task planned for a time window on one local day (SPEC 5.1, 6.3).
 *
 * Depends on: core/time/TimeRange.kt.
 * Must not: validate itself; the rules are in core/plan/BlockRules.kt.
 * Tags are not stored here: a block shows its task's tags at display time.
 */
package com.ritoghosh.ttt.core.model

import com.ritoghosh.ttt.core.time.TimeRange

data class Block(
    val id: Long,
    val taskId: Long,
    val startMs: Long,
    /** Greater than startMs (checked by BlockRules before saving). */
    val endMs: Long,
) {
    /** The planned window. Only call on a block that passed BlockRules (end after start). */
    val range: TimeRange
        get() = TimeRange(startMs, endMs)
}
