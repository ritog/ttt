/**
 * TagTotals — worked time per tag for a set of sessions (Log, report).
 *
 * Depends on: core/model/Entry.kt.
 * Must not: split time between tags. A task with two tags counts fully in both, so tag totals
 * can add up to more than the time worked (the Log and report say so).
 */
package com.ritoghosh.ttt.core.stats

import com.ritoghosh.ttt.core.model.Entry

/** One row: a tag (null means "no tag") and its worked time. */
data class TagTotal(val tag: String?, val workedMs: Long)

/**
 * Totals for [entries], using each session's task tags from [tagsByTask].
 * Largest first; ties by name; "no tag" last.
 */
fun tagTotals(entries: List<Entry>, tagsByTask: Map<Long, List<String>>): List<TagTotal> {
    val totals = mutableMapOf<String?, Long>()
    entries.forEach { entry ->
        val tags = tagsByTask[entry.taskId].orEmpty()
        val keys: List<String?> = tags.ifEmpty { listOf(null) }
        keys.forEach { tag -> totals[tag] = (totals[tag] ?: 0L) + entry.workedMs }
    }
    val tagged = totals.filterKeys { it != null }
        .map { (tag, ms) -> TagTotal(tag, ms) }
        .sortedWith(compareByDescending<TagTotal> { it.workedMs }.thenBy { it.tag })
    val untagged = totals[null]?.let { listOf(TagTotal(null, it)) }.orEmpty()
    return tagged + untagged
}
