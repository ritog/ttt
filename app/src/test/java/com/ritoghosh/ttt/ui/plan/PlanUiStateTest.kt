/**
 * PlanUiStateTest — joining a day's blocks and sessions with their tasks, worked time per block,
 * the tag filter, and "is this today" (SPEC 6.5, 6.6, 9.2).
 *
 * Depends on: ui/plan/PlanUiState.kt, core/model, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.plan

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlanUiStateTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.parse("2026-11-16")
    private val minute = 60_000L
    private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, title: String, tags: List<String> = emptyList()) =
        Task(id, title, tags, isDone = false, createdAtMs = 0L, doneAtMs = null, note = "")

    private val writing = task(1, "Write", listOf("book", "deep"))
    private val errands = task(2, "Errands", listOf("home"))
    private val blocks = listOf(
        Block(11, taskId = 2, startMs = at("16:00"), endMs = at("17:00")),
        Block(10, taskId = 1, startMs = at("09:00"), endMs = at("11:00")),
    )
    private val entries = listOf(
        Entry(20, taskId = 1, startedAtMs = at("09:30"), endedAtMs = at("10:00"), pausedMs = 0L, note = ""),
        Entry(21, taskId = 2, startedAtMs = at("12:00"), endedAtMs = at("12:20"), pausedMs = 0L, note = ""),
    )

    private fun build(
        selectedTag: String? = null,
        nowMs: Long = at("12:00"),
        blockList: List<Block> = blocks,
        entryList: List<Entry> = entries,
    ) = buildPlanUiState(
        day, zone, nowMs, blockList, entryList,
        listOf(writing, errands), listOf("book", "deep", "home"), selectedTag,
    )

    @Test
    fun `blocks come with their task title and first tag, earliest first`() {
        val state = build()
        assertEquals(listOf(10L, 11L), state.blocks.map { it.block.id })
        assertEquals("Write", state.blocks.first().taskTitle)
        assertEquals("book", state.blocks.first().firstTag)
    }

    @Test
    fun `each block shows the time worked on its task inside its window`() {
        val state = build()
        assertEquals(30 * minute, state.blocks.first { it.block.id == 10L }.workedInBlockMs)
        assertEquals(0L, state.blocks.first { it.block.id == 11L }.workedInBlockMs) // errands worked at 12, not 16–17
    }

    @Test
    fun `sessions are listed for the actual column, earliest first`() {
        assertEquals(listOf(20L, 21L), build().sessions.map { it.entry.id })
    }

    @Test
    fun `the tag filter keeps only blocks and sessions whose task has that tag`() {
        val state = build(selectedTag = "home")
        assertEquals("home", state.activeTag)
        assertEquals(listOf(11L), state.blocks.map { it.block.id })
        assertEquals(listOf(21L), state.sessions.map { it.entry.id })
    }

    @Test
    fun `a block whose task is missing is skipped`() {
        val orphan = Block(12, taskId = 99, startMs = at("13:00"), endMs = at("14:00"))
        assertEquals(listOf(10L, 11L), build(blockList = blocks + orphan).blocks.map { it.block.id })
    }

    @Test
    fun `the day range and today flag follow the zone`() {
        val state = build()
        assertEquals(at("00:00"), state.dayStartMs)
        assertEquals(24 * 60 * minute, state.dayEndMs - state.dayStartMs)
        assertTrue(state.isToday)
        assertFalse(build(nowMs = at("12:00") + 24 * 60 * minute).isToday)
    }

    @Test
    fun `a task without tags gives an untinted block`() {
        val plain = task(3, "Call")
        val state = buildPlanUiState(
            day, zone, at("12:00"),
            listOf(Block(13, 3, at("10:00"), at("10:30"))), emptyList(),
            listOf(plain), emptyList(), null,
        )
        assertNull(state.blocks.single().firstTag)
    }
}
