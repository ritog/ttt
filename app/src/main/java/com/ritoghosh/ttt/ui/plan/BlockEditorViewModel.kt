/**
 * BlockEditorViewModel — the block editor sheet: task, date, start, end, duration chips, save, delete (SPEC 6.3, 9.2).
 *
 * Depends on: data/repo (BlockRepository, TaskRepository), core/plan (BlockTimes, BlockRules), core/time.
 * Must not: validate blocks itself; BlockRepository.saveBlock applies BlockRules and reports problems.
 */
package com.ritoghosh.ttt.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.DEFAULT_NEW_BLOCK_MINUTES
import com.ritoghosh.ttt.core.MIN_BLOCK_MINUTES
import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.plan.BlockCheck
import com.ritoghosh.ttt.core.plan.blockEndMs
import com.ritoghosh.ttt.core.plan.blockEndTime
import com.ritoghosh.ttt.core.plan.blockStartMs
import com.ritoghosh.ttt.core.plan.durationMinutes
import com.ritoghosh.ttt.core.plan.endAfterDuration
import com.ritoghosh.ttt.core.plan.roundToGrid
import com.ritoghosh.ttt.core.plan.suggestedBlockStart
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.time.zonedDateTimeOf
import com.ritoghosh.ttt.data.repo.AddTaskResult
import com.ritoghosh.ttt.data.repo.BlockRepository
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.ui.components.matchingTasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private const val STOP_TIMEOUT_MS = 5_000L

class BlockEditorViewModel(
    private val blockRepository: BlockRepository,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Search text of the task picker (Compose state so typing is never delayed). */
    var taskQuery by mutableStateOf("")
        private set

    private val state = MutableStateFlow<BlockEditorUiState?>(null)

    /** Null when the sheet is closed. */
    val uiState: StateFlow<BlockEditorUiState?> = state.asStateFlow()

    /** Tasks the picker offers for the current search text. */
    val taskOptions: StateFlow<List<Task>> = combine(
        taskRepository.observeTasks(),
        snapshotFlow { taskQuery },
    ) { tasks, query -> matchingTasks(tasks, query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Opens an empty editor on [date], optionally with a task already chosen ("Schedule a block"). */
    fun openNew(date: LocalDate, taskId: Long? = null) {
        viewModelScope.launch {
            val task = taskId?.let { taskRepository.getTask(it) }
            val start = suggestedBlockStart(clock.nowMs(), date, clock.zone())
            taskQuery = ""
            val end = endAfterDuration(start, DEFAULT_NEW_BLOCK_MINUTES)
            state.value = BlockEditorUiState(blockId = null, task = task, date = date, start = start, end = end)
        }
    }

    fun openExisting(blockId: Long) {
        viewModelScope.launch {
            val block = blockRepository.getBlock(blockId) ?: return@launch
            val zone = clock.zone()
            taskQuery = ""
            state.value = BlockEditorUiState(
                blockId = block.id,
                task = taskRepository.getTask(block.taskId),
                date = localDateOf(block.startMs, zone),
                start = zonedDateTimeOf(block.startMs, zone).toLocalTime(),
                end = blockEndTime(block.endMs, zone),
            )
        }
    }

    fun onTaskQueryChange(text: String) {
        taskQuery = text
        updateOpen { it.copy(problem = null) }
    }

    fun onTaskSelected(task: Task) {
        updateOpen { it.copy(task = task, problem = null) }
    }

    fun onClearTask() {
        taskQuery = ""
        updateOpen { it.copy(task = null) }
    }

    /** Creates a task from the search text (quick-add rules: "#tags" become tags) and selects it. */
    fun onCreateTaskFromQuery() {
        val text = taskQuery
        viewModelScope.launch {
            when (val result = taskRepository.addTask(text)) {
                is AddTaskResult.Added -> {
                    val task = taskRepository.getTask(result.taskId) ?: return@launch
                    taskQuery = ""
                    updateOpen { it.copy(task = task, problem = null) }
                }
                is AddTaskResult.InvalidTitle -> {
                    updateOpen { it.copy(problem = BlockEditorProblem.InvalidNewTask(result.error)) }
                }
            }
        }
    }

    fun onDateChange(date: LocalDate) {
        updateOpen { it.copy(date = date, problem = null) }
    }

    /** Moving the start keeps the block's length (at least the minimum), like a calendar does. */
    fun onStartChange(time: LocalTime) {
        val start = roundToGrid(time)
        updateOpen {
            val length = durationMinutes(it.start, it.end).coerceAtLeast(MIN_BLOCK_MINUTES)
            it.copy(start = start, end = endAfterDuration(start, length), problem = null)
        }
    }

    fun onEndChange(time: LocalTime) {
        updateOpen { it.copy(end = roundToGrid(time), problem = null) }
    }

    fun onDurationChip(minutes: Int) {
        updateOpen { it.copy(end = endAfterDuration(it.start, minutes), problem = null) }
    }

    fun onSave() {
        val editor = state.value ?: return
        val task = editor.task
        if (task == null) {
            updateOpen { it.copy(problem = BlockEditorProblem.NoTask) }
            return
        }
        val zone = clock.zone()
        val block = Block(
            id = editor.blockId ?: NEW_ID,
            taskId = task.id,
            startMs = blockStartMs(editor.date, editor.start, zone),
            endMs = blockEndMs(editor.date, editor.end, zone),
        )
        viewModelScope.launch {
            when (val check = blockRepository.saveBlock(block)) {
                BlockCheck.Ok -> close()
                is BlockCheck.Invalid -> updateOpen { it.copy(problem = BlockEditorProblem.Rule(check.problem)) }
                is BlockCheck.Overlaps -> {
                    val title = taskRepository.getTask(check.clashing.taskId)?.title.orEmpty()
                    val clash = check.clashing
                    val problem = BlockEditorProblem.Overlap(title, clash.startMs, clash.endMs, zone)
                    updateOpen { it.copy(problem = problem) }
                }
            }
        }
    }

    /** A block can always be deleted (SPEC 6.2); no confirmation, it is easy to re-create. */
    fun onDelete() {
        val blockId = state.value?.blockId ?: return
        viewModelScope.launch {
            blockRepository.deleteBlock(blockId)
            close()
        }
    }

    /** "Start timer on this task": closes the sheet and hands the task to the Track tab (SPEC 6.3). */
    fun onStartTimer(openTrack: (Long) -> Unit) {
        val taskId = state.value?.task?.id ?: return
        close()
        openTrack(taskId)
    }

    fun onDismiss() {
        close()
    }

    private fun updateOpen(change: (BlockEditorUiState) -> BlockEditorUiState) {
        state.update { current -> current?.let(change) }
    }

    private fun close() {
        state.value = null
    }
}
