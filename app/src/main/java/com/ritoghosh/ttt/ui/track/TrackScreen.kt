/**
 * TrackScreen — the Track tab: task picker and Start while idle; the timer face and its buttons
 * while running; the note card after a session; today's total at the bottom (SPEC 9.3, 15.3).
 *
 * Depends on: TrackViewModel, TimerFace, TrackParts, TrackDialogs, ui/components, ui/nav/ScreenRequests.kt.
 * Must not: decide timer behaviour; it forwards taps. Like the editor sheets, it reads the
 * ViewModel's text fields directly (DECISIONS.md, M2).
 */
package com.ritoghosh.ttt.ui.track

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.ui.components.TagFilterRow
import com.ritoghosh.ttt.ui.components.TaskPicker
import com.ritoghosh.ttt.ui.nav.ScreenRequests
import com.ritoghosh.ttt.ui.theme.Spacing

@Composable
fun TrackRoute(viewModel: TrackViewModel, screenRequests: ScreenRequests) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timerRequest by screenRequests.startTimerForTask.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsAllowed = granted }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = hasNotificationPermission(context)
        viewModel.onScreenResumed()
    }
    // "Start timer" from the task editor or a block: select that task here.
    LaunchedEffect(timerRequest, state.isLoaded) {
        val taskId = timerRequest
        if (taskId != null && state.isLoaded) {
            viewModel.onTaskRequested(taskId)
            screenRequests.consumeTimerRequest()
        }
    }
    // A break that runs out while you watch ends at once, without waiting for the alarm.
    LaunchedEffect(state.isBreakDue) {
        if (state.isBreakDue) viewModel.onBreakDue()
    }

    TrackScreen(
        state = state,
        viewModel = viewModel,
        notificationsAllowed = notificationsAllowed,
        onStart = {
            // Asked the first time a timer starts (SPEC 10.1); the timer works the same without it.
            if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.onStart()
        },
        onAllowExactAlarms = { openExactAlarmSettings(context) },
    )
    TrackDialogs(state, viewModel)
}

@Composable
private fun TrackScreen(
    state: TrackUiState,
    viewModel: TrackViewModel,
    notificationsAllowed: Boolean,
    onStart: () -> Unit,
    onAllowExactAlarms: () -> Unit,
) {
    if (!state.isLoaded) return
    val isIdle = state.timer.status == TimerStatus.IDLE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (!isIdle) TimerFace(state)
        if (state.canChooseTask) TaskChooser(state, viewModel)
        if (isIdle) {
            PlannedTodayLine(state)
            Button(
                onClick = onStart,
                enabled = state.task != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.timer_start)) }
            PermissionHints(notificationsAllowed, state.canScheduleExactAlarms, onAllowExactAlarms)
        } else {
            TimerButtons(state, viewModel)
        }
        if (state.timer.notePromptEntryId != null) NoteCard(viewModel)
        TodayRow(state.todaySessionCount, state.todayWorkedMs)
    }
}

/** The tag filter and the picker; the filter narrows the picker's list (SPEC 6.5). */
@Composable
private fun TaskChooser(state: TrackUiState, viewModel: TrackViewModel) {
    TagFilterRow(state.tags, state.activeTag, viewModel::onTagFilterClick, viewModel::onClearTagFilter)
    TaskPicker(
        selected = if (state.isPickingTask) null else state.task,
        query = viewModel.taskQuery,
        options = state.taskOptions,
        onQueryChange = viewModel::onTaskQueryChange,
        onSelect = viewModel::onTaskPicked,
        onClearSelection = viewModel::onChangeTaskClicked,
        onCreateFromQuery = viewModel::onCreateTaskFromQuery,
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
