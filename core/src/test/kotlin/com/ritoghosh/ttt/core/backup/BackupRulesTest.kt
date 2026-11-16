/**
 * BackupRulesTest — backup file names, pruning to the newest N, and when an auto-backup is due (SPEC 8).
 *
 * Depends on: core/backup/BackupRules.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupRulesTest {

    private val hour = 3_600_000L

    @Test
    fun `the file name follows SPEC 8_1`() {
        assertEquals("ttt-backup-2026-11-16-0905.db", backupFileName(LocalDateTime.parse("2026-11-16T09:05:59")))
    }

    @Test
    fun `only names this app writes count as backups`() {
        assertTrue(isBackupFileName("ttt-backup-2026-11-16-0905.db"))
        assertFalse(isBackupFileName("ttt-backup-2026-11-16-0905 (1).db"))
        assertFalse(isBackupFileName("notes.db"))
        assertFalse(isBackupFileName("ttt-backup-2026-11-16-0905.db.txt"))
    }

    @Test
    fun `pruning keeps the newest N and ignores other files`() {
        val names = listOf(
            "ttt-backup-2026-11-14-0900.db",
            "photo.jpg",
            "ttt-backup-2026-11-16-0900.db",
            "ttt-backup-2026-11-15-0900.db",
            "ttt-backup-2026-11-13-0900.db",
        )
        assertEquals(
            listOf("ttt-backup-2026-11-14-0900.db", "ttt-backup-2026-11-13-0900.db"),
            backupsToDelete(names, keep = 2),
        )
    }

    @Test
    fun `nothing is deleted when there are no more than N`() {
        assertTrue(backupsToDelete(listOf("ttt-backup-2026-11-16-0900.db"), keep = 7).isEmpty())
    }

    @Test
    fun `at least one backup is always kept`() {
        val names = listOf("ttt-backup-2026-11-16-0900.db", "ttt-backup-2026-11-15-0900.db")
        assertEquals(listOf("ttt-backup-2026-11-15-0900.db"), backupsToDelete(names, keep = 0))
    }

    @Test
    fun `an auto-backup is due the first time and after 24 hours`() {
        val now = 1_790_000_000_000L
        assertTrue(isAutoBackupDue(null, now))
        assertFalse(isAutoBackupDue(now - 23 * hour, now))
        assertTrue(isAutoBackupDue(now - 24 * hour, now))
    }

    @Test
    fun `a clock set back in time makes it due rather than blocking it for days`() {
        val now = 1_790_000_000_000L
        assertTrue(isAutoBackupDue(now + 5 * hour, now))
    }
}
