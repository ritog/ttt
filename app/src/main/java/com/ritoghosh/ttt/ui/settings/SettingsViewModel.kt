/**
 * SettingsViewModel — pomodoro lengths, theme, and backup: back up now, auto-backup folder and
 * how many to keep (SPEC 8, 9.5). Restore is the shared RestoreBackupLauncher; report export joins in M7.
 *
 * Depends on: data/repo/SettingsRepository.kt, data/backup/BackupService.kt, core/settings, core/time.
 * Must not: hold a Context. Folder permissions are taken and released by the screen.
 * Changes are saved at once; there is no Save button.
 */
package com.ritoghosh.ttt.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.settings.ThemeChoice
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.backup.BackupResult
import com.ritoghosh.ttt.data.backup.BackupService
import com.ritoghosh.ttt.data.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZoneOffset

private const val STOP_TIMEOUT_MS = 5_000L

enum class SettingsMessage { BACKUP_SAVED, BACKUP_FAILED }

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val numbers: Map<NumberSetting, Int> = emptyMap(),
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** The auto-backup folder as stored (a document-tree URI), or null when auto-backup is off. */
    val autoBackupFolderUri: String? = null,
    val lastAutoBackupAtMs: Long? = null,
    val zone: ZoneId = ZoneOffset.UTC,
    val message: SettingsMessage? = null,
) {
    fun valueOf(setting: NumberSetting): Int = numbers[setting] ?: setting.default

    /** "Syncthing/ttt" from "content://…/tree/primary%3ASyncthing%2Fttt"; the raw text if that fails. */
    val autoBackupFolderLabel: String?
        get() = autoBackupFolderUri?.let { stored ->
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(stored)).substringAfter(':') }
                .getOrDefault(stored)
        }
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupService: BackupService,
    private val clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<SettingsMessage?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.observeValues(), message) { values, current ->
        SettingsUiState(
            isLoaded = true,
            numbers = NumberSetting.entries.associateWith { it.readFrom(values) },
            theme = ThemeChoice.readFrom(values),
            autoBackupFolderUri = values[AUTO_BACKUP_FOLDER_URI_KEY],
            lastAutoBackupAtMs = values[LAST_AUTO_BACKUP_AT_KEY]?.toLongOrNull(),
            zone = clock.zone(),
            message = current,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** − and + buttons: one step at a time, kept inside the setting's range by the repository. */
    fun onStep(setting: NumberSetting, delta: Int) {
        val next = uiState.value.valueOf(setting) + delta
        viewModelScope.launch { settingsRepository.setNumber(setting, next) }
    }

    fun onThemeSelected(choice: ThemeChoice) {
        viewModelScope.launch { settingsRepository.setTheme(choice) }
    }

    /** Name offered in the "Save as" picker. */
    fun suggestedBackupFileName(): String = backupService.suggestedFileName()

    fun onBackupDestinationChosen(destination: Uri) {
        viewModelScope.launch {
            val result = backupService.writeBackupTo(destination)
            val saved = result == BackupResult.SAVED
            message.value = if (saved) SettingsMessage.BACKUP_SAVED else SettingsMessage.BACKUP_FAILED
        }
    }

    /** The screen has already taken the persistable permission for [treeUri]. */
    fun onAutoBackupFolderChosen(treeUri: Uri) {
        viewModelScope.launch { settingsRepository.setAutoBackupFolder(treeUri.toString()) }
    }

    /** The screen releases the folder permission; here the setting (and last backup time) go. */
    fun onStopAutoBackup() {
        viewModelScope.launch { settingsRepository.setAutoBackupFolder(null) }
    }

    fun onMessageShown() {
        message.value = null
    }
}
