/**
 * TaskPicker — choose a task by searching open tasks, or create one inline from the search text.
 * Used by the block editor now and the Track screen in M4 (SPEC 9.2, 9.3).
 *
 * Depends on: core/model/Task.kt, TagChip, ui/theme.
 * Must not: create tasks itself; it reports onCreate and the ViewModel calls the repository.
 */
package com.ritoghosh.ttt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.ui.theme.MinTouchTarget
import com.ritoghosh.ttt.ui.theme.Spacing

/** How many matches the picker lists at once. */
const val TASK_PICKER_MAX_OPTIONS = 6

/**
 * Open tasks matching [query]: title contains it, or a tag starts with it ("#bo" finds "book").
 * Newest first. With an empty query these are simply the most recent open tasks.
 */
fun matchingTasks(tasks: List<Task>, query: String): List<Task> {
    val text = query.trim().lowercase()
    val tagText = text.removePrefix("#")
    return tasks
        .filter { !it.isDone }
        .filter { task ->
            text.isEmpty() ||
                task.title.lowercase().contains(text) ||
                task.tags.any { tag -> tag.startsWith(tagText) }
        }
        .sortedByDescending { it.createdAtMs }
        .take(TASK_PICKER_MAX_OPTIONS)
}

/** Offer "Create …" unless the query is blank or exactly matches an existing option's title. */
fun shouldOfferCreate(query: String, options: List<Task>): Boolean =
    query.isNotBlank() && options.none { it.title.equals(query.trim(), ignoreCase = true) }

@Composable
fun TaskPicker(
    selected: Task?,
    query: String,
    options: List<Task>,
    onQueryChange: (String) -> Unit,
    onSelect: (Task) -> Unit,
    onClearSelection: () -> Unit,
    onCreateFromQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(R.string.block_task_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selected.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearSelection) { Text(stringResource(R.string.block_task_change)) }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.block_task_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            options.forEach { task -> OptionRow(task.title, task.tags.joinToString(" "), onClick = { onSelect(task) }) }
            if (shouldOfferCreate(query, options)) {
                OptionRow(stringResource(R.string.block_task_create, query.trim()), "", onClick = onCreateFromQuery)
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
