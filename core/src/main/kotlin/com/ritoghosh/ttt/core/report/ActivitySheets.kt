/**
 * ActivitySheets — the Sessions, Daily and Tags sheets (SPEC 11.2).
 *
 * Depends on: ReportContext, Constants.kt, core/stats (PlanMatch).
 * Must not: count a session on two days; a session belongs to the day it started (as in the Log).
 *
 * Sessions: sessions starting in the range, oldest first, with how each relates to the plan.
 * Daily: one row per day in the range with a session, a block or a completed task.
 * Tags: every tag in use (plus "(no tag)" if any task has none); minutes use the range, the
 * 7- and 30-day columns count back from now, "upcoming" is blocks starting after now.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.REPORT_LONG_WINDOW_DAYS
import com.ritoghosh.ttt.core.REPORT_SHORT_WINDOW_DAYS
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.stats.PlanMatch
import java.time.LocalDate

private const val MS_PER_DAY = 86_400_000L
private const val NO_TAG = "(no tag)"

private val SESSIONS_HEADER = listOf(
    "Date", "Start", "End", "Worked min", "Paused min", "Task", "Tags", "Note", "Plan status", "Block",
)

private val DAILY_HEADER = listOf(
    "Date", "Weekday", "Planned min", "Tracked min", "Difference", "Sessions", "Tasks worked on", "Tasks completed",
)

private val TAGS_HEADER = listOf(
    "Tag", "Open tasks", "Done tasks", "Minutes worked", "Last 7 days (min)", "Last 30 days (min)",
    "Upcoming planned (min)",
)

internal fun sessionsSheet(context: ReportContext): ReportTable =
    ReportTable("Sessions", SESSIONS_HEADER, context.entriesInRange.map { sessionRow(context, it) })

private fun sessionRow(context: ReportContext, entry: Entry): List<Cell> {
    val match = context.matches[entry.id] ?: PlanMatch.Unplanned
    val (status, blockId) = when (match) {
        is PlanMatch.InBlock -> "In block" to match.blockId
        is PlanMatch.ContinuedAfter -> "Continued after block" to match.blockId
        PlanMatch.Unplanned -> "Unplanned" to null
    }
    return listOf(
        context.dateCell(entry.startedAtMs),
        context.timeCell(entry.startedAtMs),
        context.timeCell(entry.endedAtMs),
        minutesCell(entry.workedMs),
        minutesCell(entry.pausedMs),
        text(context.titleOf(entry.taskId)),
        text(context.tagsTextOf(entry.taskId)),
        text(entry.note),
        Cell.Text(status),
        cellOrEmpty(blockId?.let(context::block)) { text(context.blockLabel(it)) },
    )
}

internal fun dailySheet(context: ReportContext): ReportTable {
    val entriesByDay = context.entriesInRange.groupBy { context.dateOf(it.startedAtMs) }
    val plannedByDay = context.blocksInRange.groupBy { context.dateOf(it.startMs) }
        .mapValues { (_, blocks) -> blocks.sumOf { it.endMs - it.startMs } }
    val completedByDay = context.tasks.mapNotNull { it.doneAtMs }
        .filter { context.inRange(it) }
        .groupingBy { context.dateOf(it) }
        .eachCount()
    val days = (entriesByDay.keys + plannedByDay.keys + completedByDay.keys).sorted()
    val rows = days.map { day ->
        val entries = entriesByDay[day].orEmpty()
        dailyRow(day, entries, plannedByDay[day] ?: 0L, completedByDay[day] ?: 0)
    }
    return ReportTable("Daily", DAILY_HEADER, rows)
}

private fun dailyRow(day: LocalDate, entries: List<Entry>, plannedMs: Long, completed: Int): List<Cell> {
    val plannedMin = wholeMinutes(plannedMs)
    val trackedMin = wholeMinutes(entries.sumOf { it.workedMs })
    return listOf(
        Cell.Date(day),
        weekdayCell(day),
        whole(plannedMin),
        whole(trackedMin),
        whole(trackedMin - plannedMin),
        whole(entries.size),
        whole(entries.map { it.taskId }.distinct().size),
        whole(completed),
    )
}

internal fun tagsSheet(context: ReportContext): ReportTable {
    val tags: List<String?> = context.tasks.flatMap { it.tags }.distinct() +
        (if (context.tasks.any { it.tags.isEmpty() }) listOf(null) else emptyList())
    val rows = tags.map { tag -> tag to tagRow(context, tag) }
        .sortedWith(
            compareBy<Pair<String?, TagRow>> { it.first == null }
                .thenByDescending { it.second.workedMs }
                .thenBy { it.first },
        )
        .map { (tag, row) -> row.cells(tag) }
    return ReportTable("Tags", TAGS_HEADER, rows)
}

/** The numbers for one tag; [workedMs] is kept in ms for sorting. */
private class TagRow(
    val openTasks: Int,
    val doneTasks: Int,
    val workedMs: Long,
    val last7Ms: Long,
    val last30Ms: Long,
    val upcomingMs: Long,
) {
    fun cells(tag: String?): List<Cell> = listOf(
        Cell.Text(tag ?: NO_TAG),
        whole(openTasks),
        whole(doneTasks),
        minutesCell(workedMs),
        minutesCell(last7Ms),
        minutesCell(last30Ms),
        minutesCell(upcomingMs),
    )
}

private fun tagRow(context: ReportContext, tag: String?): TagRow {
    val tasks = context.tasks.filter { if (tag == null) it.tags.isEmpty() else tag in it.tags }
    val taskIds = tasks.map { it.id }.toSet()
    val entries = context.allEntries.filter { it.taskId in taskIds }
    fun workedSince(daysBack: Int): Long {
        val fromMs = context.nowMs - daysBack * MS_PER_DAY
        return entries.filter { it.startedAtMs in fromMs until context.nowMs }.sumOf { it.workedMs }
    }
    return TagRow(
        openTasks = tasks.count { !it.isDone },
        doneTasks = tasks.count { it.isDone },
        workedMs = entries.filter { context.inRange(it.startedAtMs) }.sumOf { it.workedMs },
        last7Ms = workedSince(REPORT_SHORT_WINDOW_DAYS),
        last30Ms = workedSince(REPORT_LONG_WINDOW_DAYS),
        upcomingMs = context.allBlocks
            .filter { it.taskId in taskIds && it.startMs > context.nowMs }
            .sumOf { it.endMs - it.startMs },
    )
}
