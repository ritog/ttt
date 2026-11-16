/**
 * TimerState — the pomodoro timer's persisted state, mirroring the single timer_state row (SPEC 7.2).
 *
 * Depends on: nothing.
 * Must not: count time by ticking. Elapsed time is always computed from these timestamps
 * (PomodoroMachine, M4), so the timer stays right after the app is killed or the phone sleeps.
 */
package com.ritoghosh.ttt.core.model

enum class TimerPhase { NONE, WORK, SHORT_BREAK, LONG_BREAK }

/** AWAITING_START means [TimerState.phase] is queued and waits for a tap (nothing starts by itself). */
enum class TimerStatus { IDLE, RUNNING, PAUSED, AWAITING_START }

data class TimerState(
    val phase: TimerPhase,
    val status: TimerStatus,
    /** Task being worked on; kept through breaks. */
    val taskId: Long?,
    /** Planned length of the current (or queued) phase. */
    val phaseLengthMs: Long,
    /** Elapsed time in this phase before the current run segment. */
    val accumulatedMs: Long,
    /** When the current run segment began; non-null only while RUNNING. */
    val runningSinceMs: Long?,
    /** When the current pause began; non-null only while PAUSED. */
    val pausedSinceMs: Long?,
    /** Wall-clock start of the current WORK phase; null otherwise. */
    val sessionStartedAtMs: Long?,
    /** When a WORK phase reached its planned length; null until known. */
    val plannedEndReachedAtMs: Long?,
    /** Work phases finished in the current unbroken run of pomodoros (long-break cadence). */
    val completedWorkPhases: Int,
    /** Entry waiting for the "Note for this session" card; null when none. */
    val notePromptEntryId: Long?,
) {
    companion object {
        /** The resting state: no phase, no task, nothing counted. */
        val IDLE = TimerState(
            phase = TimerPhase.NONE,
            status = TimerStatus.IDLE,
            taskId = null,
            phaseLengthMs = 0L,
            accumulatedMs = 0L,
            runningSinceMs = null,
            pausedSinceMs = null,
            sessionStartedAtMs = null,
            plannedEndReachedAtMs = null,
            completedWorkPhases = 0,
            notePromptEntryId = null,
        )
    }
}
