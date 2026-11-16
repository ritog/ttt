/**
 * BackupRestoreTest — backup → restore round trip into an empty app, and the refusals: an app
 * with data, a file from a newer schema, a file that is not a backup (SPEC 8, 16 "M6").
 *
 * Depends on: a device or emulator, file-backed AppDatabase instances in a temporary folder.
 * Must not: touch the real app database file; everything lives under the test's own cache folder.
 */
package com.ritoghosh.ttt.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.DATABASE_VERSION
import com.ritoghosh.ttt.data.db.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = object : Clock {
        override fun nowMs(): Long = 1_800_000_000_000L
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private lateinit var dir: File
    private val openDatabases = mutableListOf<AppDatabase>()

    @Before
    fun makeFolder() {
        dir = File(context.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun cleanUp() {
        openDatabases.forEach { it.close() }
        dir.deleteRecursively()
    }

    @Test
    fun roundTripRestoresDataAndClearsWhatBelongsToTheOldPhone() = runBlocking {
        val backup = backupOfFilledDatabase()
        val result = restoreServiceFor(newDatabase("empty.db")).prepare(Uri.fromFile(backup))

        val pending = (result as RestoreCheck.Ready).pendingFile
        val live = File(dir, "live.db").apply { writeText("old database") }
        assertTrue(installPendingRestore(live, pending))
        assertFalse(pending.exists())

        val restored = SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READONLY)
        restored.use { db ->
            val titles = strings(db, "SELECT title FROM task ORDER BY id")
            assertEquals(listOf("Write chapter 3", "Read paper"), titles)
            assertEquals(listOf("deep"), strings(db, "SELECT name FROM tag"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM block"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM entry"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM timer_state"))
            val keys = strings(db, "SELECT `key` FROM setting ORDER BY `key`")
            assertEquals(listOf(NumberSetting.AUTO_BACKUP_KEEP.key, NumberSetting.WORK_MINUTES.key), keys)
            assertEquals(DATABASE_VERSION, db.version)
        }
    }

    @Test
    fun restoredFileOpensWithRoom() = runBlocking {
        val backup = backupOfFilledDatabase()
        val result = restoreServiceFor(newDatabase("empty.db")).prepare(Uri.fromFile(backup))
        val pending = (result as RestoreCheck.Ready).pendingFile
        val room = Room.databaseBuilder(context, AppDatabase::class.java, pending.absolutePath).build()
        openDatabases.add(room)
        assertEquals(4, room.taskDao().countRecords()) // 2 tasks + 1 block + 1 session
    }

    @Test
    fun refusesWhenTheAppHasData() = runBlocking {
        val backup = backupOfFilledDatabase()
        val live = newDatabase("live.db")
        live.openHelper.writableDatabase.execSQL(
            "INSERT INTO task (id, title, is_done, created_at, note) VALUES (1, 'Mine', 0, 0, '')",
        )
        val service = restoreServiceFor(live)
        assertFalse(service.isAppEmpty())
        assertEquals(RestoreCheck.Refused(RestoreProblem.NOT_EMPTY), service.prepare(Uri.fromFile(backup)))
        assertFalse(File(dir, PENDING_RESTORE_FILE_NAME).exists())
    }

    @Test
    fun refusesAFileFromANewerSchema() = runBlocking {
        val backup = backupOfFilledDatabase()
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = DATABASE_VERSION + 1
        }
        val result = restoreServiceFor(newDatabase("empty.db")).prepare(Uri.fromFile(backup))
        assertEquals(RestoreCheck.Refused(RestoreProblem.NEWER_VERSION), result)
        assertFalse(File(dir, PENDING_RESTORE_FILE_NAME).exists())
    }

    @Test
    fun refusesAFileThatIsNotABackup() = runBlocking {
        val service = restoreServiceFor(newDatabase("empty.db"))
        val text = File(dir, "notes.txt").apply { writeText("this is not a database, just some text ".repeat(50)) }
        assertEquals(RestoreCheck.Refused(RestoreProblem.NOT_A_BACKUP), service.prepare(Uri.fromFile(text)))

        val otherSqlite = File(dir, "other.db")
        SQLiteDatabase.openOrCreateDatabase(otherSqlite, null).use { it.execSQL("CREATE TABLE notes (body TEXT)") }
        assertEquals(RestoreCheck.Refused(RestoreProblem.NOT_A_BACKUP), service.prepare(Uri.fromFile(otherSqlite)))
    }

    @Test
    fun refusesAFileThatCannotBeRead() = runBlocking {
        val missing = Uri.fromFile(File(dir, "missing.db"))
        val result = restoreServiceFor(newDatabase("empty.db")).prepare(missing)
        assertEquals(RestoreCheck.Refused(RestoreProblem.UNREADABLE), result)
    }

    @Test
    fun nothingPendingMeansNothingInstalled() {
        val live = File(dir, "live.db").apply { writeText("current") }
        assertFalse(installPendingRestore(live, File(dir, PENDING_RESTORE_FILE_NAME)))
        assertEquals("current", live.readText())
    }

    @Test
    fun suggestedNameFollowsThePattern() {
        val service = BackupService(context, newDatabase("empty.db"), clock)
        assertEquals("ttt-backup-2027-01-15-0800.db", service.suggestedFileName())
    }

    /** A database with two tasks, a tag, a block, a session, a running timer and settings; returns its snapshot. */
    private suspend fun backupOfFilledDatabase(): File {
        val source = newDatabase("source.db")
        val sql = source.openHelper.writableDatabase
        sql.execSQL("INSERT INTO task (id, title, is_done, created_at, note) VALUES (1, 'Write chapter 3', 0, 0, '')")
        sql.execSQL("INSERT INTO task (id, title, is_done, created_at, note) VALUES (2, 'Read paper', 1, 0, 'done')")
        sql.execSQL("INSERT INTO tag (id, name) VALUES (1, 'deep')")
        sql.execSQL("INSERT INTO task_tag (task_id, tag_id) VALUES (1, 1)")
        sql.execSQL("INSERT INTO block (id, task_id, start_at, end_at) VALUES (1, 1, 0, 3600000)")
        sql.execSQL(
            "INSERT INTO entry (id, task_id, started_at, ended_at, paused_ms, note) VALUES (1, 1, 0, 1500000, 0, '')",
        )
        source.timerStateDao().save(TimerState.IDLE.toEntity())
        sql.execSQL("INSERT INTO setting (`key`, `value`) VALUES ('${NumberSetting.WORK_MINUTES.key}', '50')")
        sql.execSQL("INSERT INTO setting (`key`, `value`) VALUES ('${NumberSetting.AUTO_BACKUP_KEEP.key}', '3')")
        sql.execSQL("INSERT INTO setting (`key`, `value`) VALUES ('$AUTO_BACKUP_FOLDER_URI_KEY', 'content://old-phone')")
        sql.execSQL("INSERT INTO setting (`key`, `value`) VALUES ('$LAST_AUTO_BACKUP_AT_KEY', '123')")
        val snapshot = File(dir, "ttt-backup.db")
        BackupService(context, source, clock).writeSnapshot(snapshot)
        assertTrue(snapshot.length() > 0)
        return snapshot
    }

    private fun newDatabase(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, File(dir, name).absolutePath).build()
            .also(openDatabases::add)

    private fun restoreServiceFor(live: AppDatabase) = RestoreService(context, live, workDir = dir)

    private fun strings(db: SQLiteDatabase, query: String): List<String> =
        db.rawQuery(query, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun count(db: SQLiteDatabase, query: String): Int = db.rawQuery(query, null).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
