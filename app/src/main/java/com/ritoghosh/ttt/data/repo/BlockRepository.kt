/**
 * BlockRepository — the one door to time blocks: read a day, save (with the SPEC 6.3 rules), delete.
 *
 * Depends on: data/db, core/plan/BlockRules.kt, core/time.
 * Must not: save a block that BlockRules rejects. Callers get core models only.
 *
 * Saving checks for overlaps and writes in one transaction, so two quick saves can never
 * both pass the overlap check and end up overlapping.
 */
package com.ritoghosh.ttt.data.repo

import androidx.room.withTransaction
import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.NEW_ID
import com.ritoghosh.ttt.core.plan.BlockCheck
import com.ritoghosh.ttt.core.plan.checkBlock
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.toBlock
import com.ritoghosh.ttt.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlockRepository(private val database: AppDatabase, private val clock: Clock) {

    private val blockDao = database.blockDao()

    /** Blocks overlapping [range] (e.g. one local day), earliest first, kept up to date. */
    fun observeBlocksIn(range: TimeRange): Flow<List<Block>> =
        blockDao.observeOverlapping(range.startMs, range.endMs).map { rows -> rows.map { it.toBlock() } }

    suspend fun getBlock(blockId: Long): Block? = blockDao.getById(blockId)?.toBlock()

    /**
     * Inserts a new block (id = NEW_ID) or updates an existing one, if BlockRules allow it.
     * Returns the check, so the caller can show what was wrong (including the clashing block).
     * Only blocks on the candidate's own local day can clash, because blocks never cross midnight.
     */
    suspend fun saveBlock(block: Block): BlockCheck = database.withTransaction {
        val zone = clock.zone()
        val day = dayRange(localDateOf(block.startMs, zone), zone)
        val sameDay = blockDao.getOverlapping(day.startMs, day.endMs).map { it.toBlock() }
        val check = checkBlock(block, sameDay, zone)
        if (check == BlockCheck.Ok) {
            if (block.id == NEW_ID) blockDao.insert(block.toEntity()) else blockDao.update(block.toEntity())
        }
        check
    }

    /** A block can always be deleted (SPEC 6.2). */
    suspend fun deleteBlock(blockId: Long) {
        blockDao.deleteById(blockId)
    }

    /**
     * Task id → start of its next block at or after [nowMs]. [nowMs] is fixed when called,
     * so a block that starts while the screen stays open still counts as "next" until reopened.
     */
    fun observeNextBlockStarts(nowMs: Long): Flow<Map<Long, Long>> =
        blockDao.observeNextStarts(nowMs).map { rows -> rows.associate { it.taskId to it.startAt } }
}
