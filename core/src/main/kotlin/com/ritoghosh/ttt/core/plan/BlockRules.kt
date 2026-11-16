/**
 * BlockRules — checks a block before it is saved: order, 5-minute grid, one local day,
 * minimum length, and no overlap with any other block (SPEC 6.3).
 *
 * Depends on: core/model/Block.kt, core/time, core/Constants.kt.
 * Must not: save or change blocks. Blocks in the past are allowed on purpose
 * (people plan loosely and fix later), so there is no "in the past" check.
 */
package com.ritoghosh.ttt.core.plan

import com.ritoghosh.ttt.core.BLOCK_GRANULARITY_MINUTES
import com.ritoghosh.ttt.core.MIN_BLOCK_MINUTES
import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.time.dayRange
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.time.zonedDateTimeOf
import java.time.ZoneId

private const val MS_PER_MINUTE = 60_000L

enum class BlockProblem {
    END_NOT_AFTER_START,
    NOT_ON_FIVE_MINUTE_GRID,
    CROSSES_MIDNIGHT,
    TOO_SHORT,
}

sealed interface BlockCheck {
    data object Ok : BlockCheck
    data class Invalid(val problem: BlockProblem) : BlockCheck

    /** The block clashes with [clashing]; the message names that block. */
    data class Overlaps(val clashing: Block) : BlockCheck
}

/**
 * Checks [candidate] against the rules and against [existingBlocks] (all saved blocks of any task).
 * When editing, a saved block with the candidate's own id is ignored. If several blocks clash,
 * the earliest one is reported. Problems are reported in a fixed order, one at a time.
 */
fun checkBlock(candidate: Block, existingBlocks: List<Block>, zone: ZoneId): BlockCheck {
    val problem = shapeProblem(candidate, zone)
    if (problem != null) return BlockCheck.Invalid(problem)
    val clashing = existingBlocks
        .filter { it.id != candidate.id && it.range.overlaps(candidate.range) }
        .minByOrNull { it.startMs }
    return if (clashing == null) BlockCheck.Ok else BlockCheck.Overlaps(clashing)
}

/** The first rule the block's own times break, or null if its shape is fine. */
private fun shapeProblem(block: Block, zone: ZoneId): BlockProblem? = when {
    block.endMs <= block.startMs -> BlockProblem.END_NOT_AFTER_START
    !isOnGrid(block.startMs, zone) || !isOnGrid(block.endMs, zone) -> BlockProblem.NOT_ON_FIVE_MINUTE_GRID
    block.endMs > latestEndMs(block.startMs, zone) -> BlockProblem.CROSSES_MIDNIGHT
    block.endMs - block.startMs < MIN_BLOCK_MINUTES * MS_PER_MINUTE -> BlockProblem.TOO_SHORT
    else -> null
}

/**
 * The latest allowed end for a block starting at [startMs]: the next local midnight.
 * Ending exactly at midnight is allowed ("the rest of that day").
 */
fun latestEndMs(startMs: Long, zone: ZoneId): Long = dayRange(localDateOf(startMs, zone), zone).endMs

/**
 * True if [instantMs] is on a whole 5-minute mark of *local* time (e.g. 14:05:00.000).
 * Checked on local time because that is what the user sees in the editor.
 */
fun isOnGrid(instantMs: Long, zone: ZoneId): Boolean {
    val time = zonedDateTimeOf(instantMs, zone)
    return time.second == 0 && time.nano == 0 && time.minute % BLOCK_GRANULARITY_MINUTES == 0
}
