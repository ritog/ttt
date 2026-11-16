/**
 * TaskEditorSheet — bottom sheet to edit a task's title, tags and note, and to delete or mark it done (SPEC 9.1, 6.2).
 *
 * Depends on: TaskEditorViewModel, TaskEditorDeleteDialog, ui/components, ui/theme.
 * Must not: decide anything; it shows TaskEditorUiState and forwards taps to the ViewModel.
 *
 * "Schedule a block" and "Start timer" are added with M3 and M4, when they can work.
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.TaskDeletion
import com.ritoghosh.ttt.ui.components.tagErrorText
import com.ritoghosh.ttt.ui.components.titleErrorText
import com.ritoghosh.ttt.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
fun TaskEditorSheet(state: TaskEditorUiState, viewModel: TaskEditorViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.editor_title_label)) },
                isError = state.titleError != null,
                supportingText = if (state.titleError != null) {
                    { Text(titleErrorText(state.titleError)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            TagEditor(state, viewModel)
            OutlinedTextField(
                value = viewModel.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.editor_note_label)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DeletionSection(state, onDelete = viewModel::onDeleteClicked, onMarkDone = viewModel::onMarkDone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)) {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.editor_cancel)) }
                Button(onClick = viewModel::onSave) { Text(stringResource(R.string.editor_save)) }
            }
        }
    }
    if (state.isConfirmingDelete && state.deletion is TaskDeletion.Allowed) {
        TaskEditorDeleteDialog(
            blocksToDelete = state.deletion.blocksToDelete,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteCancelled,
        )
    }
}

/** Current tags as removable chips, plus a field to add one (Enter or the + button). */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun TagEditor(state: TaskEditorUiState, viewModel: TaskEditorViewModel) {
    if (state.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            state.tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_remove_tag, tag),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
    OutlinedTextField(
        value = viewModel.tagInput,
        onValueChange = viewModel::onTagInputChange,
        label = { Text(stringResource(R.string.editor_tag_label)) },
        singleLine = true,
        isError = state.tagError != null,
        supportingText = if (state.tagError != null) {
            { Text(tagErrorText(state.tagError)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.onAddTag() }),
        trailingIcon = {
            IconButton(onClick = viewModel::onAddTag) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.cd_add_tag))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What the rules of SPEC 6.2 allow: delete, only "mark done", or "stop the timer first". */
@Composable
private fun DeletionSection(state: TaskEditorUiState, onDelete: () -> Unit, onMarkDone: () -> Unit) {
    when (state.deletion) {
        is TaskDeletion.Allowed -> TextButton(onClick = onDelete) {
            Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
        }
        TaskDeletion.HasSessions -> Column {
            QuietNote(stringResource(R.string.editor_has_sessions))
            if (!state.isDone) {
                TextButton(onClick = onMarkDone) { Text(stringResource(R.string.editor_mark_done)) }
            }
        }
        TaskDeletion.TimerIsOnTask -> QuietNote(stringResource(R.string.editor_stop_timer_first))
    }
}

@Composable
private fun QuietNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
