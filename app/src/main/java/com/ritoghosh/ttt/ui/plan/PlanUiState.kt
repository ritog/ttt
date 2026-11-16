/**
 * PlanUiState — what the Timeblock (Plan) screen shows for one day, and the pure function that builds it.
 *
 * Depends on: core/model, core/time.
 * Must not: touch Android, resources or the database; buildPlanUiState is unit-tested on the JVM.
 */
package com.ritoghosh.ttt.ui.plan

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A block with what the timeline needs to draw it. Tags are the task's (blocks inherit them). */
data class PlannedBlock(
    val block: Block,
    val taskTitle: String,
    /** The task's first tag (alphabetical); tints the block. Null if the task has no tags. */
    val firstTag: String?,
)

data class PlanUiState(
    val isLoaded: Boolean = false,
    val date: LocalDate = LocalDate.of(1970, 1, 1), // placeholder until isLoaded
    val zone: ZoneId = ZoneOffset.UTC,
    val dayStartMs: Long = 0L,
    val dayEndMs: Long = 0L,
    val isToday: Boolean = false,
    /** Refreshed about once a minute, for the current-time line. */
    val nowMs: Long = 0L,
    val blocks: List<PlannedBlock> = emptyList(),
    val tags: List<String> = emptyList(),
    val activeTag: String? = null,
)

/**
 * Joins the day's blocks with their tasks and applies the tag filter (SPEC 6.5: only blocks
 * whose task has the tag). A block whose task is missing is skipped rather than shown blank.
 */
fun buildPlanUiState(
    date: LocalDate,
    zone: ZoneId,
    nowMs: Long,
    blocks: List<Block>,
    tasks: List<Task>,
    tags: List<String>,
    selectedTag: String?,
): PlanUiState {
    val activeTag = selectedTag?.takeIf { it in tags }
    val tasksById = tasks.associateBy { it.id }
    val planned = blocks.mapNotNull { block ->
        val task = tasksById[block.taskId] ?: return@mapNotNull null
        if (activeTag != null && activeTag !in task.tags) return@mapNotNull null
        PlannedBlock(block, task.title, task.tags.firstOrNull())
    }
    val day = dayRange(date, zone)
    return PlanUiState(
        isLoaded = true,
        date = date,
        zone = zone,
        dayStartMs = day.startMs,
        dayEndMs = day.endMs,
        isToday = date == localDateOf(nowMs, zone),
        nowMs = nowMs,
        blocks = planned.sortedBy { it.block.startMs },
        tags = tags,
        activeTag = activeTag,
    )
}
