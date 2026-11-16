/**
 * TimeFormatTest — the timer's clock text and duration totals (rounding happens only here, SPEC 6.6).
 *
 * Depends on: ui/components/TimeFormat.kt, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private val second = 1_000L
    private val minute = 60 * second

    @Test
    fun `a countdown rounds up so it starts at the full length and reaches zero only at the end`() {
        assertEquals("25:00", formatClock(25 * minute, roundUp = true))
        assertEquals("24:59", formatClock(25 * minute - 1_500, roundUp = true))
        assertEquals("00:01", formatClock(1, roundUp = true))
        assertEquals("00:00", formatClock(0, roundUp = true))
    }

    @Test
    fun `counting up rounds down`() {
        assertEquals("12:34", formatClock(12 * minute + 34 * second + 999))
    }

    @Test
    fun `an hour or more shows hours`() {
        assertEquals("1:05:09", formatClock(65 * minute + 9 * second))
    }

    @Test
    fun `durations are whole minutes, with hours when needed`() {
        assertEquals("0m", formatDuration(59 * second))
        assertEquals("25m", formatDuration(25 * minute))
        assertEquals("3h 35m", formatDuration(215 * minute + 59 * second))
    }
}
