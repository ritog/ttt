/**
 * BackupSection — the Backup part of Settings (SPEC 8, 9.5): Back up now, Restore backup,
 * Auto-backup folder (choose / stop), Keep last N, and when the last auto-backup ran.
 *
 * Depends on: SettingsViewModel, ui/components (RestoreBackupLauncher, TimeFormat), Android Activity Result APIs.
 * Must not: write files itself. It only launches the system pickers and takes or releases the
 * folder permission, which needs a Context; everything else goes through the ViewModel.
 */
package com.ritoghosh.ttt.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.data.backup.RestoreService
import com.ritoghosh.ttt.ui.components.formatDayLabel
import com.ritoghosh.ttt.ui.components.formatTime
import com.ritoghosh.ttt.ui.components.rememberRestoreBackupLauncher
import com.ritoghosh.ttt.ui.theme.MinTouchTarget

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val FOLDER_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
fun BackupSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    restoreService: RestoreService,
) {
    val context = LocalContext.current
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        uri?.let(viewModel::onBackupDestinationChosen)
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && takeFolderPermission(context, uri)) viewModel.onAutoBackupFolderChosen(uri)
    }
    val restore = rememberRestoreBackupLauncher(restoreService)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { saveAs.launch(viewModel.suggestedBackupFileName()) }) {
            Text(stringResource(R.string.backup_now))
        }
        TextButton(onClick = restore) { Text(stringResource(R.string.backup_restore)) }
    }

    Column(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)) {
        Text(stringResource(R.string.backup_auto_folder), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = state.autoBackupFolderLabel ?: stringResource(R.string.backup_auto_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { chooseFolder.launch(null) }) {
            val isOff = state.autoBackupFolderUri == null
            Text(stringResource(if (isOff) R.string.backup_choose_folder else R.string.backup_change_folder))
        }
        state.autoBackupFolderUri?.let { stored ->
            TextButton(onClick = {
                releaseFolderPermission(context, stored)
                viewModel.onStopAutoBackup()
            }) { Text(stringResource(R.string.backup_stop_auto)) }
        }
    }
    if (state.autoBackupFolderUri != null) {
        StepperRow(
            label = stringResource(R.string.setting_auto_backup_keep),
            value = state.valueOf(NumberSetting.AUTO_BACKUP_KEEP),
            range = NumberSetting.AUTO_BACKUP_KEEP.range,
            onStep = { delta -> viewModel.onStep(NumberSetting.AUTO_BACKUP_KEEP, delta) },
        )
        Text(
            text = lastAutoBackupText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun lastAutoBackupText(state: SettingsUiState): String {
    val atMs = state.lastAutoBackupAtMs ?: return stringResource(R.string.backup_last_never)
    val whenText = "${formatDayLabel(localDateOf(atMs, state.zone))} ${formatTime(atMs, state.zone)}"
    return stringResource(R.string.backup_last_at, whenText)
}

/** Keeps access to the folder across restarts. False if the provider refuses (then nothing is saved). */
private fun takeFolderPermission(context: Context, treeUri: Uri): Boolean = try {
    context.contentResolver.takePersistableUriPermission(treeUri, FOLDER_FLAGS)
    true
} catch (refused: SecurityException) {
    false
}

private fun releaseFolderPermission(context: Context, stored: String) {
    try {
        context.contentResolver.releasePersistableUriPermission(Uri.parse(stored), FOLDER_FLAGS)
    } catch (alreadyGone: SecurityException) {
        // Nothing to release; the setting is removed anyway.
    }
}
