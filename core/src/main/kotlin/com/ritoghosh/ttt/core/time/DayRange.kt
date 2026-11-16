/**
 * DayRange — turns instants into local days, weeks and months, and back into TimeRanges (SPEC 4.3).
 *
 * Depends on: TimeRange.kt, java.time.
 * Must not: read the system time zone; the zone is always passed in (from a Clock).
 *
 * Rules: weeks start on Monday; a record belongs to the local day on which it starts.
 * Day lengths come from java.time, so days with a daylight-saving change are 23 or 25 hours.
 */
package com.ritoghosh.ttt.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Weeks start on Monday (SPEC 4.3). */
val FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

/** The instant [instantMs] as a date-time in [zone]. */
fun zonedDateTimeOf(instantMs: Long, zone: ZoneId): ZonedDateTime =
    Instant.ofEpochMilli(instantMs).atZone(zone)

/** The local date on which [instantMs] falls in [zone]. */
fun localDateOf(instantMs: Long, zone: ZoneId): LocalDate = zonedDateTimeOf(instantMs, zone).toLocalDate()

/** Epoch ms of the first instant of [date] in [zone]. */
fun startOfDayMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

/** The whole local day [date], from its midnight to the next midnight. */
fun dayRange(date: LocalDate, zone: ZoneId): TimeRange =
    TimeRange(startOfDayMs(date, zone), startOfDayMs(date.plusDays(1), zone))

/** The Monday-to-Sunday week containing [date]. */
fun weekRange(date: LocalDate, zone: ZoneId): TimeRange {
    val monday = date.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK))
    return TimeRange(startOfDayMs(monday, zone), startOfDayMs(monday.plusWeeks(1), zone))
}

/** The calendar month containing [date]. */
fun monthRange(date: LocalDate, zone: ZoneId): TimeRange {
    val firstDay = date.withDayOfMonth(1)
    return TimeRange(startOfDayMs(firstDay, zone), startOfDayMs(firstDay.plusMonths(1), zone))
}
