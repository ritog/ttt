/**
 * TaskEditorDeleteDialog — "Delete this task?" confirmation that states how many blocks go with it (SPEC 6.2).
 *
 * Depends on: strings.xml (plurals), Compose material3.
 * Must not: delete anything itself; it only reports Confirm or Dismiss.
 */
package com.ritoghosh.ttt.ui.todo

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R

@OptIn(ExperimentalComposeUiApi::class) // pluralStringResource, in case it is still marked experimental
@Composable
fun TaskEditorDeleteDialog(blocksToDelete: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val body = if (blocksToDelete == 0) {
        stringResource(R.string.delete_dialog_no_blocks)
    } else {
        pluralStringResource(R.plurals.delete_dialog_blocks, blocksToDelete, blocksToDelete)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_dialog_title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_dialog_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        },
    )
}
