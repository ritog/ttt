/**
 * SettingsViewModel — pomodoro lengths, theme, backup (back up now, auto-backup folder, how many
 * to keep) and the report export (SPEC 8, 9.5, 11). Restore is the shared RestoreBackupLauncher.
 *
 * Depends on: data/repo/SettingsRepository.kt, data/backup/BackupService.kt, data/report/ReportService.kt,
 * core/settings, core/report, core/time.
 * Must not: hold a Context. Folder permissions are taken and released by the screen.
 * Changes are saved at once; there is no Save button.
 */
package com.ritoghosh.ttt.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritoghosh.ttt.core.report.ReportRange
import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.settings.ThemeChoice
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.backup.BackupResult
import com.ritoghosh.ttt.data.backup.BackupService
import com.ritoghosh.ttt.data.repo.SettingsRepository
import com.ritoghosh.ttt.data.report.ReportResult
import com.ritoghosh.ttt.data.report.ReportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val STOP_TIMEOUT_MS = 5_000L

enum class SettingsMessage { BACKUP_SAVED, BACKUP_FAILED, REPORT_SAVED, REPORT_FAILED }

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val numbers: Map<NumberSetting, Int> = emptyMap(),
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** The auto-backup folder as stored (a document-tree URI), or null when auto-backup is off. */
    val autoBackupFolderUri: String? = null,
    val lastAutoBackupAtMs: Long? = null,
    val zone: ZoneId = ZoneOffset.UTC,
    val message: SettingsMessage? = null,
    /** True while a report is being written; the Export button waits. */
    val isExportingReport: Boolean = false,
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
    private val reportService: ReportService,
    private val clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<SettingsMessage?>(null)
    private val isExportingReport = MutableStateFlow(false)

    /** The range chosen in the dialog, kept while the "Save as" picker is open. */
    private var pendingReportRange: ReportRange? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeValues(),
        message,
        isExportingReport,
    ) { values, current, exporting ->
        SettingsUiState(
            isLoaded = true,
            numbers = NumberSetting.entries.associateWith { it.readFrom(values) },
            theme = ThemeChoice.readFrom(values),
            autoBackupFolderUri = values[AUTO_BACKUP_FOLDER_URI_KEY],
            lastAutoBackupAtMs = values[LAST_AUTO_BACKUP_AT_KEY]?.toLongOrNull(),
            zone = clock.zone(),
            message = current,
            isExportingReport = exporting,
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

    /** Today, for the report dialog's "This month" and its custom-range defaults. */
    fun today(): LocalDate = reportService.today()

    fun suggestedReportFileName(): String = reportService.suggestedFileName()

    /** Step 1: the range from the dialog. The screen then opens the "Save as" picker. */
    fun onReportRangeChosen(range: ReportRange) {
        pendingReportRange = range
    }

    /** Step 2: where to save. Builds and writes the report. */
    fun onReportDestinationChosen(destination: Uri) {
        val range = pendingReportRange ?: ReportRange.AllTime
        pendingReportRange = null
        isExportingReport.value = true
        viewModelScope.launch {
            val result = reportService.writeReportTo(destination, range)
            isExportingReport.value = false
            val saved = result == ReportResult.SAVED
            message.value = if (saved) SettingsMessage.REPORT_SAVED else SettingsMessage.REPORT_FAILED
        }
    }

    fun onMessageShown() {
        message.value = null
    }
}
