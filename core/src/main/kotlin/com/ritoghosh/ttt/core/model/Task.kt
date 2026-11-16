/**
 * Task — something I want to do, with its tags and done state (SPEC 5.1, 6.1).
 *
 * Depends on: core/Constants.kt.
 * Must not: know about Room or Android. The database row lives in data/db/Entities.kt.
 */
package com.ritoghosh.ttt.core.model

import com.ritoghosh.ttt.core.TASK_TITLE_MAX_LENGTH

/** Id of a record not saved yet; the database assigns the real id on insert. */
const val NEW_ID = 0L

data class Task(
    val id: Long,
    /** Trimmed, 1..200 characters, with any #tags already removed (see validateTaskTitle). */
    val title: String,
    /** Distinct, lowercase, sorted alphabetically (see normalizeTags). The first one tints blocks. */
    val tags: List<String>,
    val isDone: Boolean,
    val createdAtMs: Long,
    /** When the task was marked done; null while it is open. */
    val doneAtMs: Long?,
    val note: String,
)

/** Marks the task done at [nowMs]. A task that is already done keeps its original done time. */
fun Task.markedDone(nowMs: Long): Task = if (isDone) this else copy(isDone = true, doneAtMs = nowMs)

/** Marks the task open again and clears its done time (SPEC 6.1). */
fun Task.markedOpen(): Task = copy(isDone = false, doneAtMs = null)

enum class TitleError { EMPTY, TOO_LONG }

sealed interface TitleResult {
    data class Valid(val title: String) : TitleResult
    data class Invalid(val error: TitleError) : TitleResult
}

/**
 * Trims [raw] and checks its length. Length counts characters as people see them
 * (Unicode code points), so an emoji counts as one, not two.
 * Tags must already be removed (parseQuickAdd does that).
 */
fun validateTaskTitle(raw: String): TitleResult {
    val title = raw.trim()
    val length = title.codePointCount(0, title.length)
    return when {
        length == 0 -> TitleResult.Invalid(TitleError.EMPTY)
        length > TASK_TITLE_MAX_LENGTH -> TitleResult.Invalid(TitleError.TOO_LONG)
        else -> TitleResult.Valid(title)
    }
}
