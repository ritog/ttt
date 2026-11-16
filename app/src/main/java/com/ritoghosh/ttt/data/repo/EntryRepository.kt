/**
 * EntryRepository — reading recorded sessions (entries). Sessions are created only by the timer,
 * through TimerRepository (SPEC 6.4).
 *
 * Depends on: data/db, core/time/TimeRange.kt.
 * Must not: create entries. M5 adds note editing and deletion for the Log.
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
}
