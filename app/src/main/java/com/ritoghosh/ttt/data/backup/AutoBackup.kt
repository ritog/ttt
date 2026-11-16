/**
 * AutoBackup — when the app goes to the background and the last auto-backup is 24 hours old,
 * writes a snapshot into the folder the user chose and keeps only the newest N (SPEC 8.4).
 *
 * Depends on: BackupService, data/repo/SettingsRepository.kt, core/backup/BackupRules.kt, Android DocumentsContract.
 * Must not: use WorkManager or a background service; it runs only when the app is left.
 * Must not: delete files other than its own (names matching ttt-backup-YYYY-MM-DD-HHmm.db).
 *
 * Folder access uses DocumentsContract directly (no extra library). A tool like Syncthing or a
 * cloud app can then carry the files off the phone.
 */
package com.ritoghosh.ttt.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.ritoghosh.ttt.core.backup.backupsToDelete
import com.ritoghosh.ttt.core.backup.isAutoBackupDue
import com.ritoghosh.ttt.core.settings.AUTO_BACKUP_FOLDER_URI_KEY
import com.ritoghosh.ttt.core.settings.LAST_AUTO_BACKUP_AT_KEY
import com.ritoghosh.ttt.core.settings.NumberSetting
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AutoBackup"
private const val BACKUP_MIME_TYPE = "application/octet-stream" // keeps the ".db" name as given

enum class AutoBackupResult { NOT_SET_UP, NOT_DUE, DONE, FAILED }

class AutoBackup(
    private val context: Context,
    private val backupService: BackupService,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    /** Outlives the activity, so the backup finishes after the app is left. */
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Called from MainActivity.onStop. Returns at once; the work runs in the background. */
    fun runIfDue() {
        scope.launch {
            try {
                backUpIfDue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) { // a provider or database surprise must never crash the app
                Log.w(TAG, "Auto-backup failed", error)
            }
        }
    }

    /** One backup at a time; a failure leaves the "last backup" time unchanged, so it is retried next time. */
    suspend fun backUpIfDue(): AutoBackupResult = mutex.withLock {
        val values = settingsRepository.currentValues()
        val folder = values[AUTO_BACKUP_FOLDER_URI_KEY]?.let(Uri::parse)
            ?: return@withLock AutoBackupResult.NOT_SET_UP
        val nowMs = clock.nowMs()
        val lastBackupAtMs = values[LAST_AUTO_BACKUP_AT_KEY]?.toLongOrNull()
        if (!isAutoBackupDue(lastBackupAtMs, nowMs)) return@withLock AutoBackupResult.NOT_DUE
        val name = backupService.suggestedFileName()
        val saved = backupService.withSnapshot { snapshot ->
            val document = createDocument(folder, name) ?: return@withSnapshot false
            backupService.copyToDocument(snapshot, document)
        }
        if (saved != BackupResult.SAVED) return@withLock AutoBackupResult.FAILED
        settingsRepository.setLastAutoBackupAt(nowMs)
        try {
            prune(folder, NumberSetting.AUTO_BACKUP_KEEP.readFrom(values))
        } catch (error: Exception) { // the new backup is saved; extra old files are only clutter
            Log.w(TAG, "Could not list old backups", error)
        }
        AutoBackupResult.DONE
    }

    private fun createDocument(treeUri: Uri, displayName: String): Uri? {
        val folderId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderId)
        return DocumentsContract.createDocument(context.contentResolver, parent, BACKUP_MIME_TYPE, displayName)
    }

    /** Deletes our own oldest backups beyond [keep]. A file that cannot be deleted is left and logged. */
    private fun prune(treeUri: Uri, keep: Int) {
        val children = listChildren(treeUri)
        val toDelete = backupsToDelete(children.keys.toList(), keep)
        toDelete.forEach { name ->
            val documentId = children[name] ?: return@forEach
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (error: Exception) { // providers throw various types; one stuck file must not stop the rest
                Log.w(TAG, "Could not delete old backup $name", error)
            }
        }
    }

    /** Display name → document id of the files directly inside the folder. */
    private fun listChildren(treeUri: Uri): Map<String, String> {
        val folderId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val result = mutableMapOf<String, String>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(1)] = cursor.getString(0)
        }
        return result
    }
}
