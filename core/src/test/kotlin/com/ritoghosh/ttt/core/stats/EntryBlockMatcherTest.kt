/**
 * EntryBlockMatcherTest — in block (largest overlap), continued after block (including chains),
 * and unplanned (SPEC 6.6).
 *
 * Depends on: core/stats/EntryBlockMatcher.kt, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.stats

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.testing.at
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryBlockMatcherTest {

    private val day = "2026-11-16"
    private val task = 1L
    private val otherTask = 2L

    private fun block(id: Long, start: String, end: String, taskId: Long = task) =
        Block(id, taskId, at(day, start), at(day, end))

    private fun entry(id: Long, start: String, end: String, taskId: Long = task) =
        Entry(id, taskId, at(day, start), at(day, end), pausedMs = 0L, note = "")

    private fun match(entries: List<Entry>, blocks: List<Block>) = matchEntriesToBlocks(entries, blocks)

    @Test
    fun `a session overlapping a block is in that block`() {
        val result = match(listOf(entry(10, "14:30", "14:55")), listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.InBlock(1), result[10])
    }

    @Test
    fun `a session overlapping two blocks belongs to the one it overlaps most`() {
        val blocks = listOf(block(1, "13:00", "14:00"), block(2, "14:00", "16:00"))
        val result = match(listOf(entry(10, "13:50", "14:30")), blocks)
        assertEquals(PlanMatch.InBlock(2), result[10])
    }

    @Test
    fun `overrunning a block still counts as in block`() {
        val result = match(listOf(entry(10, "15:40", "16:25")), listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.InBlock(1), result[10])
    }

    @Test
    fun `a session starting within 30 minutes after a block continues it`() {
        val result = match(listOf(entry(10, "16:30", "16:55")), listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.ContinuedAfter(1), result[10])
    }

    @Test
    fun `a session starting more than 30 minutes after a block is unplanned`() {
        val result = match(listOf(entry(10, "16:31", "16:55")), listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.Unplanned, result[10])
    }

    @Test
    fun `continued sessions chain, each within 30 minutes of the previous one`() {
        val entries = listOf(
            entry(12, "17:20", "17:45"), // given out of order on purpose
            entry(10, "16:10", "16:35"),
            entry(11, "16:50", "17:15"),
        )
        val result = match(entries, listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.ContinuedAfter(1), result[10])
        assertEquals(PlanMatch.ContinuedAfter(1), result[11])
        assertEquals(PlanMatch.ContinuedAfter(1), result[12])
    }

    @Test
    fun `a gap of more than 30 minutes breaks the chain`() {
        val entries = listOf(entry(10, "16:10", "16:35"), entry(11, "17:10", "17:30"))
        val result = match(entries, listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.ContinuedAfter(1), result[10])
        assertEquals(PlanMatch.Unplanned, result[11])
    }

    @Test
    fun `a session in the block does not start a chain`() {
        // 16:10 session is in the block (it overlaps); 16:45 is 45 min after the block end → unplanned.
        val entries = listOf(entry(10, "15:30", "16:10"), entry(11, "16:45", "17:00"))
        val result = match(entries, listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.InBlock(1), result[10])
        assertEquals(PlanMatch.Unplanned, result[11])
    }

    @Test
    fun `blocks of other tasks are ignored`() {
        val result = match(listOf(entry(10, "14:30", "14:55")), listOf(block(1, "14:00", "16:00", taskId = otherTask)))
        assertEquals(PlanMatch.Unplanned, result[10])
    }

    @Test
    fun `a session before its block is unplanned`() {
        val result = match(listOf(entry(10, "12:00", "12:25")), listOf(block(1, "14:00", "16:00")))
        assertEquals(PlanMatch.Unplanned, result[10])
    }

    @Test
    fun `with two earlier blocks, the nearer one is continued`() {
        val blocks = listOf(block(1, "09:00", "10:00"), block(2, "13:00", "14:00"))
        val result = match(listOf(entry(10, "14:10", "14:30")), blocks)
        assertEquals(PlanMatch.ContinuedAfter(2), result[10])
    }
}
