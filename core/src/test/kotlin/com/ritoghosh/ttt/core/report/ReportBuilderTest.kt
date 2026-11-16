/**
 * ReportBuilderTest — every sheet's rows from the small fixture (SPEC 11.2), the range rules,
 * and rounding only at the edge (SPEC 6.6, 13).
 *
 * Depends on: ReportFixture.kt, core/testing/TestTime.kt.
 * Must not: depend on the device's language or zone; the fixture fixes both.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.testing.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {

    private val november = buildReport(FIXTURE, NOVEMBER, fixtureClock())

    @Test
    fun `six sheets in the spec's order`() {
        assertEquals(listOf("Summary", "Tasks", "Blocks", "Sessions", "Daily", "Tags"), november.map { it.title })
        assertEquals(false, november.sheet("Summary").isDataSheet)
        assertTrue(november.drop(1).all { it.isDataSheet })
    }

    @Test
    fun `summary states the range and the numbers`() {
        val summary = november.sheet("Summary")
        assertEquals(dt("2026-11-16T12:00"), summary.valueOf("Generated at"))
        assertEquals(t("Selected days"), summary.valueOf("Range"))
        assertEquals(d("2026-11-01"), summary.valueOf("From"))
        assertEquals(d("2026-11-30"), summary.valueOf("To"))
        assertEquals(n(2), summary.valueOf("Open tasks"))
        assertEquals(n(1), summary.valueOf("Done tasks"))
        assertEquals(h(3.0), summary.valueOf("Time tracked (hours)"))
        assertEquals(h(5.0), summary.valueOf("Time planned (hours)"))
        assertEquals(h(2.0), summary.valueOf("Planned minus tracked (hours)"))
        assertEquals(h(1.0), summary.valueOf("Upcoming planned time (hours)"))
        assertEquals(n(4), summary.valueOf("Sessions"))
        assertEquals(h(0.8), summary.valueOf("Tracked this week (hours)")) // 45 min
        assertEquals(h(2.3), summary.valueOf("Tracked last week (hours)")) // 135 min, half up
    }

    @Test
    fun `summary lists top tags and tasks and the overlap note`() {
        val rows = november.sheet("Summary").rows
        val tagsAt = rows.indexOf(listOf(Cell.Text("Top tags", bold = true), Cell.Text("Hours", bold = true)))
        assertEquals(
            listOf(listOf(t("book"), h(2.6)), listOf(t("writing"), h(2.6)), listOf(t("research"), h(0.4))),
            rows.subList(tagsAt + 1, tagsAt + 4),
        )
        val tasksAt = rows.indexOf(listOf(Cell.Text("Top tasks", bold = true), Cell.Text("Hours", bold = true)))
        assertEquals(
            listOf(listOf(t("Write chapter"), h(2.6)), listOf(t("Read paper"), h(0.4))),
            rows.subList(tasksAt + 1, tasksAt + 3),
        )
        assertEquals(listOf(t("Tag totals overlap; a task with two tags counts in both.")), rows.last())
    }

    @Test
    fun `tasks sheet lists every task, open first, counts in range`() {
        assertEquals(
            listOf(
                listOf(
                    t("Write chapter"), t("book, writing"), t("Open"), d("2026-11-01"), E, n(3), n(3), n(155),
                    d("2026-11-10"), d("2026-11-16"), dt("2026-11-18T09:00"), t("draft"),
                ),
                listOf(t("Call bank"), E, t("Open"), d("2026-11-15"), E, n(0), n(0), n(0), E, E, E, E),
                listOf(
                    t("Read paper"), t("research"), t("Done"), d("2026-10-20"), d("2026-11-10"), n(0), n(1), n(25),
                    d("2026-10-25"), d("2026-11-09"), E, E,
                ),
            ),
            november.sheet("Tasks").rows,
        )
    }

    @Test
    fun `blocks sheet shows plan vs actual per block`() {
        val tags = t("book, writing")
        assertEquals(
            listOf(
                listOf(
                    d("2026-11-10"), t("Tue"), tm("14:00"), tm("16:00"), n(120), t("Write chapter"), tags,
                    t("Past"), n(80), n(-40), n(30), t("No"),
                ),
                listOf(
                    d("2026-11-16"), t("Mon"), tm("11:00"), tm("13:00"), n(120), t("Write chapter"), tags,
                    t("In progress"), n(45), n(-75), n(0), t("No"),
                ),
                listOf(
                    d("2026-11-18"), t("Wed"), tm("09:00"), tm("10:00"), n(60), t("Write chapter"), tags,
                    t("Upcoming"), n(0), n(-60), n(0), t("No"),
                ),
            ),
            november.sheet("Blocks").rows,
        )
    }

    @Test
    fun `sessions sheet shows how each session relates to the plan`() {
        val tags = t("book, writing")
        assertEquals(
            listOf(
                listOf(
                    d("2026-11-09"), tm("20:00"), tm("20:25"), n(25), n(0), t("Read paper"), t("research"), E,
                    t("Unplanned"), E,
                ),
                listOf(
                    d("2026-11-10"), tm("14:10"), tm("15:40"), n(80), n(10), t("Write chapter"), tags, t("intro"),
                    t("In block"), t("14:00–16:00"),
                ),
                listOf(
                    d("2026-11-10"), tm("16:10"), tm("16:40"), n(30), n(0), t("Write chapter"), tags, E,
                    t("Continued after block"), t("14:00–16:00"),
                ),
                listOf(
                    d("2026-11-16"), tm("11:00"), tm("11:45"), n(45), n(0), t("Write chapter"), tags, E,
                    t("In block"), t("11:00–13:00"),
                ),
            ),
            november.sheet("Sessions").rows,
        )
    }

    @Test
    fun `daily sheet has one row per day with activity or a block`() {
        assertEquals(
            listOf(
                listOf(d("2026-11-09"), t("Mon"), n(0), n(25), n(25), n(1), n(1), n(0)),
                listOf(d("2026-11-10"), t("Tue"), n(120), n(110), n(-10), n(2), n(1), n(1)),
                listOf(d("2026-11-16"), t("Mon"), n(120), n(45), n(-75), n(1), n(1), n(0)),
                listOf(d("2026-11-18"), t("Wed"), n(60), n(0), n(-60), n(0), n(0), n(0)),
            ),
            november.sheet("Daily").rows,
        )
    }

    @Test
    fun `tags sheet counts per tag, with no tag last`() {
        assertEquals(
            listOf(
                listOf(t("book"), n(1), n(0), n(155), n(155), n(155), n(60)),
                listOf(t("writing"), n(1), n(0), n(155), n(155), n(155), n(60)),
                listOf(t("research"), n(0), n(1), n(25), n(25), n(75), n(0)),
                listOf(t("(no tag)"), n(1), n(0), n(0), n(0), n(0), n(0)),
            ),
            november.sheet("Tags").rows,
        )
    }

    @Test
    fun `all time includes everything`() {
        val all = buildReport(FIXTURE, ReportRange.AllTime, fixtureClock())
        val summary = all.sheet("Summary")
        assertEquals(t("All time"), summary.valueOf("Range"))
        assertEquals(h(3.8), summary.valueOf("Time tracked (hours)")) // 230 min
        assertEquals(n(5), summary.valueOf("Sessions"))
        assertEquals(4, all.sheet("Blocks").rows.size)
        assertEquals(d("2026-10-25"), all.sheet("Blocks").rows.first().first())
    }

    @Test
    fun `a session belongs to the range it starts in`() {
        val data = ReportData(
            tasks = listOf(Task(1, "Late", emptyList(), false, 0, null, "")),
            blocks = emptyList(),
            entries = listOf(
                Entry(1, 1, at("2026-10-31", "23:50"), at("2026-11-01", "00:20"), 0, ""),
                Entry(2, 1, at("2026-11-30", "23:50"), at("2026-12-01", "00:20"), 0, ""),
            ),
        )
        val sessions = buildReport(data, NOVEMBER, fixtureClock()).sheet("Sessions").rows
        assertEquals(listOf(d("2026-11-30")), sessions.map { it.first() })
    }

    @Test
    fun `minutes are rounded only when the cell is made`() {
        val data = ReportData(
            tasks = listOf(Task(1, "Short", emptyList(), false, 0, null, "")),
            blocks = emptyList(),
            entries = listOf(
                Entry(1, 1, at("2026-11-10", "10:00"), at("2026-11-10", "10:00") + 90_000, 0, ""),
                Entry(2, 1, at("2026-11-10", "11:00"), at("2026-11-10", "11:00") + 90_000, 0, ""),
            ),
        )
        val report = buildReport(data, NOVEMBER, fixtureClock())
        assertEquals(listOf(n(1), n(1)), report.sheet("Sessions").rows.map { it[3] }) // 1.5 min each
        assertEquals(n(3), report.sheet("Tasks").rows.single()[7]) // 3 min total, not 1 + 1
        assertEquals(n(3), report.sheet("Daily").rows.single()[3])
    }

    @Test
    fun `an empty app still gives six sheets`() {
        val report = buildReport(ReportData(emptyList(), emptyList(), emptyList()), ReportRange.AllTime, fixtureClock())
        assertEquals(6, report.size)
        assertTrue(report.drop(1).all { it.rows.isEmpty() })
        assertTrue(listOf(t("None yet")) in report.sheet("Summary").rows)
    }

    @Test
    fun `this month covers the whole calendar month`() {
        assertEquals(NOVEMBER, thisMonth(java.time.LocalDate.parse("2026-11-16")))
        assertEquals("ttt-report-2026-11-16.xlsx", reportFileName(java.time.LocalDate.parse("2026-11-16")))
    }
}
