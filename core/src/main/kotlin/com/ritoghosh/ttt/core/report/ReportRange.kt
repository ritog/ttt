/**
 * ReportRange — which days a report covers (SPEC 11): all time, or whole local days from one
 * date to another, both included ("This month" and "Custom" are both day ranges).
 *
 * Depends on: core/time (DayRange, TimeRange).
 * Must not: read the clock; "this month" is worked out from a date the caller passes.
 *
 * A session or block belongs to the range if it **starts** inside it, the same rule the Log uses.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.time.TimeRange
import com.ritoghosh.ttt.core.time.startOfDayMs
import java.time.LocalDate
import java.time.ZoneId

sealed interface ReportRange {
    data object AllTime : ReportRange

    /** From the start of [first] to the end of [last], local days. */
    data class Days(val first: LocalDate, val last: LocalDate) : ReportRange {
        init {
            require(!last.isBefore(first)) { "Report range ends ($last) before it starts ($first)" }
        }
    }
}

/** The calendar month that contains [today]. */
fun thisMonth(today: LocalDate): ReportRange.Days =
    ReportRange.Days(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))

/** The instants covered, or null for all time. Days are measured in [zone] (DST-safe). */
fun ReportRange.timeRange(zone: ZoneId): TimeRange? = when (this) {
    ReportRange.AllTime -> null
    is ReportRange.Days -> TimeRange(startOfDayMs(first, zone), startOfDayMs(last.plusDays(1), zone))
}
