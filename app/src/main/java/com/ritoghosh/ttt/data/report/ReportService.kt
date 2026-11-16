/**
 * ReportService — makes the .xlsx report: reads every task, block and session in one transaction,
 * builds the six sheets (core ReportBuilder) and writes them to a document the user picked (SPEC 11).
 *
 * Depends on: data/db (DAOs, mappers), core/report (buildReport, XlsxWriter), core/time, Android ContentResolver.
 * Must not: keep the report anywhere; it goes straight to the chosen document. It is one-way:
 * nothing reads a report back.
 */
package com.ritoghosh.ttt.data.report

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.ritoghosh.ttt.core.report.ReportData
import com.ritoghosh.ttt.core.report.ReportRange
import com.ritoghosh.ttt.core.report.XlsxWriter
import com.ritoghosh.ttt.core.report.buildReport
import com.ritoghosh.ttt.core.report.reportFileName
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.localDateOf
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.toBlock
import com.ritoghosh.ttt.data.db.toEntry
import com.ritoghosh.ttt.data.db.toTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

private const val TAG = "ReportService"

/** The MIME type of an .xlsx file, for the "Save as" picker. */
const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

enum class ReportResult { SAVED, FAILED }

class ReportService(
    private val context: Context,
    private val database: AppDatabase,
    private val clock: Clock,
) {

    fun today(): LocalDate = localDateOf(clock.nowMs(), clock.zone())

    /** "ttt-report-2026-11-16.xlsx". */
    fun suggestedFileName(): String = reportFileName(today())

    suspend fun writeReportTo(destination: Uri, range: ReportRange): ReportResult = withContext(Dispatchers.IO) {
        try {
            val tables = buildReport(loadData(), range, clock)
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: return@withContext ReportResult.FAILED
            output.use { XlsxWriter().write(tables, it) }
            ReportResult.SAVED
        } catch (error: IOException) {
            Log.w(TAG, "Writing the report failed", error)
            ReportResult.FAILED
        } catch (error: SecurityException) {
            Log.w(TAG, "No permission to write the report", error)
            ReportResult.FAILED
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "The report destination is not valid", error)
            ReportResult.FAILED
        }
    }

    /** One transaction, so a timer finishing mid-export cannot leave the three lists out of step. */
    private suspend fun loadData(): ReportData = database.withTransaction {
        ReportData(
            tasks = database.taskDao().getAll().map { it.toTask() },
            blocks = database.blockDao().getAll().map { it.toBlock() },
            entries = database.entryDao().getAll().map { it.toEntry() },
        )
    }
}
