/**
 * TodoViewModel — the Todo list: quick add, check/uncheck, tag filter, Done section, messages.
 *
 * Depends on: data/repo/TaskRepository.kt, ui/nav/TagFilterHolder.kt, TodoUiState.kt.
 * Must not: touch the database directly or hold Android Context. The task editor sheet has
 * its own TaskEditorViewModel.
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.data.repo.AddTaskResult
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.ui.nav.TagFilterHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Keep collecting for 5 s after the screen stops, so a rotation does not restart the queries. */
private const val STOP_TIMEOUT_MS = 5_000L

class TodoViewModel(
    private val repository: TaskRepository,
    private val tagFilter: TagFilterHolder,
) : ViewModel() {

    /**
     * Text in the "Add a task…" field. Kept as Compose state rather than in the StateFlow,
     * because text fields must be updated synchronously or the cursor can jump (DECISIONS.md).
     */
    var addText by mutableStateOf("")
        private set

    private val isDoneExpanded = MutableStateFlow(false)
    private val message = MutableStateFlow<TodoMessage?>(null)

    val uiState: StateFlow<TodoUiState> = combine(
        repository.observeTasks(),
        repository.observeTagNames(),
        tagFilter.selectedTag,
        isDoneExpanded,
        message,
    ) { tasks, tags, selectedTag, doneExpanded, currentMessage ->
        buildTodoUiState(tasks, tags, selectedTag, doneExpanded, currentMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodoUiState())

    fun onAddTextChange(text: String) {
        addText = text
    }

    /** Enter in the add field. On success the field clears and keeps focus (SPEC 9.1). */
    fun onAddTask() {
        val text = addText
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.addTask(text)) {
                is AddTaskResult.Added -> {
                    // Only clear if the user has not started typing the next task meanwhile.
                    if (addText == text) addText = ""
                    if (result.rejectedTagWords.isNotEmpty()) {
                        message.value = TodoMessage.RejectedTagWords(result.rejectedTagWords)
                    }
                }
                is AddTaskResult.InvalidTitle -> message.value = TodoMessage.InvalidTitle(result.error)
            }
        }
    }

    fun onTaskCheckedChange(taskId: Long, isDone: Boolean) {
        viewModelScope.launch { repository.setDone(taskId, isDone) }
    }

    /** Tapping the selected tag again clears the filter. */
    fun onTagFilterClick(tag: String) {
        tagFilter.select(if (uiState.value.activeTag == tag) null else tag)
    }

    fun onClearTagFilter() {
        tagFilter.select(null)
    }

    fun onToggleDoneSection() {
        isDoneExpanded.update { !it }
    }

    fun onMessageShown() {
        message.value = null
    }
}
