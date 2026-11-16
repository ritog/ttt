/**
 * BlockEditorSheet — bottom sheet to create or edit a block: task, date, start/end or a duration chip (SPEC 9.2).
 *
 * Depends on: BlockEditorViewModel, ui/components (TaskPicker, TimeField, DateField, TimeFormat, ErrorText).
 * Must not: decide anything; problems come from the ViewModel, which gets them from BlockRules.
 *
 * For a saved block, "Start timer" opens the Track tab with the block's task selected (SPEC 6.3).
 */
package com.ritoghosh.ttt.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.BLOCK_DURATION_CHIPS_MINUTES
import com.ritoghosh.ttt.core.plan.BlockProblem
import com.ritoghosh.ttt.core.plan.durationMinutes
import com.ritoghosh.ttt.ui.components.DateField
import com.ritoghosh.ttt.ui.components.TaskPicker
import com.ritoghosh.ttt.ui.components.TimeField
import com.ritoghosh.ttt.ui.components.formatTimeRange
import com.ritoghosh.ttt.ui.components.titleErrorText
import com.ritoghosh.ttt.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
fun BlockEditorSheet(state: BlockEditorUiState, viewModel: BlockEditorViewModel, onStartTimer: (Long) -> Unit) {
    val options by viewModel.taskOptions.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = viewModel::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            TaskPicker(
                selected = state.task,
                query = viewModel.taskQuery,
                options = options,
                onQueryChange = viewModel::onTaskQueryChange,
                onSelect = viewModel::onTaskSelected,
                onClearSelection = viewModel::onClearTask,
                onCreateFromQuery = viewModel::onCreateTaskFromQuery,
            )
            DateField(stringResource(R.string.block_date_label), state.date, viewModel::onDateChange)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                TimeField(stringResource(R.string.block_start_label), state.start, viewModel::onStartChange)
                TimeField(stringResource(R.string.block_end_label), state.end, viewModel::onEndChange)
            }
            DurationChips(currentMinutes = durationMinutes(state.start, state.end), onChip = viewModel::onDurationChip)
            state.problem?.let { problem ->
                Text(problemText(problem), color = MaterialTheme.colorScheme.error)
            }
            if (state.blockId != null && state.task != null) {
                TextButton(onClick = { viewModel.onStartTimer(onStartTimer) }) {
                    Text(stringResource(R.string.editor_start_timer))
                }
            }
            EditorButtons(isExisting = state.blockId != null, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun DurationChips(currentMinutes: Int, onChip: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        BLOCK_DURATION_CHIPS_MINUTES.forEach { minutes ->
            FilterChip(
                selected = minutes == currentMinutes,
                onClick = { onChip(minutes) },
                label = { Text(stringResource(R.string.block_duration_chip, minutes)) },
            )
        }
    }
}

@Composable
private fun EditorButtons(isExisting: Boolean, viewModel: BlockEditorViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (isExisting) {
            TextButton(onClick = viewModel::onDelete) {
                Text(stringResource(R.string.block_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        Button(onClick = viewModel::onSave) { Text(stringResource(R.string.block_save)) }
    }
}

@Composable
private fun problemText(problem: BlockEditorProblem): String = when (problem) {
    BlockEditorProblem.NoTask -> stringResource(R.string.error_block_no_task)
    is BlockEditorProblem.InvalidNewTask -> titleErrorText(problem.error)
    is BlockEditorProblem.Overlap -> stringResource(
        R.string.error_block_overlap,
        problem.taskTitle,
        formatTimeRange(problem.startMs, problem.endMs, problem.zone),
    )
    is BlockEditorProblem.Rule -> when (problem.problem) {
        BlockProblem.END_NOT_AFTER_START -> stringResource(R.string.error_block_end_before_start)
        BlockProblem.NOT_ON_FIVE_MINUTE_GRID -> stringResource(R.string.error_block_grid)
        BlockProblem.CROSSES_MIDNIGHT -> stringResource(R.string.error_block_midnight)
        BlockProblem.TOO_SHORT -> stringResource(R.string.error_block_too_short)
    }
}
