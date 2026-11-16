/**
 * TttApplication — the process-wide entry point; owns the single AppContainer, and installs a
 * restored backup before anything opens the database.
 *
 * Depends on: AppContainer, data/backup/RestoreService.kt (installPendingRestore).
 * Must not: do slow work at startup. Installing a restore is one file rename; creating
 * notification channels is a quick system call; the database itself opens lazily.
 */
package com.ritoghosh.ttt

import android.app.Application
import com.ritoghosh.ttt.data.backup.PENDING_RESTORE_FILE_NAME
import com.ritoghosh.ttt.data.backup.databaseFolder
import com.ritoghosh.ttt.data.backup.installPendingRestore
import com.ritoghosh.ttt.data.db.AppDatabase
import java.io.File

class TttApplication : Application() {

    /** Built once, on first use, and kept for the life of the process. */
    val container: AppContainer by lazy { AppContainer(this) }

    /** True once if this start installed a restored backup; MainActivity reads it to say so. */
    var restoredOnThisStart: Boolean = false
        private set

    fun consumeRestoredNotice(): Boolean = restoredOnThisStart.also { restoredOnThisStart = false }

    override fun onCreate() {
        super.onCreate()
        // A backup checked in the previous run replaces ttt.db now, while nothing has it open (SPEC 8.3).
        val databaseFile = getDatabasePath(AppDatabase.FILE_NAME)
        val pendingFile = File(databaseFolder(this), PENDING_RESTORE_FILE_NAME)
        restoredOnThisStart = installPendingRestore(databaseFile, pendingFile)
        // Channels must exist before the first notification, including one posted by the alarm receiver.
        container.notifications.createChannels()
    }
}
