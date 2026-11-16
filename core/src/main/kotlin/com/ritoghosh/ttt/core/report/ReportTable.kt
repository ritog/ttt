/**
 * ReportTable — one sheet of the report as plain data: a title, a header and rows of typed cells
 * (SPEC 11.3). ReportBuilder makes these; a ReportWriter (ReportWriter.kt) turns them into a file.
 *
 * Depends on: java.time only.
 * Must not: know about any file format. Bold, date and number styles are described here;
 * how a format shows them is the writer's business.
 */
package com.ritoghosh.ttt.core.report

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One cell. Writers must show Date, Time and DateTime as real spreadsheet dates, not text. */
sealed interface Cell {
    data class Text(val value: String, val bold: Boolean = false) : Cell

    /** [decimals] is 0 (whole number, e.g. minutes) or 1 (e.g. hours). */
    data class Number(val value: Double, val decimals: Int = 0) : Cell

    data class Date(val value: LocalDate) : Cell

    /** Time of day, to the minute. */
    data class Time(val value: LocalTime) : Cell

    /** Date and time of day, to the minute. */
    data class DateTime(val value: LocalDateTime) : Cell

    /** Nothing in this cell. */
    data object Empty : Cell
}

data class ReportTable(
    /** Sheet name; at most 31 characters (the xlsx limit). */
    val title: String,
    /** Column names; written in bold on the first row. */
    val header: List<String>,
    val rows: List<List<Cell>>,
    /**
     * True for a list of records: the header row is frozen and gets an autofilter (SPEC 11.1).
     * False for the Summary, which is key/value lines and small tables.
     */
    val isDataSheet: Boolean = true,
)

