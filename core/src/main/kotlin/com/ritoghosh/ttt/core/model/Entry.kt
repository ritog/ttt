/**
 * Entry — one finished or stopped work session, created only by the timer (SPEC 5.1, 6.4).
 *
 * Depends on: core/time/TimeRange.kt.
 * Must not: be created from user-typed times in v1 (no manual "add past session").
 */
package com.ritoghosh.ttt.core.model

import com.ritoghosh.ttt.core.time.TimeRange

data class Entry(
    val id: Long,
    val taskId: Long,
    /** Wall-clock start of the session. */
    val startedAtMs: Long,
    /** Wall-clock end of the session. */
    val endedAtMs: Long,
    /** Total pause time inside the session. */
    val pausedMs: Long,
    val note: String,
) {
    /** Time actually worked: wall-clock length minus pauses (SPEC 5.1). */
    val workedMs: Long
        get() = endedAtMs - startedAtMs - pausedMs

    /** The wall-clock window from start to end, pauses included. */
    val wallClockRange: TimeRange
        get() = TimeRange(startedAtMs, endedAtMs)
}
