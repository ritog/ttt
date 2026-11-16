/**
 * Constants — every named number from SPEC.md, kept in one place (SPEC 12, rule 7).
 *
 * Depends on: nothing.
 * Must not: hold mutable state or anything Android-specific. Change a value only together
 * with SPEC.md, the tests that pin it, and docs/DECISIONS.md.
 */
package com.ritoghosh.ttt.core

/** A task title is trimmed and must be 1..200 characters (SPEC 5.1). */
const val TASK_TITLE_MAX_LENGTH = 200

/** A tag is 1..30 characters of a-z, 0-9, '-' and '_' (SPEC 6.1). */
const val TAG_MAX_LENGTH = 30

/** Block start and end move in 5-minute steps in the editor (SPEC 6.3). */
const val BLOCK_GRANULARITY_MINUTES = 5

/** A block shorter than this is rejected (SPEC 6.3). */
const val MIN_BLOCK_MINUTES = 15

/** Quick duration chips shown in the block editor (SPEC 6.3). */
val BLOCK_DURATION_CHIPS_MINUTES = listOf(30, 60, 90, 120)

/**
 * An entry that starts within this many minutes after a block of the same task ends
 * (or after a chained "continued" entry ends) counts as "continued after block" (SPEC 6.6).
 */
const val CONTINUATION_GAP_MINUTES = 30

/** A stopped work session shorter than this is discarded, not saved (SPEC 7.3). */
const val MIN_ENTRY_MS = 60_000L

/** Above this much overtime, Finish/Stop asks whether to keep it (SPEC 7.3). */
const val LONG_OVERTIME_MINUTES = 60

/** Auto-backup runs when the last one is older than this (SPEC 8.4). */
const val AUTO_BACKUP_INTERVAL_HOURS = 24
