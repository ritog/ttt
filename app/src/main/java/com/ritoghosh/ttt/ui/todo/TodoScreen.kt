/**
 * TodoScreen — the Todo tab: add field, tag filter, open tasks, collapsible Done section (SPEC 9.1),
 * and a "Restore a backup" link while there are no tasks at all (SPEC 8.3).
 *
 * Depends on: TodoViewModel, TaskEditorViewModel, TaskRow, TaskEditorSheet, ui/components,
 * data/backup/RestoreService.kt (the empty-screen restore link).
 * Must not: hold state of its own beyond what Compose needs for the snackbar; TodoRoute
 * connects the ViewModels, TodoScreen only draws what it is given.
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.data.backup.RestoreService
import com.ritoghosh.ttt.ui.components.RestoreBackupLink
import com.ritoghosh.ttt.ui.components.TagFilterRow
import com.ritoghosh.ttt.ui.components.formatDuration
import com.ritoghosh.ttt.ui.components.formatWeekdayTime
import com.ritoghosh.ttt.ui.components.rememberRestoreBackupLauncher
import com.ritoghosh.ttt.ui.components.titleErrorText
import com.ritoghosh.ttt.ui.theme.ListRowHeight
import com.ritoghosh.ttt.ui.theme.Spacing

/** Connects the ViewModels to the stateless screen and the editor sheet. */
@Composable
fun TodoRoute(
    todoViewModel: TodoViewModel,
    editorViewModel: TaskEditorViewModel,
    onScheduleBlock: (Long) -> Unit,
    onStartTimer: (Long) -> Unit,
    restoreService: RestoreService,
) {
    val state by todoViewModel.uiState.collectAsStateWithLifecycle()
    val onRestoreBackup = rememberRestoreBackupLauncher(restoreService)
    val editor by editorViewModel.uiState.collectAsStateWithLifecycle()
    TodoScreen(
        state = state,
        addText = todoViewModel.addText,
        onAddTextChange = todoViewModel::onAddTextChange,
        onAddTask = todoViewModel::onAddTask,
        onTaskCheckedChange = todoViewModel::onTaskCheckedChange,
        onTaskClick = editorViewModel::open,
        onTagFilterClick = todoViewModel::onTagFilterClick,
        onClearTagFilter = todoViewModel::onClearTagFilter,
        onToggleDoneSection = todoViewModel::onToggleDoneSection,
        onMessageShown = todoViewModel::onMessageShown,
        onRestoreBackup = onRestoreBackup,
    )
    editor?.let {
        TaskEditorSheet(
            state = it,
            viewModel = editorViewModel,
            onScheduleBlock = onScheduleBlock,
            onStartTimer = onStartTimer,
        )
    }
}

@Composable
fun TodoScreen(
    state: TodoUiState,
    addText: String,
    onAddTextChange: (String) -> Unit,
    onAddTask: () -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    onTaskClick: (Long) -> Unit,
    onTagFilterClick: (String) -> Unit,
    onClearTagFilter: () -> Unit,
    onToggleDoneSection: () -> Unit,
    onMessageShown: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item(key = "add") { AddTaskField(addText, onAddTextChange, onAddTask) }
            item(key = "filter") {
                TagFilterRow(state.tags, state.activeTag, onTagFilterClick, onClearTagFilter)
            }
            val emptyMessageRes = emptyTextRes(state)
            if (emptyMessageRes != null) {
                item(key = "empty") { QuietLine(stringResource(emptyMessageRes)) }
            }
            if (state.isLoaded && !state.hasAnyTask) {
                item(key = "restore") { RestoreBackupLink(onRestoreBackup, Modifier.padding(horizontal = Spacing.s)) }
            }
            items(state.openTasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    secondaryText = secondaryText(state, task.id, isOpen = true),
                    onCheckedChange = { onTaskCheckedChange(task.id, it) },
                    onClick = { onTaskClick(task.id) },
                )
            }
            if (state.doneTasks.isNotEmpty()) {
                item(key = "done-header") {
                    DoneHeader(state.doneTasks.size, onToggleDoneSection)
                }
                if (state.isDoneExpanded) {
                    items(state.doneTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            secondaryText = secondaryText(state, task.id, isOpen = false),
                            onCheckedChange = { onTaskCheckedChange(task.id, it) },
                            onClick = { onTaskClick(task.id) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Always visible at the top. Enter adds the task; focus stays for the next one. */
@Composable
private fun AddTaskField(text: String, onTextChange: (String) -> Unit, onAdd: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.todo_add_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // A custom onDone replaces the default "hide keyboard", so focus stays in the field.
        keyboardActions = KeyboardActions(onDone = { onAdd() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    )
}

@Composable
private fun DoneHeader(count: Int, onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.todo_done_header, count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.l),
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xl),
    )
}

/** "Next block Sat 14:00 · 2h 15m worked", either part when it applies, else null (SPEC 9.1). */
@Composable
private fun secondaryText(state: TodoUiState, taskId: Long, isOpen: Boolean): String? {
    val nextBlock = state.nextBlockStarts[taskId]?.takeIf { isOpen }?.let {
        stringResource(R.string.todo_next_block, formatWeekdayTime(it, state.zone))
    }
    val worked = state.workedByTask[taskId]?.takeIf { it > 0 }?.let {
        stringResource(R.string.todo_worked, formatDuration(it))
    }
    return listOfNotNull(nextBlock, worked).joinToString(" · ").ifEmpty { null }
}

/** The one-sentence empty state, or null when there is nothing to say (SPEC 9.1, 15.2). */
private fun emptyTextRes(state: TodoUiState): Int? = when {
    !state.isLoaded || state.openTasks.isNotEmpty() -> null
    !state.hasAnyTask -> R.string.todo_empty
    state.activeTag != null -> R.string.todo_filtered_empty
    else -> null // everything is done; the Done section below says enough
}

@Composable
private fun messageText(message: TodoMessage): String = when (message) {
    is TodoMessage.InvalidTitle -> titleErrorText(message.error)
    is TodoMessage.RejectedTagWords -> stringResource(R.string.todo_rejected_tags, message.words.joinToString(" "))
}
