/**
 * Entities — the Room tables, column for column as in SPEC 5.1 and docs/DATA.md.
 *
 * Depends on: Room annotations only.
 * Must not: be used outside the data layer. The rest of the app uses core/model classes,
 * converted in Mappers.kt. Changing anything here changes the schema: that needs a migration,
 * a migration test and a DATA.md update (docs/CHANGING.md).
 *
 * All tables exist from version 1, even those first used in later milestones (block, entry,
 * timer_state, setting), so no migration is needed before the first release.
 */
package com.ritoghosh.ttt.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "is_done") val isDone: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "done_at") val doneAt: Long?,
    @ColumnInfo(defaultValue = "") val note: String,
)

@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "task_tag",
    primaryKeys = ["task_id", "tag_id"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class TaskTagEntity(
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "block",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("start_at"), Index("task_id")],
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
)

/** RESTRICT: the database itself refuses to delete a task that has sessions (SPEC 6.2). */
@Entity(
    tableName = "entry",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("started_at"), Index("task_id")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "paused_ms") val pausedMs: Long,
    @ColumnInfo(defaultValue = "") val note: String,
)

/**
 * The timer's single row (id = 1). Phase and status are stored as the enum names in text,
 * so the backup file is readable with plain sqlite3. A missing row means IDLE.
 */
@Entity(tableName = "timer_state")
data class TimerStateEntity(
    @PrimaryKey val id: Int = TIMER_STATE_ROW_ID,
    val phase: String,
    val status: String,
    @ColumnInfo(name = "task_id") val taskId: Long?,
    @ColumnInfo(name = "phase_length_ms") val phaseLengthMs: Long,
    @ColumnInfo(name = "accumulated_ms") val accumulatedMs: Long,
    @ColumnInfo(name = "running_since_ms") val runningSinceMs: Long?,
    @ColumnInfo(name = "paused_since_ms") val pausedSinceMs: Long?,
    @ColumnInfo(name = "session_started_at_ms") val sessionStartedAtMs: Long?,
    @ColumnInfo(name = "planned_end_reached_at_ms") val plannedEndReachedAtMs: Long?,
    @ColumnInfo(name = "completed_work_phases") val completedWorkPhases: Int,
    @ColumnInfo(name = "note_prompt_entry_id") val notePromptEntryId: Long?,
)

/** The only row id the timer_state table ever uses. */
const val TIMER_STATE_ROW_ID = 1

/** Settings (SPEC 9.5) live in the database so that backups include them. */
@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Facts about this database file: created_at, app_version_at_creation. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
