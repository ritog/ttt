/**
 * TimeFormat — how dates and times are written on screen: 24-hour "14:05", "Mon 16 Nov".
 *
 * Depends on: java.time, the device locale (for day and month names).
 * Must not: read the time zone itself; callers pass the zone from the Clock.
 * Reports use their own formats (SPEC 11); this file is for the UI only.
 */
package com.ritoghosh.ttt.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "14:05" */
fun formatTime(time: LocalTime): String = time.format(TIME_FORMAT)

/** "14:05" for an instant in [zone]. */
fun formatTime(instantMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(instantMs).atZone(zone).format(TIME_FORMAT)

/** "14:00–16:00" */
fun formatTimeRange(startMs: Long, endMs: Long, zone: ZoneId): String =
    formatTime(startMs, zone) + "–" + formatTime(endMs, zone)

/** "Mon 16 Nov", with day and month names in the device language. */
fun formatDayLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

/** "Sat 14:00" for an instant in [zone]. */
fun formatWeekdayTime(instantMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(instantMs).atZone(zone).format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()))

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

/**
 * A running clock: "24:59" or "1:05:09". [roundUp] is for countdowns, so 25:00 shows at the start
 * and 0:00 only when time is really up; counting up rounds down.
 */
fun formatClock(durationMs: Long, roundUp: Boolean = false): String {
    val wholeSeconds = if (roundUp) (durationMs + MS_PER_SECOND - 1) / MS_PER_SECOND else durationMs / MS_PER_SECOND
    val hours = wholeSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = (wholeSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val seconds = wholeSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** A total: "3h 35m", "25m", "0m". Whole minutes, rounded down (minutes are rounded only here, SPEC 6.6). */
fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / (MS_PER_SECOND * SECONDS_PER_MINUTE)
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
