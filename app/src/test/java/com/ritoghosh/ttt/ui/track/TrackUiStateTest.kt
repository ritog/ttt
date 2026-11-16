/**
 * TrackUiStateTest — the Track screen's derived state: selected task, filtered picker, planned-today
 * line, today's totals, and when the task may be changed (SPEC 6.5, 7.4, 9.3).
 *
 * Depends on: ui/track/TrackUiState.kt, core/model, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.track

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TrackUiStateTest {

    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun task(id: Long, title: String, tags: List<String> = emptyList()) =
        Task(id, title, tags, isDone = false, createdAtMs = id, doneAtMs = null, note = "")

    private val write = task(1, "Write", listOf("book"))
    private val shop = task(2, "Shop", listOf("home"))

    private fun build(
        timer: TimerState = TimerState.IDLE.copy(taskId = 1),
        blocks: List<Block> = emptyList(),
        entries: List<Entry> = emptyList(),
        selectedTag: String? = null,
        local: TrackLocalState = TrackLocalState(),
    ) = buildTrackUiState(
        timer, listOf(write, shop), now, ZoneOffset.UTC, entries, blocks,
        tags = listOf("book", "home"), selectedTag = selectedTag, taskQuery = "", local = local,
    )

    @Test
    fun `the selected task comes from the timer state`() {
        val state = build()
        assertEquals(write, state.task)
        assertFalse(state.isPickingTask)
    }

    @Test
    fun `with no task selected the picker shows its search`() {
        assertTrue(build(timer = TimerState.IDLE).isPickingTask)
    }

    @Test
    fun `the picker respects the tag filter`() {
        assertEquals(listOf(shop), build(selectedTag = "home").taskOptions)
    }

    @Test
    fun `planned today shows the next block of the selected task, else the last one`() {
        val past = Block(10, 1, now - 120 * minute, now - 60 * minute)
        val next = Block(11, 1, now + 30 * minute, now + 90 * minute)
        val otherTask = Block(12, 2, now + 10 * minute, now + 20 * minute)
        assertEquals(next, build(blocks = listOf(past, next, otherTask)).plannedToday)
        assertEquals(past, build(blocks = listOf(past, otherTask)).plannedToday)
        assertNull(build(blocks = listOf(otherTask)).plannedToday)
    }

    @Test
    fun `today counts sessions and worked time`() {
        val entries = listOf(
            Entry(1, 1, now - 100 * minute, now - 70 * minute, pausedMs = 5 * minute, note = ""),
            Entry(2, 2, now - 60 * minute, now - 35 * minute, pausedMs = 0L, note = ""),
        )
        val state = build(entries = entries)
        assertEquals(2, state.todaySessionCount)
        assertEquals(50 * minute, state.todayWorkedMs)
    }

    @Test
    fun `the task can be chosen while idle or waiting, not mid-work`() {
        assertTrue(build().canChooseTask)
        val running = TimerState.IDLE.copy(
            phase = TimerPhase.WORK, status = TimerStatus.RUNNING, taskId = 1,
            phaseLengthMs = 25 * minute, runningSinceMs = now, sessionStartedAtMs = now,
        )
        assertFalse(build(timer = running).canChooseTask)
        assertTrue(build(timer = running.copy(status = TimerStatus.AWAITING_START, runningSinceMs = null)).canChooseTask)
    }

    @Test
    fun `a running break past its length is due`() {
        val breakRunning = TimerState.IDLE.copy(
            phase = TimerPhase.SHORT_BREAK, status = TimerStatus.RUNNING, taskId = 1,
            phaseLengthMs = 5 * minute, runningSinceMs = now - 6 * minute,
        )
        assertTrue(build(timer = breakRunning).isBreakDue)
    }
}
