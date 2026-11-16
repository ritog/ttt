/**
 * PomodoroMachine — the timer's rules, as pure functions (SPEC 7.1–7.3).
 *
 * Depends on: core/model, TimerEvent.kt, TimerMath.kt, PomodoroSteps.kt, PomodoroSettings.kt, core/Constants.kt.
 * Must not: touch Android, the database or the system clock (use the nowMs passed in).
 *
 * In words: a work phase counts down to its planned length, then keeps counting as overtime
 * until Finish. A break ends on its own. Nothing starts without a tap. An event that does not
 * apply to the current state changes nothing.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.MIN_ENTRY_MS
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus

/**
 * Applies [event] to [state] at [nowMs].
 *
 * A break that has already run out is ended first, whatever the event (as if Evaluate had been
 * sent), so a late tap never acts on a break that is really over. This never chains: only that
 * one break ends, and the next work phase still waits for a tap.
 */
fun transition(state: TimerState, event: TimerEvent, nowMs: Long, settings: PomodoroSettings): TransitionResult {
    val afterDueBreak = evaluate(state, nowMs, settings)
    val result = handle(afterDueBreak.state, event, nowMs, settings)
    return if (result.alarm == AlarmAction.Unchanged) result.copy(alarm = afterDueBreak.alarm) else result
}

private fun handle(state: TimerState, event: TimerEvent, nowMs: Long, settings: PomodoroSettings): TransitionResult =
    when (event) {
        is TimerEvent.Start -> start(state, event.taskId, nowMs, settings)
        TimerEvent.Pause -> pause(state, nowMs)
        TimerEvent.Resume -> resume(state, nowMs)
        TimerEvent.Evaluate -> TransitionResult(state) // already applied in transition()
        is TimerEvent.Finish -> finish(state, event.keepOvertime, nowMs, settings)
        is TimerEvent.Stop -> stop(state, event.keepOvertime, nowMs)
        TimerEvent.StartNext -> startNext(state, nowMs)
        TimerEvent.SkipBreak -> skipBreak(state, settings)
        is TimerEvent.ChangeTask -> changeTask(state, event.taskId)
        TimerEvent.ClearNotePrompt -> TransitionResult(state.copy(notePromptEntryId = null))
    }

private fun start(state: TimerState, taskId: Long, nowMs: Long, settings: PomodoroSettings): TransitionResult {
    if (state.status != TimerStatus.IDLE) return TransitionResult(state)
    val running = state.copy(
        phase = TimerPhase.WORK,
        status = TimerStatus.RUNNING,
        taskId = taskId,
        phaseLengthMs = settings.workMs,
        accumulatedMs = 0L,
        runningSinceMs = nowMs,
        pausedSinceMs = null,
        sessionStartedAtMs = nowMs,
        plannedEndReachedAtMs = null,
    )
    return TransitionResult(running, alarm = AlarmAction.ScheduleAt(nowMs + settings.workMs))
}

private fun pause(state: TimerState, nowMs: Long): TransitionResult {
    if (state.status != TimerStatus.RUNNING) return TransitionResult(state)
    val paused = endRunSegment(state, nowMs).copy(status = TimerStatus.PAUSED, pausedSinceMs = nowMs)
    return TransitionResult(paused, alarm = AlarmAction.Cancel)
}

private fun resume(state: TimerState, nowMs: Long): TransitionResult {
    if (state.status != TimerStatus.PAUSED) return TransitionResult(state)
    val running = state.copy(status = TimerStatus.RUNNING, runningSinceMs = nowMs, pausedSinceMs = null)
    val remaining = running.remainingMs(nowMs)
    // In overtime there is no planned end left to alert about.
    val alarm = if (remaining > 0) AlarmAction.ScheduleAt(nowMs + remaining) else AlarmAction.Cancel
    return TransitionResult(running, alarm = alarm)
}

/** Ends a running break whose time is up and queues work for the same task. Anything else: unchanged. */
private fun evaluate(state: TimerState, nowMs: Long, settings: PomodoroSettings): TransitionResult =
    if (state.isBreakDue(nowMs)) {
        TransitionResult(queueWork(state, settings), alarm = AlarmAction.Cancel)
    } else {
        TransitionResult(state)
    }

private fun finish(state: TimerState, keepOvertime: Boolean, nowMs: Long, settings: PomodoroSettings): TransitionResult {
    if (!state.isInOvertime(nowMs)) return TransitionResult(state)
    val entry = sessionEntry(state, keepOvertime, nowMs)
    val completed = state.completedWorkPhases + 1
    val isLongBreak = completed % settings.workPhasesBeforeLongBreak == 0
    val awaitingBreak = state.copy(
        phase = if (isLongBreak) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK,
        status = TimerStatus.AWAITING_START,
        phaseLengthMs = if (isLongBreak) settings.longBreakMs else settings.shortBreakMs,
        accumulatedMs = 0L,
        runningSinceMs = null,
        pausedSinceMs = null,
        sessionStartedAtMs = null,
        plannedEndReachedAtMs = null,
        completedWorkPhases = completed,
    )
    return TransitionResult(awaitingBreak, entryToSave = entry, alarm = AlarmAction.Cancel)
}

private fun stop(state: TimerState, keepOvertime: Boolean, nowMs: Long): TransitionResult {
    if (state.status == TimerStatus.IDLE) return TransitionResult(state)
    // Sessions under a minute are discarded (SPEC 7.3); breaks are never recorded.
    val entry = if (state.phase == TimerPhase.WORK && state.isActive) {
        sessionEntry(state, keepOvertime, nowMs)?.takeIf { it.workedMs >= MIN_ENTRY_MS }
    } else {
        null
    }
    return TransitionResult(idleFrom(state), entryToSave = entry, alarm = AlarmAction.Cancel)
}

private fun startNext(state: TimerState, nowMs: Long): TransitionResult {
    if (state.status != TimerStatus.AWAITING_START) return TransitionResult(state)
    val running = state.copy(
        status = TimerStatus.RUNNING,
        accumulatedMs = 0L,
        runningSinceMs = nowMs,
        pausedSinceMs = null,
        sessionStartedAtMs = if (state.phase == TimerPhase.WORK) nowMs else null,
        plannedEndReachedAtMs = null,
    )
    return TransitionResult(running, alarm = AlarmAction.ScheduleAt(nowMs + state.phaseLengthMs))
}

private fun skipBreak(state: TimerState, settings: PomodoroSettings): TransitionResult =
    if (state.status == TimerStatus.AWAITING_START && state.isBreak) {
        TransitionResult(queueWork(state, settings))
    } else {
        TransitionResult(state)
    }

private fun changeTask(state: TimerState, taskId: Long): TransitionResult =
    if (state.status == TimerStatus.IDLE || state.status == TimerStatus.AWAITING_START) {
        TransitionResult(state.copy(taskId = taskId))
    } else {
        TransitionResult(state)
    }
