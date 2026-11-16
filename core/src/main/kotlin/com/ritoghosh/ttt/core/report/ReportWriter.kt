/**
 * ReportWriter — turns report tables into one file (SPEC 11.3). XlsxWriter is the only one today;
 * a zip-of-CSVs writer could be swapped in if a spreadsheet app ever misbehaves.
 *
 * Depends on: ReportTable.kt, java.io.
 * Must not: open or close files itself; the caller owns the stream.
 *
 * Note for a future CSV writer: prefix any cell that begins with =, +, - or @ with a single
 * quote, so spreadsheet apps do not run it as a formula.
 */
package com.ritoghosh.ttt.core.report

import java.io.OutputStream

interface ReportWriter {
    /** Writes [tables] in order as one file to [output]. Does not close [output]. */
    fun write(tables: List<ReportTable>, output: OutputStream)
}
