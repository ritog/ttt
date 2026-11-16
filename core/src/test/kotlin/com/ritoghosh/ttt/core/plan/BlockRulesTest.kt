/**
 * BlockRulesTest — order, 5-minute grid, one local day, minimum length, overlap (SPEC 6.3).
 *
 * Depends on: core/plan/BlockRules.kt, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.plan

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.testing.BERLIN
import com.ritoghosh.ttt.core.testing.FakeClock
import com.ritoghosh.ttt.core.testing.KOLKATA
import com.ritoghosh.ttt.core.testing.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRulesTest {

    private val day = "2026-11-16"

    private fun block(start: String, end: String, id: Long = NEW_ID, taskId: Long = 1L, endDay: String = day) =
        Block(id = id, taskId = taskId, startMs = at(day, start), endMs = at(endDay, end))

    private fun check(candidate: Block, existing: List<Block> = emptyList()) =
        checkBlock(candidate, existing, KOLKATA)

    @Test
    fun `a normal block on the grid is fine`() {
        assertEquals(BlockCheck.Ok, check(block("14:00", "16:00")))
    }

    @Test
    fun `end must be after start`() {
        assertEquals(BlockCheck.Invalid(BlockProblem.END_NOT_AFTER_START), check(block("14:00", "14:00")))
        assertEquals(BlockCheck.Invalid(BlockProblem.END_NOT_AFTER_START), check(block("14:00", "13:00")))
    }

    @Test
    fun `fifteen minutes is the minimum length`() {
        assertEquals(BlockCheck.Ok, check(block("14:00", "14:15")))
        assertEquals(BlockCheck.Invalid(BlockProblem.TOO_SHORT), check(block("14:00", "14:10")))
    }

    @Test
    fun `times must sit on five-minute marks`() {
        assertEquals(BlockCheck.Invalid(BlockProblem.NOT_ON_FIVE_MINUTE_GRID), check(block("14:03", "15:00")))
        assertEquals(BlockCheck.Invalid(BlockProblem.NOT_ON_FIVE_MINUTE_GRID), check(block("14:00", "15:07")))
        val withSeconds = Block(NEW_ID, 1L, at(day, "14:00") + 1_000L, at(day, "15:00"))
        assertEquals(BlockCheck.Invalid(BlockProblem.NOT_ON_FIVE_MINUTE_GRID), check(withSeconds))
    }

    @Test
    fun `a block may run until midnight but not past it`() {
        assertEquals(BlockCheck.Ok, check(block("23:00", "00:00", endDay = "2026-11-17")))
        assertEquals(
            BlockCheck.Invalid(BlockProblem.CROSSES_MIDNIGHT),
            check(block("23:30", "00:30", endDay = "2026-11-17")),
        )
    }

    @Test
    fun `the latest end is the next local midnight`() {
        assertEquals(at("2026-11-17", "00:00"), latestEndMs(at(day, "09:20"), KOLKATA))
    }

    @Test
    fun `the latest end follows daylight saving days`() {
        // 2026-03-29 in Berlin has 23 hours; the next midnight is still the next local midnight.
        val start = at("2026-03-29", "22:00", BERLIN)
        assertEquals(at("2026-03-30", "00:00", BERLIN), latestEndMs(start, BERLIN))
    }

    @Test
    fun `blocks in the past are allowed`() {
        val clock = FakeClock(nowMs = at("2026-11-20", "12:00"))
        assertTrue(block("09:00", "10:00").endMs < clock.nowMs())
        assertEquals(BlockCheck.Ok, check(block("09:00", "10:00")))
    }

    @Test
    fun `an overlapping block is rejected and the clashing block is named`() {
        val saved = block("14:00", "16:00", id = 7L)
        assertEquals(BlockCheck.Overlaps(saved), check(block("15:00", "17:00"), listOf(saved)))
    }

    @Test
    fun `blocks of different tasks may not overlap either`() {
        val otherTask = block("14:00", "16:00", id = 7L, taskId = 2L)
        assertEquals(BlockCheck.Overlaps(otherTask), check(block("13:00", "14:30"), listOf(otherTask)))
    }

    @Test
    fun `a block inside another one overlaps`() {
        val saved = block("14:00", "16:00", id = 7L)
        assertEquals(BlockCheck.Overlaps(saved), check(block("14:30", "15:00"), listOf(saved)))
    }

    @Test
    fun `back-to-back blocks touch but do not overlap`() {
        val saved = listOf(block("13:00", "14:00", id = 7L), block("16:00", "17:00", id = 8L))
        assertEquals(BlockCheck.Ok, check(block("14:00", "16:00"), saved))
    }

    @Test
    fun `when several blocks clash the earliest is reported`() {
        val late = block("15:00", "16:00", id = 8L)
        val early = block("13:00", "14:30", id = 7L)
        assertEquals(BlockCheck.Overlaps(early), check(block("14:00", "15:30"), listOf(late, early)))
    }

    @Test
    fun `editing a block does not clash with its own saved copy`() {
        val saved = block("14:00", "16:00", id = 7L)
        val edited = block("14:30", "16:30", id = 7L)
        assertEquals(BlockCheck.Ok, check(edited, listOf(saved)))
    }

    @Test
    fun `shape problems are reported before overlaps`() {
        val saved = block("14:00", "16:00", id = 7L)
        assertEquals(BlockCheck.Invalid(BlockProblem.TOO_SHORT), check(block("14:00", "14:10"), listOf(saved)))
    }

    @Test
    fun `the grid check uses local time`() {
        assertTrue(isOnGrid(at(day, "14:05"), KOLKATA))
        assertFalse(isOnGrid(at(day, "14:06"), KOLKATA))
    }
}
