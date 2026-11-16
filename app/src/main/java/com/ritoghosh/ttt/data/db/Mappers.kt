/**
 * Mappers — converts between database rows (Entities.kt) and core models, in one place.
 *
 * Depends on: Entities.kt, Daos.kt (TaskWithTagNames), core/model.
 * Must not: apply business rules; it only copies fields.
 */
package com.ritoghosh.ttt.data.db

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.model.normalizeTags

/** Separator GROUP_CONCAT uses by default. */
private const val TAG_NAMES_SEPARATOR = ","

fun TaskWithTagNames.toTask(): Task = Task(
    id = task.id,
    title = task.title,
    tags = normalizeTags(tagNames?.split(TAG_NAMES_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()),
    isDone = task.isDone,
    createdAtMs = task.createdAt,
    doneAtMs = task.doneAt,
    note = task.note,
)

/** The task row only; tags are stored separately in task_tag. */
fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    isDone = isDone,
    createdAt = createdAtMs,
    doneAt = doneAtMs,
    note = note,
)

fun BlockEntity.toBlock(): Block = Block(id = id, taskId = taskId, startMs = startAt, endMs = endAt)

fun Block.toEntity(): BlockEntity = BlockEntity(id = id, taskId = taskId, startAt = startMs, endAt = endMs)

fun EntryEntity.toEntry(): Entry = Entry(
    id = id,
    taskId = taskId,
    startedAtMs = startedAt,
    endedAtMs = endedAt,
    pausedMs = pausedMs,
    note = note,
)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    taskId = taskId,
    startedAt = startedAtMs,
    endedAt = endedAtMs,
    pausedMs = pausedMs,
    note = note,
)

/**
 * Unknown phase or status text (a damaged or hand-edited file) reads as IDLE rather than
 * crashing at start: the timer is the one thing that must always open.
 */
fun TimerStateEntity.toTimerState(): TimerState {
    val knownPhase = TimerPhase.entries.firstOrNull { it.name == phase }
    val knownStatus = TimerStatus.entries.firstOrNull { it.name == status }
    if (knownPhase == null || knownStatus == null) return TimerState.IDLE
    return TimerState(
        phase = knownPhase,
        status = knownStatus,
        taskId = taskId,
        phaseLengthMs = phaseLengthMs,
        accumulatedMs = accumulatedMs,
        runningSinceMs = runningSinceMs,
        pausedSinceMs = pausedSinceMs,
        sessionStartedAtMs = sessionStartedAtMs,
        plannedEndReachedAtMs = plannedEndReachedAtMs,
        completedWorkPhases = completedWorkPhases,
        notePromptEntryId = notePromptEntryId,
    )
}

fun TimerState.toEntity(): TimerStateEntity = TimerStateEntity(
    id = TIMER_STATE_ROW_ID,
    phase = phase.name,
    status = status.name,
    taskId = taskId,
    phaseLengthMs = phaseLengthMs,
    accumulatedMs = accumulatedMs,
    runningSinceMs = runningSinceMs,
    pausedSinceMs = pausedSinceMs,
    sessionStartedAtMs = sessionStartedAtMs,
    plannedEndReachedAtMs = plannedEndReachedAtMs,
    completedWorkPhases = completedWorkPhases,
    notePromptEntryId = notePromptEntryId,
)
