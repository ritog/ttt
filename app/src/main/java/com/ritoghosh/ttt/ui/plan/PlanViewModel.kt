/**
 * PlanViewModel — the Timeblock screen's day: which date is shown, its blocks, the tag filter, the time now.
 *
 * Depends on: data/repo (Block, Entry, Task repositories), core/time, ui/nav/TagFilterHolder.kt.
 * Must not: edit blocks; BlockEditorViewModel does that.
 */
package com.ritoghosh.ttt.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.data.repo.BlockRepository
import com.ritoghosh.ttt.data.repo.EntryRepository
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.ui.nav.TagFilterHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** The current-time line moves once a minute; finer would only cost battery. */
private const val NOW_REFRESH_MS = 60_000L

/** Keep collecting for 5 s after the screen stops, so a rotation does not restart the queries. */
private const val STOP_TIMEOUT_MS = 5_000L

class PlanViewModel(
    private val blockRepository: BlockRepository,
    private val entryRepository: EntryRepository,
    taskRepository: TaskRepository,
    private val clock: Clock,
    private val tagFilter: TagFilterHolder,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(today())

    /** Emits "now" immediately and then every minute, while the screen is collected. */
    private val nowTicks: Flow<Long> = flow {
        while (true) {
            emit(clock.nowMs())
            delay(NOW_REFRESH_MS)
        }
    }

    /** The shown day's blocks (plan) and sessions (actual); the queries switch when the date changes. */
    @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
    private val dayBlocksAndSessions = selectedDate.flatMapLatest { date ->
        val day = dayRange(date, clock.zone())
        combine(blockRepository.observeBlocksIn(day), entryRepository.observeEntriesStartedIn(day)) { blocks, entries ->
            blocks to entries
        }
    }

    private val tagsAndFilter = combine(taskRepository.observeTagNames(), tagFilter.selectedTag) { tags, selected ->
        tags to selected
    }

    val uiState: StateFlow<PlanUiState> = combine(
        selectedDate,
        dayBlocksAndSessions,
        taskRepository.observeTasks(),
        tagsAndFilter,
        nowTicks,
    ) { date, (blocks, entries), tasks, (tags, selectedTag), nowMs ->
        buildPlanUiState(date, clock.zone(), nowMs, blocks, entries, tasks, tags, selectedTag)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlanUiState())

    fun onPreviousDay() {
        selectedDate.update { it.minusDays(1) }
    }

    fun onNextDay() {
        selectedDate.update { it.plusDays(1) }
    }

    fun onToday() {
        selectedDate.value = today()
    }

    fun onTagFilterClick(tag: String) {
        tagFilter.select(if (uiState.value.activeTag == tag) null else tag)
    }

    fun onClearTagFilter() {
        tagFilter.select(null)
    }

    private fun today(): LocalDate = localDateOf(clock.nowMs(), clock.zone())
}
