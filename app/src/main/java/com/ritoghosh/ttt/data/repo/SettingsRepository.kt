/**
 * SettingsRepository — reads and writes the `setting` table: pomodoro lengths, theme and
 * auto-backup settings (SPEC 8.4, 9.5). Settings live in the database so backups include them.
 *
 * Depends on: data/db, core/settings/Settings.kt.
 * Must not: interpret values itself; core/settings parses them with safe fallbacks.
 */
package com.ritoghosh.ttt.data.repo

import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.settings.THEME_KEY
import com.ritoghosh.ttt.core.settings.ThemeChoice
import com.ritoghosh.ttt.core.settings.pomodoroSettingsFrom
import com.ritoghosh.ttt.core.timer.PomodoroSettings
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(database: AppDatabase) {

    private val settingDao = database.settingDao()

    /** Every stored setting as key → text, kept up to date. */
    fun observeValues(): Flow<Map<String, String>> =
        settingDao.observeAll().map { rows -> rows.associate { it.key to it.value } }

    fun observeTheme(): Flow<ThemeChoice> = observeValues().map { ThemeChoice.readFrom(it) }

    /** The timer's settings right now (read on every timer event). */
    suspend fun pomodoroSettings(): PomodoroSettings =
        pomodoroSettingsFrom(settingDao.getAll().associate { it.key to it.value })

    /** Stores a number, kept inside the setting's allowed range. */
    suspend fun setNumber(setting: NumberSetting, value: Int) {
        settingDao.put(SettingEntity(setting.key, value.coerceIn(setting.range).toString()))
    }

    suspend fun setTheme(choice: ThemeChoice) {
        settingDao.put(SettingEntity(THEME_KEY, choice.storedValue))
    }

    /** Every stored setting right now (for one-off reads such as the auto-backup check). */
    suspend fun currentValues(): Map<String, String> = settingDao.getAll().associate { it.key to it.value }

    /** The folder the user picked for auto-backups (a document-tree URI), or null to turn auto-backup off. */
    suspend fun setAutoBackupFolder(treeUri: String?) {
        if (treeUri == null) {
            settingDao.delete(AUTO_BACKUP_FOLDER_URI_KEY)
            settingDao.delete(LAST_AUTO_BACKUP_AT_KEY)
        } else {
            settingDao.put(SettingEntity(AUTO_BACKUP_FOLDER_URI_KEY, treeUri))
        }
    }

    suspend fun setLastAutoBackupAt(atMs: Long) {
        settingDao.put(SettingEntity(LAST_AUTO_BACKUP_AT_KEY, atMs.toString()))
    }
}
