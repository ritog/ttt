/**
 * TaskRepositoryTest — tasks and tags persist, unused tags vanish, and the delete rules hold (SPEC 5.2, 6.1, 6.2).
 *
 * Depends on: a device or emulator, an in-memory AppDatabase, TaskRepository.
 * Must not: touch the real app database file.
 *
 * Blocks, entries and timer state have no repositories yet (M3/M4), so those rows are
 * inserted with plain SQL here.
 */
package com.ritoghosh.ttt.data.repo

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.model.TaskDeletion
import com.ritoghosh.ttt.core.model.TitleError
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {

    private val fixedClock = object : Clock {
        override fun nowMs(): Long = NOW_MS
        override fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    }

    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = TaskRepository(database, fixedClock)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun quickAddSavesTitleAndTags() = runBlocking {
        val id = addedId("Write outline #book #writing")
        val task = repository.getTask(id)
        assertEquals("Write outline", task?.title)
        assertEquals(listOf("book", "writing"), task?.tags)
        assertEquals(NOW_MS, task?.createdAtMs)
        assertEquals(listOf("book", "writing"), repository.observeTagNames().first())
    }

    @Test
    fun quickAddWithOnlyTagsIsRejected() = runBlocking {
        assertEquals(AddTaskResult.InvalidTitle(TitleError.EMPTY), repository.addTask("#book"))
        assertTrue(repository.observeTasks().first().isEmpty())
    }

    @Test
    fun tagsAreSharedAndUnusedTagsAreDeleted() = runBlocking {
        val first = addedId("One #book #home")
        addedId("Two #book")
        repository.updateTask(first, "One", listOf("book"), note = "")
        assertEquals(listOf("book"), repository.observeTagNames().first())
    }

    @Test
    fun checkingSetsAndUncheckingClearsDoneTime() = runBlocking {
        val id = addedId("Call mum")
        repository.setDone(id, true)
        assertEquals(NOW_MS, repository.getTask(id)?.doneAtMs)
        repository.setDone(id, false)
        assertNull(repository.getTask(id)?.doneAtMs)
    }

    @Test
    fun deletingTaskRemovesItsBlocksAndUnusedTags() = runBlocking {
        val id = addedId("Plan #solo")
        insertBlock(id)
        insertBlock(id)
        assertEquals(TaskDeletion.Allowed(blocksToDelete = 2), repository.deleteTask(id))
        assertNull(repository.getTask(id))
        assertEquals(0, database.taskDao().countBlocks(id))
        assertTrue(repository.observeTagNames().first().isEmpty())
    }

    @Test
    fun taskWithSessionsCannotBeDeleted() = runBlocking {
        val id = addedId("Deep work")
        insertEntry(id)
        assertEquals(TaskDeletion.HasSessions, repository.deleteTask(id))
        assertEquals("Deep work", repository.getTask(id)?.title)
    }

    @Test
    fun databaseItselfRefusesToDeleteTaskWithSessions() {
        val id = runBlocking { addedId("Deep work") }
        insertEntry(id)
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.taskDao().deleteById(id) }
        }
    }

    @Test
    fun taskCannotBeDeletedWhileTimerIsOnIt() = runBlocking {
        val id = addedId("Deep work")
        execSql(
            "INSERT INTO timer_state (id, phase, status, task_id, phase_length_ms, accumulated_ms, " +
                "completed_work_phases) VALUES (1, 'WORK', 'RUNNING', ?, 1500000, 0, 0)",
            id,
        )
        assertEquals(TaskDeletion.TimerIsOnTask, repository.deleteTask(id))
    }

    private suspend fun addedId(input: String): Long {
        val result = repository.addTask(input)
        check(result is AddTaskResult.Added) { "Expected the task to be added, got $result" }
        return result.taskId
    }

    private fun insertBlock(taskId: Long) =
        execSql("INSERT INTO block (task_id, start_at, end_at) VALUES (?, 0, 900000)", taskId)

    private fun insertEntry(taskId: Long) =
        execSql("INSERT INTO entry (task_id, started_at, ended_at, paused_ms, note) VALUES (?, 0, 60000, 0, '')", taskId)

    private fun execSql(sql: String, taskId: Long) {
        database.openHelper.writableDatabase.execSQL(sql, arrayOf<Any?>(taskId))
    }

    private companion object {
        const val NOW_MS = 1_790_000_000_000L
    }
}
