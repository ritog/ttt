/**
 * LogUiState — what the Log shows: time per tag for a range, and sessions grouped by day (SPEC 9.4).
 *
 * Depends on: core/model, core/stats/TagTotals.kt, core/time.
 * Must not: touch Android, resources or the database; buildLogUiState is unit-tested on the JVM.
 */
package com.ritoghosh.ttt.ui.log

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.stats.TagTotal
import com.ritoghosh.ttt.core.stats.tagTotals
import com.ritoghosh.ttt.core.time.localDateOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class LogRange { TODAY, THIS_WEEK, THIS_MONTH }

data class LogEntryRow(val entry: Entry, val taskTitle: String)

/** One day: its date, its total worked time, and its sessions, newest first. */
data class LogDay(val date: LocalDate, val totalMs: Long, val entries: List<LogEntryRow>)

data class LogUiState(
    val isLoaded: Boolean = false,
    val range: LogRange = LogRange.TODAY,
    val zone: ZoneId = ZoneOffset.UTC,
    val tagTotals: List<TagTotal> = emptyList(),
    /** Newest day first. */
    val days: List<LogDay> = emptyList(),
    val tags: List<String> = emptyList(),
    val activeTag: String? = null,
    /** The session whose note is being edited, or null. */
    val editing: LogEntryRow? = null,
    val isConfirmingDelete: Boolean = false,
)

/** What the ViewModel keeps for itself. */
data class LogLocalState(val editingEntryId: Long? = null, val isConfirmingDelete: Boolean = false)

/**
 * Applies the tag filter (only sessions whose task has the tag, and totals of those), computes
 * tag totals, and groups sessions by the local day they start on (SPEC 4.3).
 */
fun buildLogUiState(
    range: LogRange,
    zone: ZoneId,
    entries: List<Entry>,
    tasks: List<Task>,
    tags: List<String>,
    selectedTag: String?,
    local: LogLocalState,
): LogUiState {
    val activeTag = selectedTag?.takeIf { it in tags }
    val tasksById = tasks.associateBy { it.id }
    val rows = entries.mapNotNull { entry ->
        tasksById[entry.taskId]?.let { task -> LogEntryRow(entry, task.title) }
    }
    val shown = rows.filter { row ->
        activeTag == null || activeTag in tasksById[row.entry.taskId]?.tags.orEmpty()
    }
    val days = shown
        .groupBy { localDateOf(it.entry.startedAtMs, zone) }
        .map { (date, dayRows) ->
            LogDay(date, dayRows.sumOf { it.entry.workedMs }, dayRows.sortedByDescending { it.entry.startedAtMs })
        }
        .sortedByDescending { it.date }
    return LogUiState(
        isLoaded = true,
        range = range,
        zone = zone,
        tagTotals = tagTotals(shown.map { it.entry }, tasks.associate { it.id to it.tags }),
        days = days,
        tags = tags,
        activeTag = activeTag,
        editing = rows.firstOrNull { it.entry.id == local.editingEntryId },
        isConfirmingDelete = local.isConfirmingDelete,
    )
}
