/**
 * TimeRange — a half-open window of time [startMs, endMs) in epoch milliseconds.
 *
 * Depends on: nothing.
 * Must not: know about local days or zones (see DayRange.kt for that).
 *
 * Half-open means a range ending at 14:00 and one starting at 14:00 touch but do not overlap,
 * which is exactly what back-to-back blocks need.
 */
package com.ritoghosh.ttt.core.time

data class TimeRange(val startMs: Long, val endMs: Long) {

    init {
        require(endMs >= startMs) { "TimeRange end ($endMs) is before start ($startMs)" }
    }

    val durationMs: Long
        get() = endMs - startMs

    /** True if the two ranges share at least one millisecond. Touching ends do not count. */
    fun overlaps(other: TimeRange): Boolean = startMs < other.endMs && other.startMs < endMs

    /** Milliseconds the two ranges share; 0 if they do not overlap. */
    fun overlapMs(other: TimeRange): Long {
        val sharedStart = maxOf(startMs, other.startMs)
        val sharedEnd = minOf(endMs, other.endMs)
        return maxOf(0L, sharedEnd - sharedStart)
    }

    /** True if [instantMs] lies inside this range (start included, end excluded). */
    fun contains(instantMs: Long): Boolean = instantMs >= startMs && instantMs < endMs
}
