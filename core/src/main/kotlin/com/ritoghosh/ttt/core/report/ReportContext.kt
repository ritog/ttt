/**
 * ReportContext — everything the six sheets share, worked out once per report: lookups by id,
 * what falls in the range, how sessions match blocks, and how values become cells.
 *
 * Depends on: core/model, core/stats (EntryBlockMatcher), core/time, ReportRange, ReportTable.
 * Must not: round anything except when making a cell (SPEC 6.6: math in ms, minutes only at the edge).
 *
 * Rounding: minutes are whole and rounded down, the same as the app's screens, so the report and
 * the Log agree. Hours are rounded to one decimal, half up. Differences are taken between the
 * rounded minute cells, so each row adds up as written.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.stats.PlanMatch
import com.ritoghosh.ttt.core.stats.matchEntriesToBlocks
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.time.zonedDateTimeOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

internal const val MS_PER_MINUTE = 60_000L
private const val MS_PER_TENTH_HOUR = 360_000.0
private const val TENTHS_PER_HOUR = 10.0

/** The app's records, as loaded from the database. */
data class ReportData(
    val tasks: List<Task>,
    val blocks: List<Block>,
    val entries: List<Entry>,
)

internal class ReportContext(
    data: ReportData,
    val range: ReportRange,
    val nowMs: Long,
    val zone: ZoneId,
) {
    val tasks: List<Task> = data.tasks
    val allBlocks: List<Block> = data.blocks
    val allEntries: List<Entry> = data.entries
    val tagsByTask: Map<Long, List<String>> = data.tasks.associate { it.id to it.tags }
    private val tasksById: Map<Long, Task> = data.tasks.associateBy { it.id }
    private val blocksById: Map<Long, Block> = data.blocks.associateBy { it.id }
    val entriesByTask: Map<Long, List<Entry>> = data.entries.groupBy { it.taskId }
    val blocksByTask: Map<Long, List<Block>> = data.blocks.groupBy { it.taskId }

    private val window = range.timeRange(zone)

    /** Sessions starting in the range, oldest first. */
    val entriesInRange: List<Entry> = data.entries.filter { inRange(it.startedAtMs) }.sortedBy { it.startedAtMs }

    /** Blocks starting in the range, oldest first. */
    val blocksInRange: List<Block> = data.blocks.filter { inRange(it.startMs) }.sortedBy { it.startMs }

    /** Matched over all records, so the range's edges never change how a session is classified. */
    val matches: Map<Long, PlanMatch> = matchEntriesToBlocks(data.entries, data.blocks)

    fun inRange(instantMs: Long): Boolean = window?.contains(instantMs) ?: true

    fun titleOf(taskId: Long): String = tasksById[taskId]?.title.orEmpty()

    fun tagsTextOf(taskId: Long): String = tagsByTask[taskId].orEmpty().joinToString(", ")

    fun isTaskDone(taskId: Long): Boolean = tasksById[taskId]?.isDone == true

    fun block(blockId: Long): Block? = blocksById[blockId]

    fun dateOf(instantMs: Long): LocalDate = localDateOf(instantMs, zone)

    fun dateCell(instantMs: Long): Cell = Cell.Date(dateOf(instantMs))

    fun timeCell(instantMs: Long): Cell =
        Cell.Time(zonedDateTimeOf(instantMs, zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES))

    fun dateTimeCell(instantMs: Long): Cell =
        Cell.DateTime(zonedDateTimeOf(instantMs, zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES))

    /** "14:00–16:00" for a block, as on the Timeblock screen. */
    fun blockLabel(block: Block): String {
        val start = zonedDateTimeOf(block.startMs, zone).toLocalTime()
        val end = zonedDateTimeOf(block.endMs, zone).toLocalTime()
        return "%02d:%02d–%02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
    }
}

/** "Mon". The report is in English, like the app. */
internal fun weekdayCell(date: LocalDate): Cell =
    Cell.Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))

internal fun text(value: String): Cell = if (value.isEmpty()) Cell.Empty else Cell.Text(value)

internal fun boldText(value: String): Cell = Cell.Text(value, bold = true)

internal fun whole(value: Long): Cell = Cell.Number(value.toDouble())

internal fun whole(value: Int): Cell = whole(value.toLong())

/** Whole minutes, rounded down. Only for durations (never negative). */
internal fun wholeMinutes(durationMs: Long): Long = durationMs / MS_PER_MINUTE

internal fun minutesCell(durationMs: Long): Cell = whole(wholeMinutes(durationMs))

/** Hours with one decimal, rounded half up (may be negative, e.g. planned minus tracked). */
internal fun hoursCell(durationMs: Long): Cell =
    Cell.Number(Math.round(durationMs / MS_PER_TENTH_HOUR) / TENTHS_PER_HOUR, decimals = 1)

internal fun yesNo(value: Boolean): Cell = Cell.Text(if (value) "Yes" else "No")

/** A cell from a nullable value, empty when there is nothing to show. */
internal inline fun <T> cellOrEmpty(value: T?, toCell: (T) -> Cell): Cell = value?.let(toCell) ?: Cell.Empty
