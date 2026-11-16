/**
 * XlsxText — small helpers for XlsxWriter: making text safe for XML, column letters, sheet names,
 * and Excel date serial numbers.
 *
 * Depends on: Constants.kt, java.time.
 * Must not: change the meaning of text beyond removing characters XML cannot hold.
 */
package com.ritoghosh.ttt.core.report

import com.ritoghosh.ttt.core.REPORT_CELL_MAX_LENGTH
import com.ritoghosh.ttt.core.REPORT_SHEET_NAME_MAX_LENGTH
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val LETTERS = 26
private const val SECONDS_PER_DAY = 86_400.0

/**
 * Days between Excel's day 0 (1899-12-30, which absorbs Excel's 1900 leap-year bug) and
 * 1970-01-01. Correct for every date from 1900-03-01 on.
 */
private const val EXCEL_SERIAL_OF_UNIX_EPOCH = 25_569L

/** Characters a sheet name may not contain. */
private val SHEET_NAME_FORBIDDEN = Regex("""[\[\]:*?/\\]""")

/**
 * Removes characters XML 1.0 cannot carry (most control characters, lone surrogates) and cuts the
 * text at [maxLength] characters without splitting an emoji. Tabs and line breaks are kept.
 */
internal fun cleanText(raw: String, maxLength: Int = REPORT_CELL_MAX_LENGTH): String {
    val result = StringBuilder(minOf(raw.length, maxLength))
    var index = 0
    while (index < raw.length) {
        val codePoint = raw.codePointAt(index)
        val width = Character.charCount(codePoint)
        if (isXmlChar(codePoint)) {
            if (result.length + width > maxLength) break
            result.appendCodePoint(codePoint)
        }
        index += width
    }
    return result.toString()
}

private fun isXmlChar(codePoint: Int): Boolean =
    codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD ||
        codePoint in 0x20..0xD7FF || codePoint in 0xE000..0xFFFD || codePoint in 0x10000..0x10FFFF

/** Escapes text for use inside an element or a double-quoted attribute. */
internal fun escapeXml(text: String): String {
    if (text.none { it == '&' || it == '<' || it == '>' || it == '"' }) return text
    return buildString(text.length + 16) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(char)
            }
        }
    }
}

/** 0 → "A", 25 → "Z", 26 → "AA". */
internal fun columnName(index: Int): String {
    var remaining = index + 1
    val name = StringBuilder()
    while (remaining > 0) {
        val digit = (remaining - 1) % LETTERS
        name.insert(0, 'A' + digit)
        remaining = (remaining - 1) / LETTERS
    }
    return name.toString()
}

/** A legal sheet name: forbidden characters become spaces, at most 31 characters, never empty. */
internal fun sheetName(title: String): String =
    cleanText(title.replace(SHEET_NAME_FORBIDDEN, " "), REPORT_SHEET_NAME_MAX_LENGTH).trim().ifEmpty { "Sheet" }

internal fun excelSerial(date: LocalDate): Long = date.toEpochDay() + EXCEL_SERIAL_OF_UNIX_EPOCH

internal fun excelSerial(time: LocalTime): Double = time.toSecondOfDay() / SECONDS_PER_DAY

internal fun excelSerial(dateTime: LocalDateTime): Double =
    excelSerial(dateTime.toLocalDate()) + excelSerial(dateTime.toLocalTime())

/** A number as XML text without exponent notation ("0.000694…", not "6.94E-4"). */
internal fun plainNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
