/**
 * XlsxSheet — writes one worksheet part (xl/worksheets/sheetN.xml) for XlsxWriter: column widths,
 * a frozen bold header with an autofilter on data sheets, and the cells (SPEC 11.1, 11.3).
 *
 * Depends on: ReportTable.kt, XlsxText.kt, XlsxStyle (below).
 * Must not: write formulas or shared strings; every value is a plain value or an inline string.
 */
package com.ritoghosh.ttt.core.report

private const val MIN_COLUMN_WIDTH = 8
private const val MAX_COLUMN_WIDTH = 60
private const val COLUMN_PADDING = 2
private const val DATE_WIDTH = 10
private const val TIME_WIDTH = 5
private const val DATE_TIME_WIDTH = 16

/** Style indexes; they must match the cellXfs order in XlsxWriter's styles part. */
internal object XlsxStyle {
    const val BOLD = 1
    const val DATE = 2
    const val TIME = 3
    const val DATE_TIME = 4
    const val INTEGER = 5
    const val ONE_DECIMAL = 6
}

internal fun sheetXml(table: ReportTable): String {
    val columnCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0, 1)
    val lastCell = columnName(columnCount - 1) + (table.rows.size + 1)
    return buildString {
        append(XML_DECLARATION)
        append("<worksheet xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">")
        if (table.isDataSheet) {
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("</sheetView></sheetViews>")
        }
        appendColumns(columnWidths(table, columnCount))
        append("<sheetData>")
        appendRow(1, table.header.map { Cell.Text(it, bold = true) })
        table.rows.forEachIndexed { index, row -> appendRow(index + 2, row) }
        append("</sheetData>")
        if (table.isDataSheet) append("<autoFilter ref=\"A1:$lastCell\"/>")
        append("</worksheet>")
    }
}

/** The autofilter range as a formula reference, for the workbook's hidden defined name. */
internal fun filterReference(table: ReportTable, name: String): String {
    val columnCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0, 1)
    val quoted = "'" + name.replace("'", "''") + "'"
    return "$quoted!\$A\$1:\$${columnName(columnCount - 1)}\$${table.rows.size + 1}"
}

private fun StringBuilder.appendColumns(widths: List<Int>) {
    append("<cols>")
    widths.forEachIndexed { index, width ->
        append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
    }
    append("</cols>")
}

private fun StringBuilder.appendRow(rowNumber: Int, cells: List<Cell>) {
    append("<row r=\"$rowNumber\">")
    cells.forEachIndexed { index, cell -> appendCell(columnName(index) + rowNumber, cell) }
    append("</row>")
}

private fun StringBuilder.appendCell(ref: String, cell: Cell) {
    when (cell) {
        Cell.Empty -> Unit
        is Cell.Text -> {
            val style = if (cell.bold) " s=\"${XlsxStyle.BOLD}\"" else ""
            append("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">")
            append(escapeXml(cleanText(cell.value)))
            append("</t></is></c>")
        }
        is Cell.Number -> {
            val style = if (cell.decimals == 0) XlsxStyle.INTEGER else XlsxStyle.ONE_DECIMAL
            appendValue(ref, style, plainNumber(cell.value))
        }
        is Cell.Date -> appendValue(ref, XlsxStyle.DATE, excelSerial(cell.value).toString())
        is Cell.Time -> appendValue(ref, XlsxStyle.TIME, plainNumber(excelSerial(cell.value)))
        is Cell.DateTime -> appendValue(ref, XlsxStyle.DATE_TIME, plainNumber(excelSerial(cell.value)))
    }
}

private fun StringBuilder.appendValue(ref: String, style: Int, value: String) {
    append("<c r=\"$ref\" s=\"$style\"><v>").append(value).append("</v></c>")
}

/** Width in characters from the widest value in each column, kept between 8 and 60. */
private fun columnWidths(table: ReportTable, columnCount: Int): List<Int> = (0 until columnCount).map { column ->
    val headerWidth = table.header.getOrNull(column)?.length ?: 0
    val widest = table.rows.maxOfOrNull { row -> row.getOrNull(column)?.let(::displayWidth) ?: 0 } ?: 0
    (maxOf(headerWidth, widest) + COLUMN_PADDING).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
}

private fun displayWidth(cell: Cell): Int = when (cell) {
    Cell.Empty -> 0
    is Cell.Text -> cell.value.lineSequence().maxOfOrNull { it.length } ?: 0
    is Cell.Number -> plainNumber(cell.value).length
    is Cell.Date -> DATE_WIDTH
    is Cell.Time -> TIME_WIDTH
    is Cell.DateTime -> DATE_TIME_WIDTH
}
