/**
 * ConstantsTest — pins the numbers from SPEC.md so a change to one is always deliberate.
 *
 * Depends on: core/Constants.kt, JUnit 4.
 * Must not: use Android classes (this module runs on the plain JVM).
 */
package com.ritoghosh.ttt.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantsTest {

    @Test
    fun `task and tag length limits match the spec`() {
        assertEquals(200, TASK_TITLE_MAX_LENGTH)
        assertEquals(30, TAG_MAX_LENGTH)
    }

    @Test
    fun `block rules match the spec`() {
        assertEquals(5, BLOCK_GRANULARITY_MINUTES)
        assertEquals(15, MIN_BLOCK_MINUTES)
        assertEquals(listOf(30, 60, 90, 120), BLOCK_DURATION_CHIPS_MINUTES)
    }

    @Test
    fun `the suggested new block length is one of the chips`() {
        assertEquals(true, DEFAULT_NEW_BLOCK_MINUTES in BLOCK_DURATION_CHIPS_MINUTES)
    }

    @Test
    fun `every block duration chip is a whole number of granularity steps`() {
        BLOCK_DURATION_CHIPS_MINUTES.forEach { chip ->
            assertEquals("chip $chip", 0, chip % BLOCK_GRANULARITY_MINUTES)
        }
    }

    @Test
    fun `timer and matching thresholds match the spec`() {
        assertEquals(30, CONTINUATION_GAP_MINUTES)
        assertEquals(60_000L, MIN_ENTRY_MS)
        assertEquals(60, LONG_OVERTIME_MINUTES)
        assertEquals(24, AUTO_BACKUP_INTERVAL_HOURS)
    }

    @Test
    fun `report limits match the spec`() {
        assertEquals(5, REPORT_TOP_COUNT)
        assertEquals(7, REPORT_SHORT_WINDOW_DAYS)
        assertEquals(30, REPORT_LONG_WINDOW_DAYS)
        assertEquals(32_000, REPORT_CELL_MAX_LENGTH)
        assertEquals(31, REPORT_SHEET_NAME_MAX_LENGTH)
    }
}
