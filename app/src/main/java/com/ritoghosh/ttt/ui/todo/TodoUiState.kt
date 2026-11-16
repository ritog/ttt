/**
 * TodoUiState — everything the Todo screen shows, and the pure function that builds it.
 *
 * Depends on: core/model.
 * Must not: touch Android, resources or the database; buildTodoUiState is unit-tested on the JVM.
 */
package com.ritoghosh.ttt.ui.todo

import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TitleError

/** A one-time message for the snackbar. Turned into text by the screen (no resources here). */
sealed interface TodoMessage {
    data class InvalidTitle(val error: TitleError) : TodoMessage
    data class RejectedTagWords(val words: List<String>) : TodoMessage
}

data class TodoUiState(
    /** False until the first database read arrives, so the empty state does not flash. */
    val isLoaded: Boolean = false,
    /** True if any task exists at all (ignoring the filter); decides the empty state. */
    val hasAnyTask: Boolean = false,
    /** Open tasks matching the filter, newest first. */
    val openTasks: List<Task> = emptyList(),
    /** Done tasks matching the filter, most recently completed first. */
    val doneTasks: List<Task> = emptyList(),
    /** Every tag in use, alphabetically (the filter chips). */
    val tags: List<String> = emptyList(),
    /** The active filter, or null. */
    val activeTag: String? = null,
    /** The Done section is collapsed by default (SPEC 9.1). */
    val isDoneExpanded: Boolean = false,
    val message: TodoMessage? = null,
)

/**
 * Applies the tag filter and the two sort orders of SPEC 9.1.
 * A selected tag that no longer exists (its last task was deleted) is treated as no filter.
 */
fun buildTodoUiState(
    tasks: List<Task>,
    tags: List<String>,
    selectedTag: String?,
    isDoneExpanded: Boolean,
    message: TodoMessage?,
): TodoUiState {
    val activeTag = selectedTag?.takeIf { it in tags }
    val visible = if (activeTag == null) tasks else tasks.filter { activeTag in it.tags }
    return TodoUiState(
        isLoaded = true,
        hasAnyTask = tasks.isNotEmpty(),
        openTasks = visible.filter { !it.isDone }.sortedByDescending { it.createdAtMs },
        doneTasks = visible.filter { it.isDone }.sortedByDescending { it.doneAtMs ?: it.createdAtMs },
        tags = tags,
        activeTag = activeTag,
        isDoneExpanded = isDoneExpanded,
        message = message,
    )
}
