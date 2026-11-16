/**
 * LogUiStateTest — grouping by start day (newest first), day totals, tag totals, and the tag filter (SPEC 9.4).
 *
 * Depends on: ui/log/LogUiState.kt, core/model, core/stats, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.log

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.stats.TagTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogUiStateTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val minute = 60_000L
    private fun at(dateTime: String) = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private val write = Task(1, "Write", listOf("book"), isDone = false, createdAtMs = 0L, doneAtMs = null, note = "")
    private val shop = Task(2, "Shop", listOf("home"), isDone = false, createdAtMs = 0L, doneAtMs = null, note = "")

    private val entries = listOf(
        Entry(10, 1, at("2026-11-16T09:00"), at("2026-11-16T09:30"), pausedMs = 0L, note = ""),
        Entry(11, 2, at("2026-11-16T18:00"), at("2026-11-16T18:20"), pausedMs = 0L, note = "milk"),
        // Starts on the 17th just before midnight and ends on the 18th: it belongs to the 17th.
        Entry(12, 1, at("2026-11-17T23:50"), at("2026-11-18T00:15"), pausedMs = 0L, note = ""),
    )

    private fun build(selectedTag: String? = null, local: LogLocalState = LogLocalState()) =
        buildLogUiState(
            LogRange.THIS_WEEK, zone, entries, listOf(write, shop), listOf("book", "home"), selectedTag, local,
        )

    @Test
    fun `sessions are grouped by the day they start, newest day and newest session first`() {
        val days = build().days
        assertEquals(listOf(LocalDate.parse("2026-11-17"), LocalDate.parse("2026-11-16")), days.map { it.date })
        assertEquals(listOf(11L, 10L), days[1].entries.map { it.entry.id })
    }

    @Test
    fun `a session crossing midnight counts entirely toward the day it starts`() {
        val firstDay = build().days.first()
        assertEquals(LocalDate.parse("2026-11-17"), firstDay.date)
        assertEquals(25 * minute, firstDay.totalMs)
    }

    @Test
    fun `tag totals cover the range, largest first`() {
        assertEquals(listOf(TagTotal("book", 55 * minute), TagTotal("home", 20 * minute)), build().tagTotals)
    }

    @Test
    fun `the tag filter narrows sessions and totals`() {
        val state = build(selectedTag = "home")
        assertEquals(listOf(11L), state.days.flatMap { it.entries }.map { it.entry.id })
        assertEquals(listOf(TagTotal("home", 20 * minute)), state.tagTotals)
    }

    @Test
    fun `the session being edited is found by id`() {
        assertEquals("Shop", build(local = LogLocalState(editingEntryId = 11)).editing?.taskTitle)
        assertNull(build().editing)
    }
}
