/**
 * SettingsTest — stored setting text becomes PomodoroSettings and a theme, with safe fallbacks.
 *
 * Depends on: core/settings/Settings.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.settings

import com.ritoghosh.ttt.core.timer.PomodoroSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `nothing stored gives the defaults`() {
        assertEquals(PomodoroSettings.DEFAULT, pomodoroSettingsFrom(emptyMap()))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.readFrom(emptyMap()))
    }

    @Test
    fun `stored values are used`() {
        val values = mapOf(
            "work_minutes" to "50",
            "short_break_minutes" to "10",
            "long_break_minutes" to "30",
            "work_phases_before_long_break" to "2",
            "theme" to "dark",
        )
        assertEquals(PomodoroSettings.fromMinutes(50, 10, 30, 2), pomodoroSettingsFrom(values))
        assertEquals(ThemeChoice.DARK, ThemeChoice.readFrom(values))
    }

    @Test
    fun `out-of-range or garbled values fall back to the default`() {
        val values = mapOf("work_minutes" to "0", "short_break_minutes" to "abc", "theme" to "purple")
        val settings = pomodoroSettingsFrom(values)
        assertEquals(PomodoroSettings.DEFAULT.workMs, settings.workMs)
        assertEquals(PomodoroSettings.DEFAULT.shortBreakMs, settings.shortBreakMs)
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.readFrom(values))
    }

    @Test
    fun `the keys match SPEC 9_5`() {
        assertEquals(
            listOf("work_minutes", "short_break_minutes", "long_break_minutes", "work_phases_before_long_break"),
            NumberSetting.entries.filter { it.group == SettingGroup.POMODORO }.map { it.key },
        )
        assertEquals("auto_backup_keep", NumberSetting.AUTO_BACKUP_KEEP.key)
        assertEquals(7, NumberSetting.AUTO_BACKUP_KEEP.readFrom(emptyMap()))
    }
}
