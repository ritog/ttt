/**
 * BlockTimes — converting the block editor's date + clock times to instants, and the small
 * time arithmetic the editor needs (5-minute rounding, durations, suggested start).
 *
 * Depends on: core/time, core/Constants.kt, java.time.
 * Must not: validate blocks (BlockRules.kt does) or read the clock (callers pass "now").
 *
 * Convention: an END time of 00:00 means "midnight at the end of that day", because a block
 * may run until midnight but never starts its end at the beginning of its own day.
 */
package com.ritoghosh.ttt.core.plan

import com.ritoghosh.ttt.core.BLOCK_GRANULARITY_MINUTES
import com.ritoghosh.ttt.core.DEFAULT_NEW_BLOCK_START_HOUR
import com.ritoghosh.ttt.core.MIN_BLOCK_MINUTES
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.core.time.startOfDayMs
import com.ritoghosh.ttt.core.time.zonedDateTimeOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val MINUTES_PER_DAY = 24 * 60

/** The last start on the grid that still leaves room for a minimum-length block (23:45). */
private val LATEST_SUGGESTED_START: LocalTime = LocalTime.MIDNIGHT.minusMinutes(MIN_BLOCK_MINUTES.toLong())

/** Epoch ms for [start] on [date] in [zone]. */
fun blockStartMs(date: LocalDate, start: LocalTime, zone: ZoneId): Long =
    date.atTime(start).atZone(zone).toInstant().toEpochMilli()

/** Epoch ms for [end] on [date] in [zone]; 00:00 means the next midnight (see file comment). */
fun blockEndMs(date: LocalDate, end: LocalTime, zone: ZoneId): Long =
    if (end == LocalTime.MIDNIGHT) startOfDayMs(date.plusDays(1), zone) else blockStartMs(date, end, zone)

/** The local clock time of a block end; a block ending at midnight gives 00:00. */
fun blockEndTime(endMs: Long, zone: ZoneId): LocalTime = zonedDateTimeOf(endMs, zone).toLocalTime()

/** Rounds to the nearest 5-minute mark; never wraps past 23:55 into the next day. */
fun roundToGrid(time: LocalTime): LocalTime {
    val minutes = time.hour * 60 + time.minute
    val half = BLOCK_GRANULARITY_MINUTES / 2
    val rounded = (minutes + half) / BLOCK_GRANULARITY_MINUTES * BLOCK_GRANULARITY_MINUTES
    val lastMark = MINUTES_PER_DAY - BLOCK_GRANULARITY_MINUTES
    return timeOfMinutes(minOf(rounded, lastMark))
}

/** [start] plus [minutes], stopping at midnight (returned as 00:00, the end-of-day convention). */
fun endAfterDuration(start: LocalTime, minutes: Int): LocalTime {
    val total = start.hour * 60 + start.minute + minutes
    return if (total >= MINUTES_PER_DAY) LocalTime.MIDNIGHT else timeOfMinutes(total)
}

/** Length in minutes from [start] to [end], treating an end of 00:00 as midnight. */
fun durationMinutes(start: LocalTime, end: LocalTime): Int {
    val endMinutes = if (end == LocalTime.MIDNIGHT) MINUTES_PER_DAY else end.hour * 60 + end.minute
    return endMinutes - (start.hour * 60 + start.minute)
}

/**
 * Where a new block on [date] should start: on today, the next 5-minute mark after now;
 * on any other day, 09:00. Late in the evening it settles on 23:45 so a block still fits.
 */
fun suggestedBlockStart(nowMs: Long, date: LocalDate, zone: ZoneId): LocalTime {
    if (date != localDateOf(nowMs, zone)) return LocalTime.of(DEFAULT_NEW_BLOCK_START_HOUR, 0)
    val now = zonedDateTimeOf(nowMs, zone).toLocalTime()
    val minutes = now.hour * 60 + now.minute + (if (now.second > 0 || now.nano > 0) 1 else 0)
    val nextMark = (minutes + BLOCK_GRANULARITY_MINUTES - 1) / BLOCK_GRANULARITY_MINUTES * BLOCK_GRANULARITY_MINUTES
    val latest = LATEST_SUGGESTED_START.hour * 60 + LATEST_SUGGESTED_START.minute
    return timeOfMinutes(minOf(nextMark, latest))
}

private fun timeOfMinutes(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)
