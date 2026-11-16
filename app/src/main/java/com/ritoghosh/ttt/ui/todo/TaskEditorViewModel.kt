/**
 * TaskEditorViewModel — the task editor sheet: title, tags, note, mark done, delete (SPEC 6.1, 6.2, 9.1).
 *
 * Depends on: data/repo/TaskRepository.kt, core/model (tag and title rules, TaskDeletion).
 * Must not: decide deletion rules itself; it shows what TaskRepository.checkDeletion says.
 *
 * Edits are kept here until Save. Dismissing the sheet discards them (DECISIONS.md).
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.model.TagError
import com.ritoghosh.ttt.core.model.TagResult
import com.ritoghosh.ttt.core.model.TaskDeletion
import com.ritoghosh.ttt.core.model.TitleError
import com.ritoghosh.ttt.core.model.TitleResult
import com.ritoghosh.ttt.core.model.normalizeTag
import com.ritoghosh.ttt.core.model.normalizeTags
import com.ritoghosh.ttt.data.repo.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the open editor shows besides its text fields. Null in the ViewModel means "closed". */
data class TaskEditorUiState(
    val taskId: Long,
    val isDone: Boolean,
    val tags: List<String>,
    val deletion: TaskDeletion,
    val titleError: TitleError? = null,
    val tagError: TagError? = null,
    val isConfirmingDelete: Boolean = false,
)

class TaskEditorViewModel(private val repository: TaskRepository) : ViewModel() {

    // Text fields are Compose state so typing is never delayed (see TodoViewModel.addText).
    var title by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var tagInput by mutableStateOf("")
        private set

    private val state = MutableStateFlow<TaskEditorUiState?>(null)
    val uiState: StateFlow<TaskEditorUiState?> = state.asStateFlow()

    fun open(taskId: Long) {
        viewModelScope.launch {
            val task = repository.getTask(taskId) ?: return@launch
            title = task.title
            note = task.note
            tagInput = ""
            state.value = TaskEditorUiState(task.id, task.isDone, task.tags, repository.checkDeletion(taskId))
        }
    }

    fun onTitleChange(text: String) {
        title = text
        updateOpen { it.copy(titleError = null) }
    }

    fun onNoteChange(text: String) {
        note = text
    }

    fun onTagInputChange(text: String) {
        tagInput = text
        updateOpen { it.copy(tagError = null) }
    }

    /** Adds the typed tag as a chip, or shows why it is not a valid tag. */
    fun onAddTag() {
        when (val result = normalizeTag(tagInput)) {
            is TagResult.Valid -> {
                updateOpen { it.copy(tags = normalizeTags(it.tags + result.name)) }
                tagInput = ""
            }
            is TagResult.Invalid -> updateOpen { it.copy(tagError = result.error) }
        }
    }

    fun onRemoveTag(tag: String) {
        updateOpen { it.copy(tags = it.tags - tag) }
    }

    fun onSave() {
        viewModelScope.launch { if (saveEdits()) close() }
    }

    /** Saves the edits too, so nothing typed is lost (SPEC 6.2: the only option for tasks with sessions). */
    fun onMarkDone() {
        val taskId = state.value?.taskId ?: return
        viewModelScope.launch {
            if (saveEdits()) {
                repository.setDone(taskId, true)
                close()
            }
        }
    }

    fun onDeleteClicked() {
        updateOpen { if (it.deletion is TaskDeletion.Allowed) it.copy(isConfirmingDelete = true) else it }
    }

    fun onDeleteCancelled() {
        updateOpen { it.copy(isConfirmingDelete = false) }
    }

    /** The repository checks the rules again; if things changed meanwhile, the sheet shows the new reason. */
    fun onDeleteConfirmed() {
        val taskId = state.value?.taskId ?: return
        viewModelScope.launch {
            when (val decision = repository.deleteTask(taskId)) {
                is TaskDeletion.Allowed -> close()
                else -> updateOpen { it.copy(deletion = decision, isConfirmingDelete = false) }
            }
        }
    }

    fun onDismiss() {
        close()
    }

    /**
     * Adds a half-typed tag first (people often forget to press Enter), then saves.
     * Returns false and shows the error if the tag or the title is not valid.
     */
    private suspend fun saveEdits(): Boolean {
        if (tagInput.isNotBlank()) {
            onAddTag()
            if (state.value?.tagError != null) return false
        }
        val editor = state.value ?: return false
        return when (val result = repository.updateTask(editor.taskId, title, editor.tags, note)) {
            is TitleResult.Valid -> true
            is TitleResult.Invalid -> {
                updateOpen { it.copy(titleError = result.error) }
                false
            }
        }
    }

    private fun updateOpen(change: (TaskEditorUiState) -> TaskEditorUiState) {
        state.update { current -> current?.let(change) }
    }

    private fun close() {
        state.value = null
    }
}
