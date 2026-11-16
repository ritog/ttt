/**
 * EntryBlockMatcher — decides for each session whether it was done in a block, continued
 * right after one, or unplanned (SPEC 6.6). Used by the Log, the Timeblock screen and the report.
 *
 * Depends on: core/model (Entry, Block), core/Constants.kt.
 * Must not: compare a session with blocks of other tasks; plans are per task.
 *
 * Rules, for a session E and the blocks of the same task:
 *  1. In block: E overlaps a block. With several, the one with the largest overlap wins.
 *  2. Continued after block: E overlaps no block, but starts at most 30 minutes after the end
 *     of a block, or after the end of a session already counted as continued for that block
 *     (a chain). Sessions are processed in time order so chains work.
 *  3. Unplanned: everything else.
 */
package com.ritoghosh.ttt.core.stats

import com.ritoghosh.ttt.core.CONTINUATION_GAP_MINUTES
import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry

private const val CONTINUATION_GAP_MS = CONTINUATION_GAP_MINUTES * 60_000L

sealed interface PlanMatch {
    data class InBlock(val blockId: Long) : PlanMatch
    data class ContinuedAfter(val blockId: Long) : PlanMatch
    data object Unplanned : PlanMatch
}

/** Entry id → how it relates to the plan. Blocks and entries of any tasks may be passed in. */
fun matchEntriesToBlocks(entries: List<Entry>, blocks: List<Block>): Map<Long, PlanMatch> {
    val blocksByTask = blocks.groupBy { it.taskId }
    val result = mutableMapOf<Long, PlanMatch>()
    entries.groupBy { it.taskId }.forEach { (taskId, taskEntries) ->
        val taskBlocks = blocksByTask[taskId].orEmpty()
        // Block id → end of the latest session counted as continued for it (for chaining).
        val chainEnds = mutableMapOf<Long, Long>()
        taskEntries.sortedBy { it.startedAtMs }.forEach { entry ->
            val match = matchOne(entry, taskBlocks, chainEnds)
            if (match is PlanMatch.ContinuedAfter) chainEnds[match.blockId] = entry.endedAtMs
            result[entry.id] = match
        }
    }
    return result
}

private fun matchOne(entry: Entry, blocks: List<Block>, chainEnds: Map<Long, Long>): PlanMatch {
    val window = entry.wallClockRange
    val bestOverlap = blocks
        .filter { it.range.overlaps(window) }
        .maxByOrNull { it.range.overlapMs(window) }
    if (bestOverlap != null) return PlanMatch.InBlock(bestOverlap.id)

    // The latest point each earlier block (or its chain) reached; the session must start soon after.
    val continued = blocks
        .filter { it.endMs <= entry.startedAtMs }
        .map { block -> block to maxOf(block.endMs, chainEnds[block.id] ?: block.endMs) }
        .filter { (_, anchor) -> entry.startedAtMs - anchor <= CONTINUATION_GAP_MS }
        .maxByOrNull { (_, anchor) -> anchor }
    return if (continued != null) PlanMatch.ContinuedAfter(continued.first.id) else PlanMatch.Unplanned
}
