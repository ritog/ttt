/**
 * PomodoroEntryTest — the session saved by Finish and Stop: real length, pauses, overtime kept or cut,
 * finishing while paused, and discarding sessions under a minute (SPEC 7.3).
 *
 * Depends on: core/timer, core/model, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroEntryTest {

    private fun entry(start: Long, end: Long, paused: Long) = Entry(NEW_ID, TASK, start, end, paused, note = "")

    @Test
    fun `finish keeping overtime records the whole session`() {
        val result = workStartedAt().apply(TimerEvent.Finish(keepOvertime = true), T0 + min(30))
        assertEquals(entry(T0, T0 + min(30), paused = 0L), result.entryToSave)
    }

    @Test
    fun `finish keeping the planned time only cuts the session back to plan`() {
        val result = workStartedAt().apply(TimerEvent.Finish(keepOvertime = false), T0 + min(215))
        assertEquals(entry(T0, T0 + min(25), paused = 0L), result.entryToSave)
    }

    @Test
    fun `cutting back to plan keeps the pauses that happened before the planned end`() {
        val state = workStartedAt()
            .then(TimerEvent.Pause, T0 + min(10))
            .then(TimerEvent.Resume, T0 + min(15))
        val saved = state.apply(TimerEvent.Finish(keepOvertime = false), T0 + min(120)).entryToSave
        // Planned length reached at 15 + (25 − 10) = 30 min; worked 25; paused 5.
        assertEquals(entry(T0, T0 + min(30), paused = min(5)), saved)
        assertEquals(min(25), saved?.workedMs)
    }

    @Test
    fun `paused time inside the session is recorded`() {
        val saved = workStartedAt()
            .then(TimerEvent.Pause, T0 + min(10))
            .then(TimerEvent.Resume, T0 + min(18))
            .apply(TimerEvent.Finish(keepOvertime = true), T0 + min(40))
            .entryToSave
        assertEquals(entry(T0, T0 + min(40), paused = min(8)), saved)
        assertEquals(min(32), saved?.workedMs)
    }

    @Test
    fun `finishing while paused ends the session where the pause began`() {
        val saved = workStartedAt()
            .then(TimerEvent.Pause, T0 + min(30))
            .apply(TimerEvent.Finish(keepOvertime = true), T0 + min(45))
            .entryToSave
        assertEquals(entry(T0, T0 + min(30), paused = 0L), saved)
    }

    @Test
    fun `stopping early keeps the real, partial length`() {
        val result = workStartedAt().apply(TimerEvent.Stop(keepOvertime = true), T0 + min(12))
        assertEquals(entry(T0, T0 + min(12), paused = 0L), result.entryToSave)
        assertEquals(TimerStatus.IDLE, result.state.status)
    }

    @Test
    fun `a session under one minute is discarded`() {
        val result = workStartedAt().apply(TimerEvent.Stop(keepOvertime = true), T0 + sec(59))
        assertNull(result.entryToSave)
        assertEquals(TimerStatus.IDLE, result.state.status)
    }

    @Test
    fun `exactly one minute is kept`() {
        assertEquals(min(1), workStartedAt().apply(TimerEvent.Stop(true), T0 + min(1)).entryToSave?.workedMs)
    }

    @Test
    fun `stop in overtime can also be cut back to plan`() {
        val result = workStartedAt().apply(TimerEvent.Stop(keepOvertime = false), T0 + min(100))
        assertEquals(entry(T0, T0 + min(25), paused = 0L), result.entryToSave)
    }

    @Test
    fun `keep-planned-only before the planned end changes nothing`() {
        val result = workStartedAt().apply(TimerEvent.Stop(keepOvertime = false), T0 + min(10))
        assertEquals(entry(T0, T0 + min(10), paused = 0L), result.entryToSave)
    }

    @Test
    fun `the overtime question is asked only after more than an hour of overtime`() {
        val running = workStartedAt()
        assertFalse(running.needsOvertimeChoice(T0 + min(25 + 60)))
        assertTrue(running.needsOvertimeChoice(T0 + min(25 + 61)))
    }
}
