/**
 * PomodoroSettings — the lengths the timer uses for work, breaks, and the long-break cadence (SPEC 9.5).
 *
 * Depends on: core/Constants.kt.
 * Must not: read storage; the settings repository (M5) builds this from the setting table.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.DEFAULT_LONG_BREAK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_SHORT_BREAK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_WORK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_WORK_PHASES_BEFORE_LONG_BREAK

private const val MS_PER_MINUTE = 60_000L

data class PomodoroSettings(
    val workMs: Long,
    val shortBreakMs: Long,
    val longBreakMs: Long,
    /** At least 1; every Nth finished work phase is followed by a long break. */
    val workPhasesBeforeLongBreak: Int,
) {
    init {
        require(workMs > 0 && shortBreakMs > 0 && longBreakMs > 0) { "Phase lengths must be positive" }
        require(workPhasesBeforeLongBreak >= 1) { "workPhasesBeforeLongBreak must be at least 1" }
    }

    companion object {
        /** 25 / 5 / 15 minutes, long break after every 4th work phase. */
        val DEFAULT = fromMinutes(
            DEFAULT_WORK_MINUTES,
            DEFAULT_SHORT_BREAK_MINUTES,
            DEFAULT_LONG_BREAK_MINUTES,
            DEFAULT_WORK_PHASES_BEFORE_LONG_BREAK,
        )

        fun fromMinutes(work: Int, shortBreak: Int, longBreak: Int, workPhasesBeforeLongBreak: Int) = PomodoroSettings(
            workMs = work * MS_PER_MINUTE,
            shortBreakMs = shortBreak * MS_PER_MINUTE,
            longBreakMs = longBreak * MS_PER_MINUTE,
            workPhasesBeforeLongBreak = workPhasesBeforeLongBreak,
        )
    }
}
