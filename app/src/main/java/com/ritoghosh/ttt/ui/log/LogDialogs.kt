/**
 * LogDialogs — edit a session's note (with a way to delete the session), and confirm deletion
 * (SPEC 6.2: "A session can be deleted from the Log with confirmation").
 *
 * Depends on: LogUiState, LogViewModel, ui/components/TimeFormat.kt.
 * Must not: delete without the confirmation step.
 */
package com.ritoghosh.ttt.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.components.formatDuration
import com.ritoghosh.ttt.ui.components.formatTimeRange
import com.ritoghosh.ttt.ui.theme.Spacing

@Composable
fun LogDialogs(state: LogUiState, viewModel: LogViewModel) {
    val row = state.editing ?: return
    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancelled,
            title = { Text(stringResource(R.string.log_delete_title)) },
            text = { Text(stringResource(R.string.log_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(stringResource(R.string.delete_dialog_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteCancelled) { Text(stringResource(R.string.editor_cancel)) }
            },
        )
        return
    }
    val entry = row.entry
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        title = { Text(row.taskTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = formatTimeRange(entry.startedAtMs, entry.endedAtMs, state.zone) +
                        " · " + formatDuration(entry.workedMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = viewModel.noteText,
                    onValueChange = viewModel::onNoteTextChange,
                    label = { Text(stringResource(R.string.editor_note_label)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = viewModel::onDeleteClicked) {
                    Text(stringResource(R.string.log_delete_session), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onSaveNote) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeEditor) { Text(stringResource(R.string.editor_cancel)) }
        },
    )
}
