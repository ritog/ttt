/**
 * TrackDialogs — the two questions the Track screen can ask: keep long overtime or not, and
 * "Stop this session?" (SPEC 7.3, 9.3).
 *
 * Depends on: TrackUiState (TrackDialog), TrackViewModel, ui/components/TimeFormat.kt.
 * Must not: end the session itself; it reports the answer to the ViewModel.
 */
package com.ritoghosh.ttt.ui.track

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.components.formatDuration

@Composable
fun TrackDialogs(state: TrackUiState, viewModel: TrackViewModel) {
    when (val dialog = state.dialog) {
        null -> Unit
        is TrackDialog.OvertimeChoice -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.overtime_title)) },
            text = { Text(stringResource(R.string.overtime_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onOvertimeChoice(keepOvertime = true) }) {
                    Text(stringResource(R.string.overtime_keep_all, formatDuration(dialog.totalWorkedMs)))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onOvertimeChoice(keepOvertime = false) }) {
                    Text(stringResource(R.string.overtime_keep_planned, formatDuration(dialog.plannedMs)))
                }
            },
        )
        TrackDialog.ConfirmStop -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.stop_title)) },
            text = { Text(stringResource(R.string.stop_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmStop) { Text(stringResource(R.string.timer_stop)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.editor_cancel)) }
            },
        )
    }
}
