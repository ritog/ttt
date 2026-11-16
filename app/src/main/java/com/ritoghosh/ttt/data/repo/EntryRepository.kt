/**
 * EntryRepository — reading recorded sessions (entries). Sessions are created only by the timer,
 * through TimerRepository (SPEC 6.4).
 *
 * Depends on: data/db, core/time/TimeRange.kt.
 * Must not: create entries, or change their times (out of scope in v1). Notes and deletion only.
 */
package com.ritoghosh.ttt.data.repo

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.toEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EntryRepository(database: AppDatabase) {

    private val entryDao = database.entryDao()

    /** Sessions that start inside [range] (a session belongs to the day it starts), oldest first. */
    fun observeEntriesStartedIn(range: TimeRange): Flow<List<Entry>> =
        entryDao.observeStartedIn(range.startMs, range.endMs).map { rows -> rows.map { it.toEntry() } }

    /** Task id → all-time worked milliseconds. */
    fun observeWorkedByTask(): Flow<Map<Long, Long>> =
        entryDao.observeWorkedPerTask().map { rows -> rows.associate { it.taskId to it.workedMs } }

    suspend fun updateNote(entryId: Long, note: String) {
        entryDao.updateNote(entryId, note.trim())
    }

    /** Deleting a session from the Log (asked for confirmation first, SPEC 6.2). */
    suspend fun deleteEntry(entryId: Long) {
        entryDao.deleteById(entryId)
    }
}
