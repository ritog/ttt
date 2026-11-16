/**
 * BlockRepositoryTest — blocks persist, overlaps are rejected and not saved, editing and deleting work (SPEC 6.3).
 *
 * Depends on: a device or emulator, an in-memory AppDatabase, BlockRepository, TaskRepository.
 * Must not: touch the real app database file.
 */
package com.ritoghosh.ttt.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.plan.BlockCheck
import com.ritoghosh.ttt.core.plan.BlockProblem
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.dayRange
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class BlockRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val day: LocalDate = LocalDate.parse("2026-11-16")
    private val clock = object : Clock {
        override fun nowMs(): Long = at("08:00")
        override fun zone(): ZoneId = zone
    }

    private lateinit var database: AppDatabase
    private lateinit var blocks: BlockRepository
    private var taskId = 0L

    @Before
    fun openDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        blocks = BlockRepository(database, clock)
        val added = TaskRepository(database, clock).addTask("Write outline")
        check(added is AddTaskResult.Added)
        taskId = added.taskId
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun validBlockIsSavedAndShownForItsDay() = runBlocking {
        assertEquals(BlockCheck.Ok, blocks.saveBlock(block("14:00", "16:00")))
        val saved = blocks.observeBlocksIn(dayRange(day, zone)).first()
        assertEquals(1, saved.size)
        assertEquals(at("14:00"), saved.single().startMs)
    }

    @Test
    fun overlappingBlockIsRejectedAndNotSaved() = runBlocking {
        blocks.saveBlock(block("14:00", "16:00"))
        val result = blocks.saveBlock(block("15:00", "17:00"))
        assertTrue(result is BlockCheck.Overlaps)
        assertEquals(1, blocks.observeBlocksIn(dayRange(day, zone)).first().size)
    }

    @Test
    fun invalidBlockIsRejectedAndNotSaved() = runBlocking {
        assertEquals(BlockCheck.Invalid(BlockProblem.TOO_SHORT), blocks.saveBlock(block("14:00", "14:10")))
        assertTrue(blocks.observeBlocksIn(dayRange(day, zone)).first().isEmpty())
    }

    @Test
    fun editingBlockDoesNotClashWithItself() = runBlocking {
        blocks.saveBlock(block("14:00", "16:00"))
        val saved = blocks.observeBlocksIn(dayRange(day, zone)).first().single()
        assertEquals(BlockCheck.Ok, blocks.saveBlock(saved.copy(endMs = at("16:30"))))
        assertEquals(at("16:30"), blocks.getBlock(saved.id)?.endMs)
    }

    @Test
    fun deletedBlockIsGone() = runBlocking {
        blocks.saveBlock(block("14:00", "16:00"))
        val saved = blocks.observeBlocksIn(dayRange(day, zone)).first().single()
        blocks.deleteBlock(saved.id)
        assertNull(blocks.getBlock(saved.id))
    }

    @Test
    fun nextBlockStartIsTheEarliestUpcomingOne() = runBlocking {
        blocks.saveBlock(block("07:00", "07:30")) // before "now" (08:00), not upcoming
        blocks.saveBlock(block("16:00", "17:00"))
        blocks.saveBlock(block("10:00", "11:00"))
        assertEquals(mapOf(taskId to at("10:00")), blocks.observeNextBlockStarts(clock.nowMs()).first())
    }

    private fun block(start: String, end: String) = Block(NEW_ID, taskId, at(start), at(end))

    private fun at(time: String): Long = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
}
