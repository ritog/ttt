/**
 * TaskRow — one task in the list: checkbox, title, tag chips. No borders, at least 56 dp tall (SPEC 15.3).
 *
 * Depends on: core/model/Task.kt, ui/components/TagChip.kt, ui/theme.
 * Must not: change data itself; it reports taps.
 *
 * Done state is shown by the checkbox and a strike-through, never by colour alone (SPEC 15.2).
 * The quiet secondary line shows "Next block Sat 14:00 · 2h 15m worked" when either applies.
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.ui.components.TagChip
import com.ritoghosh.ttt.ui.theme.ListRowHeight
import com.ritoghosh.ttt.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class) // FlowRow, in case this Compose version still marks it experimental
@Composable
fun TaskRow(
    task: Task,
    secondaryText: String?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClick = onClick)
            .padding(start = Spacing.xs, end = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TalkBack reads "Done: Write outline, checkbox, not checked" instead of an unnamed checkbox.
        val checkboxLabel = stringResource(R.string.cd_task_done, task.title)
        Checkbox(
            checked = task.isDone,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = checkboxLabel },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                color = if (task.isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    task.tags.forEach { TagChip(it) }
                }
            }
        }
    }
}
