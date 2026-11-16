/**
 * EntryAndSettingsRepositoryTest — session notes and deletion from the Log, worked time per task,
 * and settings that persist and stay in range (SPEC 6.4, 9.4, 9.5).
 *
 * Depends on: a device or emulator, an in-memory AppDatabase.
 * Must not: touch the real app database file.
 */
package com.ritoghosh.ttt.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.settings.ThemeChoice
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.timer.PomodoroSettings
import com.ritoghosh.ttt.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryAndSettingsRepositoryTest {

    private val minute = 60_000L
    private lateinit var database: AppDatabase
    private lateinit var entries: EntryRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        entries = EntryRepository(database)
        settings = SettingsRepository(database)
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO task (id, title, is_done, created_at, note) VALUES (1, 'Write', 0, 0, '')")
        val insertEntry = "INSERT INTO entry (id, task_id, started_at, ended_at, paused_ms, note) VALUES (?, 1, ?, ?, ?, '')"
        sql.execSQL(insertEntry, arrayOf<Any?>(10, 0, 30 * minute, 5 * minute)) // 25 min worked
        sql.execSQL(insertEntry, arrayOf<Any?>(11, 60 * minute, 80 * minute, 0)) // 20 min worked
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun noteIsTrimmedAndSaved() = runBlocking {
        entries.updateNote(10, "  first draft  ")
        assertEquals("first draft", all().first { it.id == 10L }.note)
    }

    @Test
    fun deletedSessionIsGoneAndNoLongerCounts() = runBlocking {
        assertEquals(mapOf(1L to 45 * minute), entries.observeWorkedByTask().first())
        entries.deleteEntry(11)
        assertEquals(listOf(10L), all().map { it.id })
        assertEquals(mapOf(1L to 25 * minute), entries.observeWorkedByTask().first())
    }

    @Test
    fun settingsStartAtDefaultsAndPersist() = runBlocking {
        assertEquals(PomodoroSettings.DEFAULT, settings.pomodoroSettings())
        settings.setNumber(NumberSetting.WORK_MINUTES, 50)
        settings.setTheme(ThemeChoice.DARK)
        assertEquals(50 * minute, settings.pomodoroSettings().workMs)
        assertEquals(ThemeChoice.DARK, settings.observeTheme().first())
    }

    @Test
    fun numbersAreKeptInRange() = runBlocking {
        settings.setNumber(NumberSetting.SHORT_BREAK_MINUTES, 0)
        settings.setNumber(NumberSetting.WORK_PHASES_BEFORE_LONG_BREAK, 99)
        val stored = settings.observeValues().first()
        assertEquals("1", stored[NumberSetting.SHORT_BREAK_MINUTES.key])
        assertEquals("12", stored[NumberSetting.WORK_PHASES_BEFORE_LONG_BREAK.key])
    }

    private suspend fun all() = entries.observeEntriesStartedIn(TimeRange(0L, Long.MAX_VALUE)).first()
}
