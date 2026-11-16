/**
 * Settings — the setting keys stored in the `setting` table, their allowed values, and how
 * stored text becomes PomodoroSettings, a theme and backup settings (SPEC 8.4, 9.5).
 *
 * Depends on: core/timer/PomodoroSettings.kt, core/Constants.kt.
 * Must not: read storage. Missing or invalid stored values fall back to the defaults, so a
 * damaged settings row can never stop the timer from working.
 */
package com.ritoghosh.ttt.core.settings

import com.ritoghosh.ttt.core.DEFAULT_AUTO_BACKUP_KEEP
import com.ritoghosh.ttt.core.DEFAULT_LONG_BREAK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_SHORT_BREAK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_WORK_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_WORK_PHASES_BEFORE_LONG_BREAK
import com.ritoghosh.ttt.core.timer.PomodoroSettings

/** Keys of settings that are not plain numbers (SPEC 9.5). */
const val THEME_KEY = "theme"
const val AUTO_BACKUP_FOLDER_URI_KEY = "auto_backup_folder_uri"
const val LAST_AUTO_BACKUP_AT_KEY = "last_auto_backup_at"

/** Which part of the Settings screen a number setting belongs to. */
enum class SettingGroup { POMODORO, BACKUP }

/** The number settings on the Settings screen, with their key, allowed range, default and group. */
enum class NumberSetting(val key: String, val range: IntRange, val default: Int, val group: SettingGroup) {
    WORK_MINUTES("work_minutes", 1..180, DEFAULT_WORK_MINUTES, SettingGroup.POMODORO),
    SHORT_BREAK_MINUTES("short_break_minutes", 1..60, DEFAULT_SHORT_BREAK_MINUTES, SettingGroup.POMODORO),
    LONG_BREAK_MINUTES("long_break_minutes", 1..120, DEFAULT_LONG_BREAK_MINUTES, SettingGroup.POMODORO),
    WORK_PHASES_BEFORE_LONG_BREAK(
        "work_phases_before_long_break",
        1..12,
        DEFAULT_WORK_PHASES_BEFORE_LONG_BREAK,
        SettingGroup.POMODORO,
    ),
    AUTO_BACKUP_KEEP("auto_backup_keep", 1..30, DEFAULT_AUTO_BACKUP_KEEP, SettingGroup.BACKUP),
    ;

    /** The stored value if it is a whole number in range, otherwise the default. */
    fun readFrom(values: Map<String, String>): Int =
        values[key]?.toIntOrNull()?.takeIf { it in range } ?: default
}

enum class ThemeChoice(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        /** The stored theme, or SYSTEM if missing or unknown. */
        fun readFrom(values: Map<String, String>): ThemeChoice =
            entries.firstOrNull { it.storedValue == values[THEME_KEY] } ?: SYSTEM
    }
}

/** Builds the timer's settings from the stored key/value pairs. */
fun pomodoroSettingsFrom(values: Map<String, String>): PomodoroSettings = PomodoroSettings.fromMinutes(
    work = NumberSetting.WORK_MINUTES.readFrom(values),
    shortBreak = NumberSetting.SHORT_BREAK_MINUTES.readFrom(values),
    longBreak = NumberSetting.LONG_BREAK_MINUTES.readFrom(values),
    workPhasesBeforeLongBreak = NumberSetting.WORK_PHASES_BEFORE_LONG_BREAK.readFrom(values),
)
