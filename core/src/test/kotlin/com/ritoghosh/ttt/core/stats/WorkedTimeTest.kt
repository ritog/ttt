/**
 * WorkedTimeTest — worked time inside a block (pauses spread evenly), continued time, and tag totals.
 *
 * Depends on: core/stats, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.stats

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.testing.at
import com.ritoghosh.ttt.core.testing.minutes
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkedTimeTest {

    private val day = "2026-11-16"
    private val block = Block(1, taskId = 1, startMs = at(day, "14:00"), endMs = at(day, "16:00"))

    private fun entry(id: Long, start: String, end: String, paused: Long = 0L, taskId: Long = 1L) =
        Entry(id, taskId, at(day, start), at(day, end), paused, note = "")

    @Test
    fun `a session fully inside the block counts its worked time`() {
        assertEquals(minutes(25), workedInBlockMs(block, listOf(entry(10, "14:00", "14:30", paused = minutes(5)))))
    }

    @Test
    fun `only the part inside the window counts`() {
        // 15:30–16:30 overlaps the block for 30 of its 60 minutes.
        assertEquals(minutes(30), workedInBlockMs(block, listOf(entry(10, "15:30", "16:30"))))
    }

    @Test
    fun `pauses are spread evenly over the session`() {
        // 60 min wall clock, 20 min paused → 2/3 worked; 30 min inside the block → 20 min.
        assertEquals(minutes(20), workedInBlockMs(block, listOf(entry(10, "15:30", "16:30", paused = minutes(20)))))
    }

    @Test
    fun `sessions of other tasks do not count`() {
        assertEquals(0L, workedInBlockMs(block, listOf(entry(10, "14:00", "15:00", taskId = 2L))))
    }

    @Test
    fun `continued time sums the sessions matched as continued`() {
        val entries = listOf(
            entry(10, "15:00", "16:00"),
            entry(11, "16:10", "16:40", paused = minutes(5)),
            entry(12, "18:00", "18:30"),
        )
        val matches = matchEntriesToBlocks(entries, listOf(block))
        assertEquals(minutes(25), continuedAfterBlockMs(block, entries, matches))
    }

    @Test
    fun `a task with two tags counts in both, and untagged time comes last`() {
        val entries = listOf(
            entry(10, "09:00", "10:00", taskId = 1L),
            entry(11, "11:00", "11:30", taskId = 2L),
            entry(12, "12:00", "12:15", taskId = 3L),
        )
        val tags = mapOf(1L to listOf("book", "deep"), 2L to listOf("book"), 3L to emptyList())
        assertEquals(
            listOf(
                TagTotal("book", minutes(90)),
                TagTotal("deep", minutes(60)),
                TagTotal(null, minutes(15)),
            ),
            tagTotals(entries, tags),
        )
    }
}
