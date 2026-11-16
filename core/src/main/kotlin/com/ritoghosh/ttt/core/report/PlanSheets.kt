/**
 * PlanSheets — the Tasks and Blocks sheets (SPEC 11.2).
 *
 * Depends on: ReportContext, core/stats (WorkedTime).
 * Must not: leave any task out; Tasks lists every task, worked on or not, open or done.
 *
 * Tasks: counts and minutes use the range; created, done, first/last worked and next block do not.
 * Open tasks first (oldest first), then done tasks (most recently done first).
 * Blocks: blocks starting in the range, past, current and future, oldest first.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.stats.continuedAfterBlockMs
import com.ritoghosh.ttt.core.stats.workedInBlockMs

private val TASKS_HEADER = listOf(
    "Task", "Tags", "Status", "Created", "Done on", "Blocks planned", "Sessions",
    "Minutes worked", "First worked", "Last worked", "Next block", "Note",
)

private val BLOCKS_HEADER = listOf(
    "Date", "Weekday", "Start", "End", "Planned min", "Task", "Tags", "Status",
    "Worked in window (min)", "Difference (min)", "Continued after end (min)", "Task done?",
)

internal fun tasksSheet(context: ReportContext): ReportTable {
    val open = context.tasks.filter { !it.isDone }
        .sortedWith(compareBy<Task> { it.createdAtMs }.thenBy { it.title })
    val done = context.tasks.filter { it.isDone }
        .sortedWith(compareByDescending<Task> { it.doneAtMs }.thenBy { it.title })
    return ReportTable("Tasks", TASKS_HEADER, (open + done).map { taskRow(context, it) })
}

private fun taskRow(context: ReportContext, task: Task): List<Cell> {
    val allEntries = context.entriesByTask[task.id].orEmpty()
    val entriesInRange = allEntries.filter { context.inRange(it.startedAtMs) }
    val blocks = context.blocksByTask[task.id].orEmpty()
    val nextBlockStart = blocks.filter { it.startMs > context.nowMs }.minOfOrNull { it.startMs }
    return listOf(
        text(task.title),
        text(context.tagsTextOf(task.id)),
        Cell.Text(if (task.isDone) "Done" else "Open"),
        context.dateCell(task.createdAtMs),
        cellOrEmpty(task.doneAtMs) { context.dateCell(it) },
        whole(blocks.count { context.inRange(it.startMs) }),
        whole(entriesInRange.size),
        minutesCell(entriesInRange.sumOf { it.workedMs }),
        cellOrEmpty(allEntries.minOfOrNull { it.startedAtMs }) { context.dateCell(it) },
        cellOrEmpty(allEntries.maxOfOrNull { it.startedAtMs }) { context.dateCell(it) },
        cellOrEmpty(nextBlockStart) { context.dateTimeCell(it) },
        text(task.note),
    )
}

internal fun blocksSheet(context: ReportContext): ReportTable =
    ReportTable("Blocks", BLOCKS_HEADER, context.blocksInRange.map { blockRow(context, it) })

private fun blockRow(context: ReportContext, block: Block): List<Cell> {
    val taskEntries = context.entriesByTask[block.taskId].orEmpty()
    val plannedMin = wholeMinutes(block.endMs - block.startMs)
    val workedMin = wholeMinutes(workedInBlockMs(block, taskEntries))
    val continuedMs = continuedAfterBlockMs(block, taskEntries, context.matches)
    val date = context.dateOf(block.startMs)
    return listOf(
        Cell.Date(date),
        weekdayCell(date),
        context.timeCell(block.startMs),
        context.timeCell(block.endMs),
        whole(plannedMin),
        text(context.titleOf(block.taskId)),
        text(context.tagsTextOf(block.taskId)),
        Cell.Text(blockStatus(block, context.nowMs)),
        whole(workedMin),
        whole(workedMin - plannedMin),
        minutesCell(continuedMs),
        yesNo(context.isTaskDone(block.taskId)),
    )
}

private fun blockStatus(block: Block, nowMs: Long): String = when {
    nowMs < block.startMs -> "Upcoming"
    nowMs < block.endMs -> "In progress"
    else -> "Past"
}
