/**
 * PomodoroMachineTest — every transition of SPEC 7.3: start, pause/resume arithmetic, overtime,
 * breaks, no chaining, long-break cadence, task changes, note prompt.
 * Entry details (Finish/Stop, keepOvertime, paused_ms) are in PomodoroEntryTest.
 *
 * Depends on: core/timer, core/model, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PomodoroMachineTest {

    @Test
    fun `start from idle runs a work phase and schedules the planned end`() {
        val result = TimerState.IDLE.apply(TimerEvent.Start(TASK), T0)
        with(result.state) {
            assertEquals(TimerPhase.WORK, phase)
            assertEquals(TimerStatus.RUNNING, status)
            assertEquals(TASK, taskId)
            assertEquals(min(25), phaseLengthMs)
            assertEquals(T0, sessionStartedAtMs)
        }
        assertEquals(AlarmAction.ScheduleAt(T0 + min(25)), result.alarm)
    }

    @Test
    fun `start is ignored unless idle`() {
        val running = workStartedAt()
        assertEquals(running, running.then(TimerEvent.Start(OTHER_TASK), T0 + min(1)))
    }

    @Test
    fun `pause adds the run to accumulated time and cancels the alarm`() {
        val result = workStartedAt().apply(TimerEvent.Pause, T0 + min(10))
        assertEquals(TimerStatus.PAUSED, result.state.status)
        assertEquals(min(10), result.state.accumulatedMs)
        assertEquals(T0 + min(10), result.state.pausedSinceMs)
        assertEquals(AlarmAction.Cancel, result.alarm)
    }

    @Test
    fun `time stands still while paused and resume reschedules for what is left`() {
        val paused = workStartedAt().then(TimerEvent.Pause, T0 + min(10))
        assertEquals(min(10), paused.elapsedMs(T0 + min(14)))
        val result = paused.apply(TimerEvent.Resume, T0 + min(15))
        assertEquals(AlarmAction.ScheduleAt(T0 + min(30)), result.alarm)
        assertEquals(min(15), result.state.elapsedMs(T0 + min(20)))
        assertEquals(min(10), result.state.remainingMs(T0 + min(20)))
    }

    @Test
    fun `a work phase past its planned length keeps counting as overtime`() {
        val running = workStartedAt()
        val evaluated = running.then(TimerEvent.Evaluate, T0 + min(30))
        assertEquals(running, evaluated)
        assertEquals(min(5), evaluated.overtimeMs(T0 + min(30)))
    }

    @Test
    fun `pausing and resuming during overtime keeps the overtime and sets no alarm`() {
        val paused = workStartedAt().then(TimerEvent.Pause, T0 + min(30))
        assertEquals(T0 + min(25), paused.plannedEndReachedAtMs)
        val result = paused.apply(TimerEvent.Resume, T0 + min(40))
        assertEquals(AlarmAction.Cancel, result.alarm)
        assertEquals(min(15), result.state.overtimeMs(T0 + min(50)))
    }

    @Test
    fun `planned end reached accounts for earlier pauses`() {
        val state = workStartedAt()
            .then(TimerEvent.Pause, T0 + min(10))
            .then(TimerEvent.Resume, T0 + min(20))
            .then(TimerEvent.Pause, T0 + min(40))
        // 10 min before the pause + 15 min after resuming at 20 → planned length reached at 35.
        assertEquals(T0 + min(35), state.plannedEndReachedAtMs)
    }

    @Test
    fun `finish before the planned length is ignored`() {
        val running = workStartedAt()
        val result = running.apply(TimerEvent.Finish(keepOvertime = true), T0 + min(20))
        assertEquals(running, result.state)
        assertNull(result.entryToSave)
    }

    @Test
    fun `finish queues a short break that waits for a tap`() {
        val result = workStartedAt().apply(TimerEvent.Finish(keepOvertime = true), T0 + min(26))
        with(result.state) {
            assertEquals(TimerPhase.SHORT_BREAK, phase)
            assertEquals(TimerStatus.AWAITING_START, status)
            assertEquals(min(5), phaseLengthMs)
            assertEquals(1, completedWorkPhases)
            assertEquals(TASK, taskId)
        }
        assertEquals(AlarmAction.Cancel, result.alarm)
    }

    @Test
    fun `start next begins the break and schedules its end`() {
        val awaiting = workStartedAt().then(TimerEvent.Finish(keepOvertime = true), T0 + min(26))
        val result = awaiting.apply(TimerEvent.StartNext, T0 + min(27))
        assertEquals(TimerStatus.RUNNING, result.state.status)
        assertNull(result.state.sessionStartedAtMs)
        assertEquals(AlarmAction.ScheduleAt(T0 + min(32)), result.alarm)
    }

    @Test
    fun `a running break ends on evaluate and queues work for the same task`() {
        val onBreak = workStartedAt()
            .then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
            .then(TimerEvent.StartNext, T0 + min(25))
        assertEquals(onBreak, onBreak.then(TimerEvent.Evaluate, T0 + min(29)))
        val after = onBreak.then(TimerEvent.Evaluate, T0 + min(30))
        assertEquals(TimerPhase.WORK, after.phase)
        assertEquals(TimerStatus.AWAITING_START, after.status)
        assertEquals(TASK, after.taskId)
        assertEquals(min(25), after.phaseLengthMs)
    }

    @Test
    fun `after a long absence only the one break ends, nothing chains`() {
        val onBreak = workStartedAt()
            .then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
            .then(TimerEvent.StartNext, T0 + min(25))
        val tenHoursLater = onBreak.then(TimerEvent.Evaluate, T0 + min(600))
        assertEquals(TimerStatus.AWAITING_START, tenHoursLater.status)
        assertEquals(TimerPhase.WORK, tenHoursLater.phase)
        assertNull(tenHoursLater.runningSinceMs)
    }

    @Test
    fun `a late tap on a break that already ran out acts on the ended break`() {
        val onBreak = workStartedAt()
            .then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
            .then(TimerEvent.StartNext, T0 + min(25))
        val result = onBreak.apply(TimerEvent.Pause, T0 + min(90))
        assertEquals(TimerStatus.AWAITING_START, result.state.status)
        assertEquals(TimerPhase.WORK, result.state.phase)
        assertEquals(AlarmAction.Cancel, result.alarm)
    }

    @Test
    fun `starting queued work opens a new session`() {
        val (awaitingWork, at) = TimerState.IDLE.oneFullPomodoro(T0)
        val working = awaitingWork.then(TimerEvent.StartNext, at + min(2))
        assertEquals(at + min(2), working.sessionStartedAtMs)
        assertEquals(TimerPhase.WORK, working.phase)
    }

    @Test
    fun `skip break queues work straight away`() {
        val awaitingBreak = workStartedAt().then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
        val skipped = awaitingBreak.then(TimerEvent.SkipBreak, T0 + min(25))
        assertEquals(TimerPhase.WORK, skipped.phase)
        assertEquals(TimerStatus.AWAITING_START, skipped.status)
    }

    @Test
    fun `every fourth finished work phase is followed by a long break`() {
        var state = TimerState.IDLE
        var at = T0
        repeat(3) {
            val (next, nextAt) = state.oneFullPomodoro(at)
            state = next
            at = nextAt
        }
        val fourth = state.then(TimerEvent.StartNext, at).then(TimerEvent.Finish(keepOvertime = true), at + min(25))
        assertEquals(TimerPhase.LONG_BREAK, fourth.phase)
        assertEquals(min(15), fourth.phaseLengthMs)
        assertEquals(4, fourth.completedWorkPhases)
    }

    @Test
    fun `the long-break count resets when the timer goes idle`() {
        val (afterOne, at) = TimerState.IDLE.oneFullPomodoro(T0)
        val idle = afterOne.then(TimerEvent.Stop(keepOvertime = true), at)
        assertEquals(TimerStatus.IDLE, idle.status)
        assertEquals(0, idle.completedWorkPhases)
        assertEquals(TASK, idle.taskId) // stays selected for next time
    }

    @Test
    fun `stop during a break or while waiting goes idle without a session`() {
        val awaiting = workStartedAt().then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
        val result = awaiting.then(TimerEvent.StartNext, T0 + min(25)).apply(TimerEvent.Stop(true), T0 + min(27))
        assertEquals(TimerStatus.IDLE, result.state.status)
        assertNull(result.entryToSave)
        assertEquals(AlarmAction.Cancel, result.alarm)
    }

    @Test
    fun `the task can change while idle or waiting, never mid-work`() {
        assertEquals(OTHER_TASK, TimerState.IDLE.then(TimerEvent.ChangeTask(OTHER_TASK), T0).taskId)
        val awaiting = workStartedAt().then(TimerEvent.Finish(keepOvertime = true), T0 + min(25))
        assertEquals(OTHER_TASK, awaiting.then(TimerEvent.ChangeTask(OTHER_TASK), T0 + min(25)).taskId)
        assertEquals(TASK, workStartedAt().then(TimerEvent.ChangeTask(OTHER_TASK), T0 + min(1)).taskId)
    }

    @Test
    fun `the note prompt survives going idle and clears when handled`() {
        val withPrompt = workStartedAt().copy(notePromptEntryId = 42L)
        val idle = withPrompt.then(TimerEvent.Stop(keepOvertime = true), T0 + min(5))
        assertEquals(42L, idle.notePromptEntryId)
        assertNull(idle.then(TimerEvent.ClearNotePrompt, T0 + min(6)).notePromptEntryId)
    }

    @Test
    fun `events that do not apply change nothing`() {
        val idle = TimerState.IDLE
        listOf(TimerEvent.Pause, TimerEvent.Resume, TimerEvent.StartNext, TimerEvent.SkipBreak, TimerEvent.Stop(true))
            .forEach { event -> assertEquals(event.toString(), idle, idle.then(event, T0)) }
    }
}
