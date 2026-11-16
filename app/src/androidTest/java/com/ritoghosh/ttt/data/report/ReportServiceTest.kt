/**
 * ReportServiceTest — the export path on a device: rows come out of Room, through ReportBuilder
 * and XlsxWriter, into a file with six worksheets that contains the task's name (SPEC 11).
 *
 * Depends on: a device or emulator, an in-memory AppDatabase, a file in the test's cache folder.
 * Must not: check sheet contents in detail; core's ReportBuilderTest and XlsxWriterTest do that.
 */
package com.ritoghosh.ttt.data.report

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.report.ReportRange
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ReportServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = object : Clock {
        override fun nowMs(): Long = 1_800_000_000_000L // 2027-01-15 08:00 UTC
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private lateinit var database: AppDatabase
    private lateinit var output: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        output = File(context.cacheDir, "report-test.xlsx").apply { delete() }
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO task (id, title, is_done, created_at, note) VALUES (1, 'Write chapter', 0, 0, '')")
        sql.execSQL("INSERT INTO tag (id, name) VALUES (1, 'book')")
        sql.execSQL("INSERT INTO task_tag (task_id, tag_id) VALUES (1, 1)")
        sql.execSQL("INSERT INTO block (id, task_id, start_at, end_at) VALUES (1, 1, 1799996400000, 1800003600000)")
        sql.execSQL(
            "INSERT INTO entry (id, task_id, started_at, ended_at, paused_ms, note) " +
                "VALUES (1, 1, 1799996400000, 1799997900000, 0, '')",
        )
    }

    @After
    fun tearDown() {
        database.close()
        output.delete()
    }

    @Test
    fun writesAWorkbookWithSixSheets() = runBlocking {
        val service = ReportService(context, database, clock)
        assertEquals("ttt-report-2027-01-15.xlsx", service.suggestedFileName())

        assertEquals(ReportResult.SAVED, service.writeReportTo(Uri.fromFile(output), ReportRange.AllTime))

        ZipFile(output).use { zip ->
            val sheets = zip.entries().toList().map { it.name }.filter { it.startsWith("xl/worksheets/") }
            assertEquals(6, sheets.size)
            val tasksSheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml")).reader().readText()
            assertTrue(tasksSheet.contains("Write chapter"))
            assertTrue(tasksSheet.contains("book"))
        }
    }
}
