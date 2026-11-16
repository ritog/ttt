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
