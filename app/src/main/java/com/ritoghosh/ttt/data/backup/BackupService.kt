/**
 * BackupService — writes a backup: a consistent SQLite snapshot made with VACUUM INTO, copied to
 * a place the user picked (SPEC 8.1, 8.2). Also used by AutoBackup.
 *
 * Depends on: data/db/AppDatabase.kt, core/backup/BackupRules.kt, core/time, Android ContentResolver.
 * Must not: write anywhere without the system picker or a folder the user granted; there are
 * no storage permissions.
 *
 * VACUUM INTO is safe while the app is running: SQLite writes a complete copy of the database
 * as it is at that moment. It needs SQLite 3.27+, which every supported Android version has.
 */
package com.ritoghosh.ttt.data.backup

import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.util.Log
import com.ritoghosh.ttt.core.backup.backupFileName
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.zonedDateTimeOf
import com.ritoghosh.ttt.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "BackupService"
private const val SNAPSHOT_FILE_NAME = "ttt-snapshot.db"

enum class BackupResult { SAVED, FAILED }

class BackupService(
    private val context: Context,
    private val database: AppDatabase,
    private val clock: Clock,
) {

    /** "ttt-backup-2026-11-16-1830.db" for now, in the device's zone. */
    fun suggestedFileName(): String =
        backupFileName(zonedDateTimeOf(clock.nowMs(), clock.zone()).toLocalDateTime())

    /** Snapshot → the document at [destination] (from the system "Save as" picker). */
    suspend fun writeBackupTo(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        withSnapshot { snapshot -> copyToDocument(snapshot, destination) }
    }

    /**
     * Makes a snapshot in the app's cache, hands it to [use], and always deletes it afterwards.
     * Returns FAILED (and logs why) if the snapshot or [use] fails.
     */
    internal fun withSnapshot(use: (File) -> Boolean): BackupResult {
        val snapshot = File(context.cacheDir, SNAPSHOT_FILE_NAME)
        return try {
            writeSnapshot(snapshot)
            if (use(snapshot)) BackupResult.SAVED else BackupResult.FAILED
        } catch (error: SQLException) {
            Log.w(TAG, "Snapshot failed", error)
            BackupResult.FAILED
        } catch (error: IOException) {
            Log.w(TAG, "Writing the backup failed", error)
            BackupResult.FAILED
        } catch (error: SecurityException) {
            Log.w(TAG, "No permission to write the backup", error)
            BackupResult.FAILED
        } catch (error: IllegalArgumentException) {
            // A folder URI the provider no longer accepts (e.g. the folder was deleted).
            Log.w(TAG, "The backup destination is no longer valid", error)
            BackupResult.FAILED
        } finally {
            snapshot.delete()
        }
    }

    /** VACUUM INTO refuses to overwrite, so any old file at [target] is removed first. */
    fun writeSnapshot(target: File) {
        target.delete()
        database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf<Any?>(target.absolutePath))
    }

    /** Copies [snapshot] into a document; "wt" truncates a file being overwritten. */
    internal fun copyToDocument(snapshot: File, destination: Uri): Boolean {
        val output = context.contentResolver.openOutputStream(destination, "wt") ?: return false
        output.use { out -> snapshot.inputStream().use { it.copyTo(out) } }
        return true
    }
}
