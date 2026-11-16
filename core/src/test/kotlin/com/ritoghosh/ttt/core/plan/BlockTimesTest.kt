/**
 * BlockTimesTest — the block editor's time arithmetic: instants, rounding, durations, suggested start.
 *
 * Depends on: core/plan/BlockTimes.kt, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.plan

import com.ritoghosh.ttt.core.testing.BERLIN
import com.ritoghosh.ttt.core.testing.KOLKATA
import com.ritoghosh.ttt.core.testing.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BlockTimesTest {

    private val day = LocalDate.parse("2026-11-16")
    private fun time(text: String) = LocalTime.parse(text)

    @Test
    fun `start and end become instants on that local day`() {
        assertEquals(at("2026-11-16", "14:00"), blockStartMs(day, time("14:00"), KOLKATA))
        assertEquals(at("2026-11-16", "16:30"), blockEndMs(day, time("16:30"), KOLKATA))
    }

    @Test
    fun `an end of 00_00 means the next midnight`() {
        assertEquals(at("2026-11-17", "00:00"), blockEndMs(day, LocalTime.MIDNIGHT, KOLKATA))
    }

    @Test
    fun `an end at midnight reads back as 00_00`() {
        assertEquals(LocalTime.MIDNIGHT, blockEndTime(at("2026-11-17", "00:00"), KOLKATA))
    }

    @Test
    fun `times round to the nearest five minutes`() {
        assertEquals(time("14:00"), roundToGrid(time("14:02")))
        assertEquals(time("14:05"), roundToGrid(time("14:03")))
        assertEquals(time("15:00"), roundToGrid(time("14:58")))
    }

    @Test
    fun `rounding never wraps into the next day`() {
        assertEquals(time("23:55"), roundToGrid(time("23:58")))
        assertEquals(LocalTime.MIDNIGHT, roundToGrid(LocalTime.MIDNIGHT))
    }

    @Test
    fun `a duration chip stops at midnight`() {
        assertEquals(time("15:30"), endAfterDuration(time("14:00"), 90))
        assertEquals(LocalTime.MIDNIGHT, endAfterDuration(time("23:30"), 60))
    }

    @Test
    fun `duration counts an end of 00_00 as midnight`() {
        assertEquals(120, durationMinutes(time("14:00"), time("16:00")))
        assertEquals(60, durationMinutes(time("23:00"), LocalTime.MIDNIGHT))
        assertEquals(-60, durationMinutes(time("14:00"), time("13:00")))
    }

    @Test
    fun `on today a new block starts at the next five-minute mark`() {
        val now = at("2026-11-16", "14:02") + 30_000L // 14:02:30
        assertEquals(time("14:05"), suggestedBlockStart(now, day, KOLKATA))
    }

    @Test
    fun `exactly on a mark, the new block starts right then`() {
        assertEquals(time("14:05"), suggestedBlockStart(at("2026-11-16", "14:05"), day, KOLKATA))
    }

    @Test
    fun `on another day a new block starts at nine`() {
        val now = at("2026-11-16", "14:02")
        assertEquals(time("09:00"), suggestedBlockStart(now, LocalDate.parse("2026-11-18"), KOLKATA))
    }

    @Test
    fun `late at night the suggestion stays where a block still fits`() {
        assertEquals(time("23:45"), suggestedBlockStart(at("2026-11-16", "23:52"), day, KOLKATA))
    }

    @Test
    fun `instants follow daylight saving`() {
        // 2026-03-29 in Berlin: 02:00-03:00 does not exist; 23:00 to midnight is still one hour.
        val dstDay = LocalDate.parse("2026-03-29")
        val start = blockStartMs(dstDay, time("23:00"), BERLIN)
        assertEquals(3_600_000L, blockEndMs(dstDay, LocalTime.MIDNIGHT, BERLIN) - start)
    }
}
