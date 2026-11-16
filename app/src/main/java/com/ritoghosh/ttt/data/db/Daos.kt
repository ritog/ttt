/**
 * Daos — the SQL the app runs. One small interface per area; all queries are written out in full.
 *
 * Depends on: Entities.kt, Room, kotlinx.coroutines Flow.
 * Must not: contain business rules (those are in :core) or be called from the ui layer.
 * Repositories are the only callers.
 */
package com.ritoghosh.ttt.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A task row plus its tag names joined with commas (tags never contain commas; see TagRules). */
data class TaskWithTagNames(
    @Embedded val task: TaskEntity,
    @ColumnInfo(name = "tag_names") val tagNames: String?,
)

/** Every task with its tags. GROUP_CONCAT order is not guaranteed; Mappers.kt sorts the tags. */
private const val TASKS_WITH_TAG_NAMES =
    "SELECT task.*, " +
        "(SELECT GROUP_CONCAT(tag.name) FROM task_tag JOIN tag ON tag.id = task_tag.tag_id " +
        "WHERE task_tag.task_id = task.id) AS tag_names " +
        "FROM task"

@Dao
interface TaskDao {

    @Query(TASKS_WITH_TAG_NAMES)
    fun observeAll(): Flow<List<TaskWithTagNames>>

    /** Every task, once (for the report). */
    @Query(TASKS_WITH_TAG_NAMES)
    suspend fun getAll(): List<TaskWithTagNames>

    @Query(TASKS_WITH_TAG_NAMES + " WHERE task.id = :taskId")
    suspend fun getById(taskId: Long): TaskWithTagNames?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    /** Blocks and tag links go with the task (CASCADE); entries make this fail (RESTRICT). */
    @Query("DELETE FROM task WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)

    @Query("SELECT COUNT(*) FROM entry WHERE task_id = :taskId")
    suspend fun countEntries(taskId: Long): Int

    @Query("SELECT COUNT(*) FROM block WHERE task_id = :taskId")
    suspend fun countBlocks(taskId: Long): Int

    /** Tasks + blocks + sessions; restore is allowed only when this is 0 (SPEC 8.3). */
    @Query("SELECT (SELECT COUNT(*) FROM task) + (SELECT COUNT(*) FROM block) + (SELECT COUNT(*) FROM entry)")
    suspend fun countRecords(): Int

    /** 1 if the timer holds this task (running, paused or waiting for the next phase), else 0. */
    @Query("SELECT COUNT(*) FROM timer_state WHERE task_id = :taskId AND status != 'IDLE'")
    suspend fun countTimerOnTask(taskId: Long): Int
}

@Dao
interface TagDao {

    /** Names of all tags that exist (i.e. are used by at least one task), alphabetically. */
    @Query("SELECT name FROM tag ORDER BY name")
    fun observeNames(): Flow<List<String>>

    /** Adds tags that do not exist yet; existing names are left alone (UNIQUE + IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(tags: List<TagEntity>)

    @Query("SELECT id FROM tag WHERE name IN (:names)")
    suspend fun idsForNames(names: List<String>): List<Long>

    @Query("DELETE FROM task_tag WHERE task_id = :taskId")
    suspend fun unlinkAllFromTask(taskId: Long)

    @Insert
    suspend fun linkToTask(links: List<TaskTagEntity>)

    /** Tags with no remaining tasks are deleted automatically (SPEC 5.2). */
    @Query("DELETE FROM tag WHERE id NOT IN (SELECT tag_id FROM task_tag)")
    suspend fun deleteUnused()
}

/** The earliest upcoming block start of one task. */
data class NextBlockStart(
    val taskId: Long,
    val startAt: Long,
)

/** Blocks that overlap the half-open window [startMs, endMs), earliest first. */
private const val BLOCKS_OVERLAPPING =
    "SELECT * FROM block WHERE start_at < :endMs AND end_at > :startMs ORDER BY start_at"

@Dao
interface BlockDao {

    /** Every block, once (for the report). */
    @Query("SELECT * FROM block ORDER BY start_at")
    suspend fun getAll(): List<BlockEntity>

    @Query(BLOCKS_OVERLAPPING)
    fun observeOverlapping(startMs: Long, endMs: Long): Flow<List<BlockEntity>>

    @Query(BLOCKS_OVERLAPPING)
    suspend fun getOverlapping(startMs: Long, endMs: Long): List<BlockEntity>

    @Query("SELECT * FROM block WHERE id = :blockId")
    suspend fun getById(blockId: Long): BlockEntity?

    @Insert
    suspend fun insert(block: BlockEntity): Long

    @Update
    suspend fun update(block: BlockEntity)

    @Query("DELETE FROM block WHERE id = :blockId")
    suspend fun deleteById(blockId: Long)

    /** For each task, its first block starting at or after [nowMs] (the Todo row's "Next block"). */
    @Query(
        "SELECT task_id AS taskId, MIN(start_at) AS startAt FROM block " +
            "WHERE start_at >= :nowMs GROUP BY task_id",
    )
    fun observeNextStarts(nowMs: Long): Flow<List<NextBlockStart>>
}

@Dao
interface EntryDao {

    /** Every session, once (for the report). */
    @Query("SELECT * FROM entry ORDER BY started_at")
    suspend fun getAll(): List<EntryEntity>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("UPDATE entry SET note = :note WHERE id = :entryId")
    suspend fun updateNote(entryId: Long, note: String)

    /** Entries that START inside [startMs, endMs): a session belongs to the day it starts (SPEC 4.3). */
    @Query("SELECT * FROM entry WHERE started_at >= :startMs AND started_at < :endMs ORDER BY started_at")
    fun observeStartedIn(startMs: Long, endMs: Long): Flow<List<EntryEntity>>

    @Query("DELETE FROM entry WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    /** All-time worked time per task (the Todo row's "2h 15m worked"). */
    @Query(
        "SELECT task_id AS taskId, SUM(ended_at - started_at - paused_ms) AS workedMs " +
            "FROM entry GROUP BY task_id",
    )
    fun observeWorkedPerTask(): Flow<List<TaskWorked>>
}

/** Total worked time of one task. */
data class TaskWorked(
    val taskId: Long,
    val workedMs: Long,
)

@Dao
interface SettingDao {

    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting")
    suspend fun getAll(): List<SettingEntity>

    /** Writes one setting, replacing its old value. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun delete(key: String)
}

/** The timer's single row; its id is always 1 (TIMER_STATE_ROW_ID). */
@Dao
interface TimerStateDao {

    @Query("SELECT * FROM timer_state WHERE id = 1")
    fun observe(): Flow<TimerStateEntity?>

    @Query("SELECT * FROM timer_state WHERE id = 1")
    suspend fun get(): TimerStateEntity?

    /** Writes the row, creating it the first time. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: TimerStateEntity)
}
