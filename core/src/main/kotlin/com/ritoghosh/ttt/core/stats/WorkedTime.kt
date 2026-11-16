/**
 * WorkedTime — how much was actually worked inside a block's window, and right after it (SPEC 6.6).
 *
 * Depends on: core/model (Entry, Block), EntryBlockMatcher.kt.
 * Must not: round. Everything is in milliseconds; minutes appear only in labels and report cells.
 *
 * Simplification (also in docs/DATA.md): a session's pauses are spread evenly over its
 * wall-clock window. So the part of a session inside a block counts
 * overlap × (worked ÷ wall-clock length), even if the pause really happened outside the block.
 */
package com.ritoghosh.ttt.core.stats

import com.ritoghosh.ttt.core.model.Block
import com.ritoghosh.ttt.core.model.Entry

/** Worked time of [entries] of the block's task that falls inside [block]'s window. */
fun workedInBlockMs(block: Block, entries: List<Entry>): Long =
    entries
        .filter { it.taskId == block.taskId }
        .sumOf { entry ->
            val wallClock = entry.wallClockRange.durationMs
            if (wallClock <= 0L) {
                0L
            } else {
                block.range.overlapMs(entry.wallClockRange) * entry.workedMs / wallClock
            }
        }

/** Worked time of the sessions matched as "continued after" [block]. */
fun continuedAfterBlockMs(block: Block, entries: List<Entry>, matches: Map<Long, PlanMatch>): Long =
    entries
        .filter { matches[it.id] == PlanMatch.ContinuedAfter(block.id) }
        .sumOf { it.workedMs }
