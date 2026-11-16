/**
 * DayRangeTest — local days, Monday weeks, months, and time ranges (SPEC 4.3).
 *
 * Depends on: core/time, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.time

import com.ritoghosh.ttt.core.testing.BERLIN
import com.ritoghosh.ttt.core.testing.KOLKATA
import com.ritoghosh.ttt.core.testing.at
import com.ritoghosh.ttt.core.testing.hours
import com.ritoghosh.ttt.core.testing.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayRangeTest {

    @Test
    fun `an instant belongs to its local date, not its UTC date`() {
        // 02:00 in Kolkata on the 17th is still the 16th in UTC.
        assertEquals(LocalDate.parse("2026-11-17"), localDateOf(at("2026-11-17", "02:00"), KOLKATA))
    }

    @Test
    fun `a day runs from local midnight to the next local midnight`() {
        val range = dayRange(LocalDate.parse("2026-11-16"), KOLKATA)
        assertEquals(at("2026-11-16", "00:00"), range.startMs)
        assertEquals(at("2026-11-17", "00:00"), range.endMs)
    }

    @Test
    fun `daylight saving days are 23 and 25 hours long`() {
        assertEquals(hours(23), dayRange(LocalDate.parse("2026-03-29"), BERLIN).durationMs)
        assertEquals(hours(25), dayRange(LocalDate.parse("2026-10-25"), BERLIN).durationMs)
    }

    @Test
    fun `weeks start on Monday`() {
        // 2026-11-19 is a Thursday; its week is Mon 16th to Mon 23rd.
        val range = weekRange(LocalDate.parse("2026-11-19"), KOLKATA)
        assertEquals(at("2026-11-16", "00:00"), range.startMs)
        assertEquals(at("2026-11-23", "00:00"), range.endMs)
    }

    @Test
    fun `a Sunday belongs to the week that started the Monday before`() {
        val range = weekRange(LocalDate.parse("2026-11-22"), KOLKATA)
        assertEquals(at("2026-11-16", "00:00"), range.startMs)
    }

    @Test
    fun `a month runs from its first day to the first of the next month`() {
        val range = monthRange(LocalDate.parse("2026-12-15"), KOLKATA)
        assertEquals(at("2026-12-01", "00:00"), range.startMs)
        assertEquals(at("2027-01-01", "00:00"), range.endMs)
    }

    @Test
    fun `touching ranges do not overlap`() {
        val morning = TimeRange(at("2026-11-16", "09:00"), at("2026-11-16", "10:00"))
        val next = TimeRange(at("2026-11-16", "10:00"), at("2026-11-16", "11:00"))
        assertFalse(morning.overlaps(next))
        assertEquals(0L, morning.overlapMs(next))
    }

    @Test
    fun `overlap is the shared part`() {
        val block = TimeRange(at("2026-11-16", "14:00"), at("2026-11-16", "16:00"))
        val session = TimeRange(at("2026-11-16", "15:30"), at("2026-11-16", "16:45"))
        assertTrue(block.overlaps(session))
        assertEquals(minutes(30), block.overlapMs(session))
    }

    @Test
    fun `a range contains its start but not its end`() {
        val range = TimeRange(100L, 200L)
        assertTrue(range.contains(100L))
        assertFalse(range.contains(200L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a range cannot end before it starts`() {
        TimeRange(200L, 100L)
    }
}
