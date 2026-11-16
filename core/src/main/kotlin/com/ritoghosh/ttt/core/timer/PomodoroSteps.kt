/**
 * PomodoroSteps — the small building blocks PomodoroMachine uses: closing a run segment,
 * building the session entry, queueing work, and returning to idle (SPEC 7.2, 7.3).
 *
 * Depends on: core/model, TimerMath.kt, PomodoroSettings.kt.
 * Must not: be called from outside core/timer; use transition() instead.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus

/**
 * Folds the current run segment into accumulatedMs. If a work phase crossed its planned length
 * during this segment and the moment is not known yet, records it: running_since + (length − accumulated).
 */
internal fun endRunSegment(state: TimerState, nowMs: Long): TimerState {
    val since = state.runningSinceMs ?: return state
    val reachedPlan = state.phase == TimerPhase.WORK &&
        state.plannedEndReachedAtMs == null &&
        state.elapsedMs(nowMs) >= state.phaseLengthMs
    val plannedEnd = if (reachedPlan) since + (state.phaseLengthMs - state.accumulatedMs) else state.plannedEndReachedAtMs
    return state.copy(
        accumulatedMs = state.accumulatedMs + (nowMs - since),
        runningSinceMs = null,
        plannedEndReachedAtMs = plannedEnd,
    )
}

/**
 * The entry for the current work phase, one rule for Finish and Stop (SPEC 7.3):
 * end = now if running, or the start of the open pause if paused (that pause is not part of the session);
 * worked = elapsed; paused_ms = (end − start) − worked.
 * With [keepOvertime] = false the entry is cut back to plan: it ends at planned_end_reached and
 * counts exactly the planned length as worked.
 */
internal fun sessionEntry(state: TimerState, keepOvertime: Boolean, nowMs: Long): Entry? {
    val taskId = state.taskId ?: return null
    val start = state.sessionStartedAtMs ?: return null
    val closed = endRunSegment(state, nowMs)
    val worked = closed.accumulatedMs
    val plannedEnd = closed.plannedEndReachedAtMs
    if (!keepOvertime && worked > state.phaseLengthMs && plannedEnd != null) {
        return Entry(NEW_ID, taskId, start, plannedEnd, (plannedEnd - start) - state.phaseLengthMs, note = "")
    }
    val end = if (state.status == TimerStatus.RUNNING) nowMs else state.pausedSinceMs ?: nowMs
    return Entry(NEW_ID, taskId, start, end, (end - start) - worked, note = "")
}

/** Waits (AWAITING_START) for the next work phase on the same task. */
internal fun queueWork(state: TimerState, settings: PomodoroSettings): TimerState = state.copy(
    phase = TimerPhase.WORK,
    status = TimerStatus.AWAITING_START,
    phaseLengthMs = settings.workMs,
    accumulatedMs = 0L,
    runningSinceMs = null,
    pausedSinceMs = null,
    sessionStartedAtMs = null,
    plannedEndReachedAtMs = null,
)

/**
 * Back to idle. The long-break count and all session fields reset (the count belongs to one
 * unbroken run of pomodoros). The task stays selected for next time, and a pending note card stays.
 */
internal fun idleFrom(state: TimerState): TimerState =
    TimerState.IDLE.copy(taskId = state.taskId, notePromptEntryId = state.notePromptEntryId)
