/**
 * ReportFixture — a small, readable set of tasks, blocks and sessions for the report tests,
 * with "now" fixed at Monday 2026-11-16 12:00 in Kolkata.
 *
 * Depends on: core/model, core/testing/TestTime.kt.
 * Must not: grow into a second app; each record is here because a test reads it.
 *
 *   Write chapter (#book #writing, open, note "draft")
 *     B1 Tue 10 Nov 14:00–16:00 — E1 14:10–15:40, 10 min paused (80 min, in block)
 *                                  E2 16:10–16:40 (30 min, continued after B1)
 *     B2 Mon 16 Nov 11:00–13:00 — E5 11:00–11:45 (45 min, in block; B2 is in progress at noon)
 *     B3 Wed 18 Nov 09:00–10:00 — upcoming
 *   Read paper (#research, done Tue 10 Nov 18:00)
 *     B4 Sun 25 Oct 10:00–11:00 — E3 10:00–10:50 (50 min, in block; before November)
 *                                  E4 Mon 9 Nov 20:00–20:25 (25 min, unplanned)
 *   Call bank (no tags, open, never worked)
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.testing.FakeClock
import com.ritoghosh.ttt.core.testing.at
import com.ritoghosh.ttt.core.testing.minutes
import java.time.LocalDate

internal const val WRITE = 1L
internal const val READ = 2L
internal const val CALL = 3L

internal val NOW: Long = at("2026-11-16", "12:00")

internal val NOVEMBER = ReportRange.Days(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))

internal fun fixtureClock() = FakeClock(NOW)

internal val FIXTURE = ReportData(
    tasks = listOf(
        Task(WRITE, "Write chapter", listOf("book", "writing"), false, at("2026-11-01", "09:00"), null, "draft"),
        Task(READ, "Read paper", listOf("research"), true, at("2026-10-20", "09:00"), at("2026-11-10", "18:00"), ""),
        Task(CALL, "Call bank", emptyList(), false, at("2026-11-15", "09:00"), null, ""),
    ),
    blocks = listOf(
        Block(1, WRITE, at("2026-11-10", "14:00"), at("2026-11-10", "16:00")),
        Block(2, WRITE, at("2026-11-16", "11:00"), at("2026-11-16", "13:00")),
        Block(3, WRITE, at("2026-11-18", "09:00"), at("2026-11-18", "10:00")),
        Block(4, READ, at("2026-10-25", "10:00"), at("2026-10-25", "11:00")),
    ),
    entries = listOf(
        Entry(1, WRITE, at("2026-11-10", "14:10"), at("2026-11-10", "15:40"), minutes(10), "intro"),
        Entry(2, WRITE, at("2026-11-10", "16:10"), at("2026-11-10", "16:40"), 0, ""),
        Entry(3, READ, at("2026-10-25", "10:00"), at("2026-10-25", "10:50"), 0, ""),
        Entry(4, READ, at("2026-11-09", "20:00"), at("2026-11-09", "20:25"), 0, ""),
        Entry(5, WRITE, at("2026-11-16", "11:00"), at("2026-11-16", "11:45"), 0, ""),
    ),
)

/** Cell shorthands so expected rows read like the sheet. */
internal fun t(value: String): Cell = Cell.Text(value)

internal fun n(value: Int): Cell = Cell.Number(value.toDouble())

internal fun h(value: Double): Cell = Cell.Number(value, decimals = 1)

internal fun d(value: String): Cell = Cell.Date(LocalDate.parse(value))

internal fun tm(value: String): Cell = Cell.Time(java.time.LocalTime.parse(value))

internal fun dt(value: String): Cell = Cell.DateTime(java.time.LocalDateTime.parse(value))

internal val E: Cell = Cell.Empty

internal fun List<ReportTable>.sheet(title: String): ReportTable = first { it.title == title }

/** The Summary's value for [key] (the cell next to it). */
internal fun ReportTable.valueOf(key: String): Cell = rows.first { it.firstOrNull() == t(key) }[1]
