/**
 * SummarySheet — the first sheet: when the report was made and what it covers, task counts,
 * tracked vs planned hours, this week vs last week, then the top 5 tags and tasks (SPEC 11.2).
 *
 * Depends on: ReportContext, Constants.kt, core/stats/TagTotals.kt, core/time (weekRange).
 * Must not: leave the range out; a partial report must never pass for the whole (SPEC 11.1).
 *
 * Task counts and "upcoming" are about now, not the range. Tracked, planned, sessions and the
 * top lists use the range. This week / last week are Monday-to-Sunday weeks around today.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.REPORT_TOP_COUNT
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.stats.tagTotals
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.time.weekRange

private const val OVERLAP_NOTE = "Tag totals overlap; a task with two tags counts in both."

internal fun summarySheet(context: ReportContext): ReportTable {
    val rows = mutableListOf<List<Cell>>()
    rows += listOf(text("Generated at"), context.dateTimeCell(context.nowMs))
    rows += rangeRows(context.range)
    rows += listOf(text("Open tasks"), whole(context.tasks.count { !it.isDone }))
    rows += listOf(text("Done tasks"), whole(context.tasks.count { it.isDone }))

    val trackedMs = context.entriesInRange.sumOf { it.workedMs }
    val plannedMs = context.blocksInRange.sumOf { it.endMs - it.startMs }
    val upcomingMs = context.allBlocks.filter { it.startMs > context.nowMs }.sumOf { it.endMs - it.startMs }
    rows += listOf(text("Time tracked (hours)"), hoursCell(trackedMs))
    rows += listOf(text("Time planned (hours)"), hoursCell(plannedMs))
    rows += listOf(text("Planned minus tracked (hours)"), hoursCell(plannedMs - trackedMs))
    rows += listOf(text("Upcoming planned time (hours)"), hoursCell(upcomingMs))
    rows += listOf(text("Sessions"), whole(context.entriesInRange.size))

    val thisWeek = weekRange(context.dateOf(context.nowMs), context.zone)
    val lastWeek = weekRange(context.dateOf(context.nowMs).minusWeeks(1), context.zone)
    rows += listOf(text("Tracked this week (hours)"), hoursCell(trackedIn(context.allEntries, thisWeek)))
    rows += listOf(text("Tracked last week (hours)"), hoursCell(trackedIn(context.allEntries, lastWeek)))

    rows += emptyList<Cell>()
    rows += topTags(context)
    rows += emptyList<Cell>()
    rows += topTasks(context)
    rows += emptyList<Cell>()
    rows += listOf(text(OVERLAP_NOTE))
    return ReportTable("Summary", listOf("Item", "Value"), rows, isDataSheet = false)
}

private fun rangeRows(range: ReportRange): List<List<Cell>> = when (range) {
    ReportRange.AllTime -> listOf(listOf(text("Range"), text("All time")))
    is ReportRange.Days -> listOf(
        listOf(text("Range"), text("Selected days")),
        listOf(text("From"), Cell.Date(range.first)),
        listOf(text("To"), Cell.Date(range.last)),
    )
}

private fun trackedIn(entries: List<Entry>, week: TimeRange): Long =
    entries.filter { week.contains(it.startedAtMs) }.sumOf { it.workedMs }

private fun topTags(context: ReportContext): List<List<Cell>> {
    val totals = tagTotals(context.entriesInRange, context.tagsByTask)
        .filter { it.tag != null && it.workedMs > 0 }
        .take(REPORT_TOP_COUNT)
    val rows = totals.map { listOf(text(it.tag.orEmpty()), hoursCell(it.workedMs)) }
    return listOf(listOf(boldText("Top tags"), boldText("Hours"))) + rows.ifEmpty { listOf(listOf(text("None yet"))) }
}

private fun topTasks(context: ReportContext): List<List<Cell>> {
    val totals = context.entriesInRange
        .groupBy { it.taskId }
        .map { (taskId, entries) -> taskId to entries.sumOf { it.workedMs } }
        .filter { (_, workedMs) -> workedMs > 0 }
        .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { context.titleOf(it.first) })
        .take(REPORT_TOP_COUNT)
    val rows = totals.map { (taskId, workedMs) -> listOf(text(context.titleOf(taskId)), hoursCell(workedMs)) }
    return listOf(listOf(boldText("Top tasks"), boldText("Hours"))) + rows.ifEmpty { listOf(listOf(text("None yet"))) }
}
