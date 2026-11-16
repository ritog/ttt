/**
 * ReportBuilder — makes the six report sheets from the app's records (SPEC 11.2), in order:
 * Summary, Tasks, Blocks, Sessions, Daily, Tags. Plain tables only; no file or Android code.
 *
 * Depends on: ReportContext and the sheet files (SummarySheet, PlanSheets, ActivitySheets), core/time/Clock.
 * Must not: show ids anywhere (SPEC 11.1). Names, local times and plain words only.
 *
 * Times are shown in the device's zone at export time. Which records each sheet takes from the
 * range is described in the sheet files and in docs/DATA.md.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.time.Clock

fun buildReport(data: ReportData, range: ReportRange, clock: Clock): List<ReportTable> {
    val context = ReportContext(data, range, clock.nowMs(), clock.zone())
    return listOf(
        summarySheet(context),
        tasksSheet(context),
        blocksSheet(context),
        sessionsSheet(context),
        dailySheet(context),
        tagsSheet(context),
    )
}

/** "ttt-report-2026-11-16.xlsx" for a report made on [date]. */
fun reportFileName(date: java.time.LocalDate): String = "ttt-report-$date.xlsx"
