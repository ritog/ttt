/**
 * RestoreService — checks and prepares a backup file for restoring (SPEC 8.3). The checked file is
 * installed by installPendingRestore when the app restarts, before anything opens the database.
 *
 * Depends on: data/db (AppDatabase, migrations), core/settings (keys), Android SQLite and ContentResolver.
 * Must not: touch the live database except to count its records. Any failure leaves it as it was.
 *
 * Steps: the app must be empty → copy the file into private storage → it must open as SQLite,
 * pass PRAGMA integrity_check, contain the task table and not be from a newer schema → open it
 * with Room, which runs the normal migrations for older backups → reset what belongs to the old
 * phone (timer state, auto-backup folder and time) → mark it "pending" for the restart.
 */
package com.ritoghosh.ttt.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.data.db.ALL_MIGRATIONS
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.DATABASE_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "RestoreService"

/** The copied backup while it is being checked. */
private const val CANDIDATE_FILE_NAME = "restore-candidate.db"

/** A checked backup waiting to replace ttt.db at the next start. */
const val PENDING_RESTORE_FILE_NAME = "pending-restore.db"

enum class RestoreProblem { NOT_EMPTY, UNREADABLE, NOT_A_BACKUP, DAMAGED, NEWER_VERSION }

sealed interface RestoreCheck {
    /** Checked and waiting in [pendingFile]; restart the app to install it. */
    data class Ready(val pendingFile: File) : RestoreCheck
    data class Refused(val problem: RestoreProblem) : RestoreCheck
}

class RestoreService(
    private val context: Context,
    private val liveDatabase: AppDatabase,
    /** Where the candidate and pending files go: the app's databases folder (a temp folder in tests). */
    private val workDir: File = databaseFolder(context),
) {

    /** Restore works only on an app with no tasks, blocks or sessions (no merging in v1). */
    suspend fun isAppEmpty(): Boolean = liveDatabase.taskDao().countRecords() == 0

    suspend fun prepare(source: Uri): RestoreCheck {
        if (!isAppEmpty()) return RestoreCheck.Refused(RestoreProblem.NOT_EMPTY)
        return withContext(Dispatchers.IO) { prepareCandidate(source) }
    }

    private fun prepareCandidate(source: Uri): RestoreCheck {
        val candidate = File(workDir, CANDIDATE_FILE_NAME)
        try {
            deleteWithSidecars(candidate)
            if (!copyIn(source, candidate)) return RestoreCheck.Refused(RestoreProblem.UNREADABLE)
            inspect(candidate)?.let { return RestoreCheck.Refused(it) }
            migrateAndReset(candidate)?.let { return RestoreCheck.Refused(it) }
            val pending = File(workDir, PENDING_RESTORE_FILE_NAME)
            deleteWithSidecars(pending)
            if (!candidate.renameTo(pending)) return RestoreCheck.Refused(RestoreProblem.UNREADABLE)
            return RestoreCheck.Ready(pending)
        } finally {
            deleteWithSidecars(candidate) // nothing left behind after a refusal
        }
    }

    private fun copyIn(source: Uri, target: File): Boolean = try {
        val input = context.contentResolver.openInputStream(source)
        input?.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        input != null
    } catch (error: IOException) {
        Log.w(TAG, "Could not read the backup file", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "No permission to read the backup file", error)
        false
    }

    /**
     * The checks that need no Room. Opened read-write because it is our own copy, and with an
     * error handler that does nothing, because Android's default one deletes "corrupt" files.
     */
    private fun inspect(file: File): RestoreProblem? {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE) { }
        } catch (notSqlite: SQLiteException) {
            return RestoreProblem.NOT_A_BACKUP
        }
        return try {
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val hasTaskTable = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'task'",
                null,
            ).use { it.count > 0 }
            when {
                integrity != "ok" -> RestoreProblem.DAMAGED
                !hasTaskTable || db.version < 1 -> RestoreProblem.NOT_A_BACKUP
                db.version > DATABASE_VERSION -> RestoreProblem.NEWER_VERSION
                else -> null
            }
        } catch (unreadable: SQLiteException) {
            RestoreProblem.NOT_A_BACKUP
        } finally {
            db.close()
        }
    }

    /**
     * Opens the copy with Room: older backups go through the normal migrations, and Room checks
     * the schema. Then clears what belongs to the old phone (SPEC 8.3). TRUNCATE journal mode so
     * no -wal file is left next to it.
     */
    private fun migrateAndReset(file: File): RestoreProblem? {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        return try {
            val db = room.openHelper.writableDatabase
            db.execSQL("DELETE FROM timer_state") // a timer running on the old phone must not create a session here
            db.execSQL(
                "DELETE FROM setting WHERE `key` IN (?, ?)",
                arrayOf<Any?>(AUTO_BACKUP_FOLDER_URI_KEY, LAST_AUTO_BACKUP_AT_KEY),
            )
            null
        } catch (wrongSchema: IllegalStateException) {
            Log.w(TAG, "Backup schema could not be opened", wrongSchema)
            RestoreProblem.DAMAGED
        } catch (broken: SQLiteException) {
            Log.w(TAG, "Backup could not be migrated", broken)
            RestoreProblem.DAMAGED
        } finally {
            room.close()
        }
    }
}

/** The folder Android keeps the app's databases in. */
fun databaseFolder(context: Context): File =
    context.getDatabasePath(AppDatabase.FILE_NAME).parentFile ?: context.filesDir

/**
 * Called at app start, before the database is opened: if a checked backup is waiting, it replaces
 * ttt.db (an atomic rename in the same folder). Returns true if one was installed. If the rename
 * fails, the waiting copy is dropped and the current database stays; the user still has the
 * original backup file.
 */
fun installPendingRestore(databaseFile: File, pendingFile: File): Boolean {
    if (!pendingFile.exists()) return false
    listOf("-wal", "-shm", "-journal").forEach { File(databaseFile.path + it).delete() }
    if (pendingFile.renameTo(databaseFile)) return true
    Log.e(TAG, "Could not install the restored database; keeping the current one")
    deleteWithSidecars(pendingFile)
    return false
}

/** Deletes a database file and the journal files SQLite may keep next to it. */
private fun deleteWithSidecars(file: File) {
    listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
}
