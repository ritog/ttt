/**
 * TrackViewModel — the Track screen: choose a task, run the pomodoro, answer the overtime and
 * stop questions, and write the session note (SPEC 7, 9.3).
 *
 * Depends on: data/repo (Timer, Task, Block, Entry repositories), core/timer, core/time, ui/nav/TagFilterHolder.kt.
 * Must not: apply timer rules itself; every action is a TimerEvent sent to TimerRepository.
 */
package com.ritoghosh.ttt.ui.track

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.MIN_ENTRY_MS
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.timer.TimerEvent
import com.ritoghosh.ttt.core.timer.elapsedMs
import com.ritoghosh.ttt.core.timer.isActive
import com.ritoghosh.ttt.core.timer.needsOvertimeChoice
import com.ritoghosh.ttt.data.repo.AddTaskResult
import com.ritoghosh.ttt.data.repo.BlockRepository
import com.ritoghosh.ttt.data.repo.EntryRepository
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.data.repo.TimerRepository
import com.ritoghosh.ttt.ui.nav.TagFilterHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The display refreshes once a second, only while the screen is visible (SPEC 7.2). */
private const val TICK_MS = 1_000L
private const val STOP_TIMEOUT_MS = 5_000L

class TrackViewModel(
    private val timerRepository: TimerRepository,
    private val taskRepository: TaskRepository,
    blockRepository: BlockRepository,
    entryRepository: EntryRepository,
    private val clock: Clock,
    private val tagFilter: TagFilterHolder,
) : ViewModel() {

    // Text fields are Compose state so typing is never delayed (DECISIONS.md, M2).
    var taskQuery by mutableStateOf("")
        private set
    var noteText by mutableStateOf("")
        private set

    private val local = MutableStateFlow(
        TrackLocalState(canScheduleExactAlarms = timerRepository.canScheduleExactAlarms()),
    )

    /** "Today" is fixed when the screen opens; reopening after midnight starts a new day. */
    private val today = dayRange(localDateOf(clock.nowMs(), clock.zone()), clock.zone())

    private val ticks = flow {
        while (true) {
            emit(clock.nowMs())
            delay(TICK_MS)
        }
    }

    private val timerTasksNow = combine(
        timerRepository.observeState(),
        taskRepository.observeTasks(),
        ticks,
    ) { timer, tasks, now -> Triple(timer, tasks, now) }

    private val todayData = combine(
        entryRepository.observeEntriesStartedIn(today),
        blockRepository.observeBlocksIn(today),
    ) { entries, blocks -> entries to blocks }

    private val tagsAndQuery = combine(
        taskRepository.observeTagNames(),
        tagFilter.selectedTag,
        snapshotFlow { taskQuery },
    ) { tags, selected, query -> Triple(tags, selected, query) }

    val uiState: StateFlow<TrackUiState> = combine(
        timerTasksNow,
        todayData,
        tagsAndQuery,
        local,
    ) { (timer, tasks, now), (entries, blocks), (tags, selected, query), localState ->
        buildTrackUiState(timer, tasks, now, clock.zone(), entries, blocks, tags, selected, query, localState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TrackUiState())

    // --- choosing a task ---

    fun onTaskQueryChange(text: String) {
        taskQuery = text
    }

    fun onChangeTaskClicked() {
        local.update { it.copy(isPickingTask = true) }
    }

    fun onTaskPicked(task: Task) {
        selectTask(task.id)
    }

    /** "Start timer" from the task editor or a block: select that task (it does not start by itself). */
    fun onTaskRequested(taskId: Long) {
        if (uiState.value.canChooseTask) selectTask(taskId)
    }

    fun onCreateTaskFromQuery() {
        val text = taskQuery
        viewModelScope.launch {
            val result = taskRepository.addTask(text)
            if (result is AddTaskResult.Added) selectTask(result.taskId)
        }
    }

    private fun selectTask(taskId: Long) {
        taskQuery = ""
        local.update { it.copy(isPickingTask = false) }
        send(TimerEvent.ChangeTask(taskId))
    }

    // --- running the timer ---

    fun onStart() {
        val taskId = uiState.value.timer.taskId ?: return
        send(TimerEvent.Start(taskId))
    }

    fun onPause() = send(TimerEvent.Pause)

    fun onResume() = send(TimerEvent.Resume)

    fun onStartNext() = send(TimerEvent.StartNext)

    fun onSkipBreak() = send(TimerEvent.SkipBreak)

    /** Called by the screen when a visible break runs out, so it ends without waiting for the alarm. */
    fun onBreakDue() = send(TimerEvent.Evaluate)

    fun onFinishPressed() {
        val timer = uiState.value.timer
        val now = clock.nowMs()
        if (timer.needsOvertimeChoice(now)) {
            showDialog(TrackDialog.OvertimeChoice(EndAction.FINISH, timer.elapsedMs(now), timer.phaseLengthMs))
        } else {
            send(TimerEvent.Finish(keepOvertime = true))
        }
    }

    /** Asks first when something real would be saved: long overtime → the two-choice question; ≥ 1 min → confirm. */
    fun onStopPressed() {
        val timer = uiState.value.timer
        val now = clock.nowMs()
        val isWork = timer.phase == TimerPhase.WORK && timer.isActive
        when {
            isWork && timer.needsOvertimeChoice(now) ->
                showDialog(TrackDialog.OvertimeChoice(EndAction.STOP, timer.elapsedMs(now), timer.phaseLengthMs))
            isWork && timer.elapsedMs(now) >= MIN_ENTRY_MS -> showDialog(TrackDialog.ConfirmStop)
            else -> send(TimerEvent.Stop(keepOvertime = true))
        }
    }

    fun onOvertimeChoice(keepOvertime: Boolean) {
        val dialog = uiState.value.dialog as? TrackDialog.OvertimeChoice ?: return
        dismissDialog()
        send(if (dialog.action == EndAction.FINISH) TimerEvent.Finish(keepOvertime) else TimerEvent.Stop(keepOvertime))
    }

    fun onConfirmStop() {
        dismissDialog()
        send(TimerEvent.Stop(keepOvertime = true))
    }

    fun dismissDialog() {
        local.update { it.copy(dialog = null) }
    }

    // --- the note card ---

    fun onNoteTextChange(text: String) {
        noteText = text
    }

    fun onSaveNote() {
        val entryId = uiState.value.timer.notePromptEntryId ?: return
        val text = noteText
        noteText = ""
        viewModelScope.launch { timerRepository.saveNote(entryId, text) }
    }

    /** Skipping loses nothing: the session is already saved (SPEC 7.3). */
    fun onSkipNote() {
        noteText = ""
        send(TimerEvent.ClearNotePrompt)
    }

    // --- filter and permissions ---

    fun onTagFilterClick(tag: String) {
        tagFilter.select(if (uiState.value.activeTag == tag) null else tag)
    }

    fun onClearTagFilter() {
        tagFilter.select(null)
    }

    /** The exact-alarm permission can change in system settings while the app is away. */
    fun onScreenResumed() {
        local.update { it.copy(canScheduleExactAlarms = timerRepository.canScheduleExactAlarms()) }
    }

    private fun showDialog(dialog: TrackDialog) {
        local.update { it.copy(dialog = dialog) }
    }

    private fun send(event: TimerEvent) {
        viewModelScope.launch { timerRepository.dispatch(event) }
    }
}
