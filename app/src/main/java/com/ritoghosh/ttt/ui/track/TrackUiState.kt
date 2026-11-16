/**
 * TrackUiState — everything the Track (timer) screen shows, and the pure function that builds it.
 *
 * Depends on: core/model, core/timer (TimerMath), ui/components/TaskPicker.kt (matchingTasks).
 * Must not: touch Android, resources or the database; buildTrackUiState is unit-tested on the JVM.
 */
package com.ritoghosh.ttt.ui.track

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.timer.isBreakDue
import com.ritoghosh.ttt.ui.components.matchingTasks
import java.time.ZoneId
import java.time.ZoneOffset

enum class EndAction { FINISH, STOP }

sealed interface TrackDialog {
    /** More than an hour of overtime: "Keep all 3h 35m" or "Keep the planned 25m only" (SPEC 7.3). */
    data class OvertimeChoice(val action: EndAction, val totalWorkedMs: Long, val plannedMs: Long) : TrackDialog

    /** Stop with at least a minute worked asks first (SPEC 9.3). */
    data object ConfirmStop : TrackDialog
}

data class TrackUiState(
    val isLoaded: Boolean = false,
    val timer: TimerState = TimerState.IDLE,
    /** Refreshed every second while the screen is visible. */
    val nowMs: Long = 0L,
    val zone: ZoneId = ZoneOffset.UTC,
    /** The selected task (timer_state.task_id), or null. */
    val task: Task? = null,
    /** True when the picker shows its search instead of the selected task. */
    val isPickingTask: Boolean = false,
    val taskOptions: List<Task> = emptyList(),
    val tags: List<String> = emptyList(),
    val activeTag: String? = null,
    /** Today's next (or current, or else last) block of the selected task, shown as a quiet line. */
    val plannedToday: Block? = null,
    val todaySessionCount: Int = 0,
    val todayWorkedMs: Long = 0L,
    val dialog: TrackDialog? = null,
    val canScheduleExactAlarms: Boolean = true,
) {
    /** The task can be chosen while idle or between phases, never mid-work (SPEC 7.3 ChangeTask). */
    val canChooseTask: Boolean
        get() = timer.status == TimerStatus.IDLE || timer.status == TimerStatus.AWAITING_START

    val isBreakDue: Boolean
        get() = timer.isBreakDue(nowMs)
}

/** What the ViewModel keeps for itself (not in the database). */
data class TrackLocalState(
    val isPickingTask: Boolean = false,
    val dialog: TrackDialog? = null,
    val canScheduleExactAlarms: Boolean = true,
)

/**
 * Builds the screen state. The picker respects the global tag filter (SPEC 6.5); the planned
 * line follows SPEC 7.4: information only, never a constraint.
 */
fun buildTrackUiState(
    timer: TimerState,
    tasks: List<Task>,
    nowMs: Long,
    zone: ZoneId,
    todayEntries: List<Entry>,
    todayBlocks: List<Block>,
    tags: List<String>,
    selectedTag: String?,
    taskQuery: String,
    local: TrackLocalState,
): TrackUiState {
    val activeTag = selectedTag?.takeIf { it in tags }
    val pickable = if (activeTag == null) tasks else tasks.filter { activeTag in it.tags }
    val task = timer.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
    val taskBlocks = todayBlocks.filter { it.taskId == task?.id }.sortedBy { it.startMs }
    return TrackUiState(
        isLoaded = true,
        timer = timer,
        nowMs = nowMs,
        zone = zone,
        task = task,
        isPickingTask = local.isPickingTask || task == null,
        taskOptions = matchingTasks(pickable, taskQuery),
        tags = tags,
        activeTag = activeTag,
        plannedToday = taskBlocks.firstOrNull { it.endMs > nowMs } ?: taskBlocks.lastOrNull(),
        todaySessionCount = todayEntries.size,
        todayWorkedMs = todayEntries.sumOf { it.workedMs },
        dialog = local.dialog,
        canScheduleExactAlarms = local.canScheduleExactAlarms,
    )
}
