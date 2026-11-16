/**
 * LogViewModel — the Log: pick a range, see time per tag and sessions by day, edit a note,
 * delete a session (SPEC 6.4, 9.4).
 *
 * Depends on: data/repo (EntryRepository, TaskRepository), core/time, ui/nav/TagFilterHolder.kt.
 * Must not: change session times (out of scope in v1).
 */
package com.ritoghosh.ttt.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.time.monthRange
import com.ritoghosh.ttt.core.time.weekRange
import com.ritoghosh.ttt.data.repo.EntryRepository
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.ui.nav.TagFilterHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

class LogViewModel(
    private val entryRepository: EntryRepository,
    taskRepository: TaskRepository,
    private val clock: Clock,
    private val tagFilter: TagFilterHolder,
) : ViewModel() {

    /** The note being edited (Compose state so typing is never delayed). */
    var noteText by mutableStateOf("")
        private set

    private val range = MutableStateFlow(LogRange.TODAY)
    private val local = MutableStateFlow(LogLocalState())

    @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest: switch the query when the range changes
    private val entries = range.flatMapLatest { entryRepository.observeEntriesStartedIn(timeRangeFor(it)) }

    private val tagsAndFilter = combine(
        taskRepository.observeTagNames(),
        tagFilter.selectedTag,
    ) { tags, selected -> tags to selected }

    val uiState: StateFlow<LogUiState> = combine(
        range,
        entries,
        taskRepository.observeTasks(),
        tagsAndFilter,
        local,
    ) { selectedRange, rangeEntries, tasks, (tags, selectedTag), localState ->
        buildLogUiState(selectedRange, clock.zone(), rangeEntries, tasks, tags, selectedTag, localState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LogUiState())

    fun onRangeSelected(selected: LogRange) {
        range.value = selected
    }

    fun onEntryClick(row: LogEntryRow) {
        noteText = row.entry.note
        local.value = LogLocalState(editingEntryId = row.entry.id)
    }

    fun onNoteTextChange(text: String) {
        noteText = text
    }

    fun onSaveNote() {
        val entryId = local.value.editingEntryId ?: return
        val text = noteText
        closeEditor()
        viewModelScope.launch { entryRepository.updateNote(entryId, text) }
    }

    fun onDeleteClicked() {
        local.update { it.copy(isConfirmingDelete = true) }
    }

    fun onDeleteCancelled() {
        local.update { it.copy(isConfirmingDelete = false) }
    }

    fun onDeleteConfirmed() {
        val entryId = local.value.editingEntryId ?: return
        closeEditor()
        viewModelScope.launch { entryRepository.deleteEntry(entryId) }
    }

    fun closeEditor() {
        local.value = LogLocalState()
    }

    fun onTagFilterClick(tag: String) {
        tagFilter.select(if (uiState.value.activeTag == tag) null else tag)
    }

    fun onClearTagFilter() {
        tagFilter.select(null)
    }

    /** Today, this week (from Monday) or this month, in the device's current zone. */
    private fun timeRangeFor(selected: LogRange): TimeRange {
        val zone = clock.zone()
        val today = localDateOf(clock.nowMs(), zone)
        return when (selected) {
            LogRange.TODAY -> dayRange(today, zone)
            LogRange.THIS_WEEK -> weekRange(today, zone)
            LogRange.THIS_MONTH -> monthRange(today, zone)
        }
    }
}
