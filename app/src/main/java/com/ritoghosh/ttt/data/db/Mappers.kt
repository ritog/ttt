/**
 * Mappers — converts between database rows (Entities.kt) and core models, in one place.
 *
 * Depends on: Entities.kt, Daos.kt (TaskWithTagNames), core/model.
 * Must not: apply business rules; it only copies fields.
 * More mappers (Block, Entry, TimerState) arrive with the milestones that use them.
 */
package com.ritoghosh.ttt.data.db

import com.ritoghosh.ttt.core.model.Task
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
