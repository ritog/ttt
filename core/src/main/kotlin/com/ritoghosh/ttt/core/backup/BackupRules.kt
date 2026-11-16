/**
 * BackupRules — backup file names, which old auto-backups to delete, and when an auto-backup is due (SPEC 8).
 *
 * Depends on: core/Constants.kt, java.time.
 * Must not: touch files. The data layer lists, writes and deletes; this file only decides.
 */
package com.ritoghosh.ttt.core.backup

import com.ritoghosh.ttt.core.AUTO_BACKUP_INTERVAL_HOURS
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FILE_NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
private val BACKUP_FILE_NAME = Regex("""ttt-backup-\d{4}-\d{2}-\d{2}-\d{4}\.db""")
private const val MS_PER_HOUR = 3_600_000L

/** "ttt-backup-2026-11-16-1830.db" for a local date-time (SPEC 8.1). */
fun backupFileName(localTime: LocalDateTime): String = "ttt-backup-${localTime.format(FILE_NAME_TIME)}.db"

/** True only for names this app writes, so pruning never touches the user's other files. */
fun isBackupFileName(name: String): Boolean = BACKUP_FILE_NAME.matches(name)

/**
 * Names to delete so that only the newest [keep] backups remain. The timestamp in the name sorts
 * in time order, so the name alone decides; other files in the folder are ignored.
 */
fun backupsToDelete(names: List<String>, keep: Int): List<String> =
    names.filter(::isBackupFileName).sortedDescending().drop(keep.coerceAtLeast(1))

/**
 * An auto-backup is due if there has never been one, if the last one is 24 hours old or more,
 * or if the clock now reads earlier than the last one (the clock was changed).
 */
fun isAutoBackupDue(lastBackupAtMs: Long?, nowMs: Long): Boolean =
    lastBackupAtMs == null ||
        nowMs < lastBackupAtMs ||
        nowMs - lastBackupAtMs >= AUTO_BACKUP_INTERVAL_HOURS * MS_PER_HOUR
