/**
 * TimerRepositoryTest — the timer with a real database: sessions saved atomically with the state,
 * the note prompt, discarding short sessions, surviving an app restart, and the delete rule (SPEC 6.2, 7.3).
 *
 * Depends on: a device or emulator, an in-memory AppDatabase, the real alarm scheduler and notifications.
 * Must not: touch the real app database file. Any alarm it schedules is cancelled afterwards.
 */
package com.ritoghosh.ttt.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.model.TaskDeletion
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.timer.TimerEvent
import com.ritoghosh.ttt.data.alarm.PhaseAlarmScheduler
import com.ritoghosh.ttt.data.alarm.TimerNotifications
import com.ritoghosh.ttt.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TimerRepositoryTest {

    /** A clock the test moves forward by hand. */
    private class ManualClock(var nowMs: Long) : Clock {
        override fun nowMs(): Long = nowMs
        override fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    }

    private val minute = 60_000L
    private val start = 1_790_000_000_000L
    private val clock = ManualClock(start)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = PhaseAlarmScheduler(context)
    private lateinit var database: AppDatabase
    private lateinit var timer: TimerRepository
    private lateinit var tasks: TaskRepository
    private var taskId = 0L

    @Before
    fun openDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        timer = newTimerRepository()
        tasks = TaskRepository(database, clock)
        val added = tasks.addTask("Deep work")
        check(added is AddTaskResult.Added)
        taskId = added.taskId
    }

    @After
    fun closeDatabase() {
        alarms.cancel()
        database.close()
    }

    @Test
    fun stoppedSessionIsSavedAndAskedAboutInOneStep() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        clock.nowMs = start + 12 * minute
        val state = timer.dispatch(TimerEvent.Stop(keepOvertime = true))
        val saved = savedEntries()
        assertEquals(1, saved.size)
        assertEquals(12 * minute, saved.single().workedMs)
        assertEquals(saved.single().id, state.notePromptEntryId)
        assertEquals(TimerStatus.IDLE, state.status)
    }

    @Test
    fun sessionUnderOneMinuteIsNotSaved() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        clock.nowMs = start + 30_000L
        val state = timer.dispatch(TimerEvent.Stop(keepOvertime = true))
        assertTrue(savedEntries().isEmpty())
        assertNull(state.notePromptEntryId)
    }

    @Test
    fun finishSavesTheSessionAndQueuesTheBreak() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        clock.nowMs = start + 27 * minute
        val state = timer.dispatch(TimerEvent.Finish(keepOvertime = true))
        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertEquals(TimerStatus.AWAITING_START, state.status)
        assertEquals(27 * minute, savedEntries().single().workedMs)
    }

    @Test
    fun noteIsSavedOnTheSessionAndThePromptCloses() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        clock.nowMs = start + 10 * minute
        val entryId = timer.dispatch(TimerEvent.Stop(keepOvertime = true)).notePromptEntryId
        check(entryId != null)
        timer.saveNote(entryId, "  outlined chapter 2  ")
        assertEquals("outlined chapter 2", savedEntries().single().note)
        assertNull(timer.observeState().first().notePromptEntryId)
    }

    @Test
    fun runningTimerSurvivesAnAppRestart() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        val afterRestart = newTimerRepository() // a fresh repository reads only what was stored
        clock.nowMs = start + 5 * minute
        val state = afterRestart.observeState().first()
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(start, state.sessionStartedAtMs)
    }

    @Test
    fun taskCannotBeDeletedWhileTheTimerIsOnIt() = runBlocking {
        timer.dispatch(TimerEvent.Start(taskId))
        assertEquals(TaskDeletion.TimerIsOnTask, tasks.checkDeletion(taskId))
    }

    private fun newTimerRepository() = TimerRepository(database, clock, alarms, TimerNotifications(context))

    private suspend fun savedEntries() =
        EntryRepository(database).observeEntriesStartedIn(TimeRange(0L, Long.MAX_VALUE)).first()
}
