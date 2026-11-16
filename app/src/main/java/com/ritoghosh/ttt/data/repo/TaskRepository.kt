/**
 * TaskRepository — the one door to tasks and tags: add, edit, done/undone, delete (with SPEC 6.2 rules).
 *
 * Depends on: data/db, core/model (rules), core/time/Clock.kt.
 * Must not: expose Room entities. Callers get core models only.
 *
 * Every write that touches more than one table runs in one transaction, so a crash can never
 * leave a task without its tags or a tag without its task.
 */
package com.ritoghosh.ttt.data.repo

import androidx.room.withTransaction
import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TaskDeletion
import com.ritoghosh.ttt.core.model.TitleError
import com.ritoghosh.ttt.core.model.TitleResult
import com.ritoghosh.ttt.core.model.checkTaskDeletion
import com.ritoghosh.ttt.core.model.markedDone
import com.ritoghosh.ttt.core.model.markedOpen
import com.ritoghosh.ttt.core.model.normalizeTags
import com.ritoghosh.ttt.core.model.parseQuickAdd
import com.ritoghosh.ttt.core.model.validateTaskTitle
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.TagEntity
import com.ritoghosh.ttt.data.db.TaskEntity
import com.ritoghosh.ttt.data.db.TaskTagEntity
import com.ritoghosh.ttt.data.db.toEntity
import com.ritoghosh.ttt.data.db.toTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface AddTaskResult {
    /** Saved. [rejectedTagWords] looked like tags but were invalid and stayed in the title. */
    data class Added(val taskId: Long, val rejectedTagWords: List<String>) : AddTaskResult
    data class InvalidTitle(val error: TitleError) : AddTaskResult
}

class TaskRepository(private val database: AppDatabase, private val clock: Clock) {

    private val taskDao = database.taskDao()
    private val tagDao = database.tagDao()

    /** All tasks with their tags, updated whenever tasks or tags change. */
    fun observeTasks(): Flow<List<Task>> = taskDao.observeAll().map { rows -> rows.map { it.toTask() } }

    /** Names of tags in use, alphabetically. */
    fun observeTagNames(): Flow<List<String>> = tagDao.observeNames()

    suspend fun getTask(taskId: Long): Task? = taskDao.getById(taskId)?.toTask()

    /** Adds a task from the quick-add line, e.g. "Write outline #book" (SPEC 6.1). */
    suspend fun addTask(input: String): AddTaskResult {
        val quickAdd = parseQuickAdd(input)
        return when (val title = validateTaskTitle(quickAdd.title)) {
            is TitleResult.Invalid -> AddTaskResult.InvalidTitle(title.error)
            is TitleResult.Valid -> {
                val newTask = TaskEntity(
                    title = title.title,
                    isDone = false,
                    createdAt = clock.nowMs(),
                    doneAt = null,
                    note = "",
                )
                val taskId = database.withTransaction {
                    val id = taskDao.insert(newTask)
                    replaceTags(id, quickAdd.tags)
                    id
                }
                AddTaskResult.Added(taskId, quickAdd.rejectedTagWords)
            }
        }
    }

    /**
     * Saves the editor's changes. [tags] must already be valid tag names (the editor checks
     * each one with normalizeTag). Returns the title check so the caller can show an error.
     */
    suspend fun updateTask(taskId: Long, title: String, tags: List<String>, note: String): TitleResult {
        val titleResult = validateTaskTitle(title)
        if (titleResult !is TitleResult.Valid) return titleResult
        database.withTransaction {
            val current = taskDao.getById(taskId)?.toTask() ?: return@withTransaction
            taskDao.update(current.copy(title = titleResult.title, note = note.trim()).toEntity())
            replaceTags(taskId, tags)
        }
        return titleResult
    }

    /** Checks or unchecks a task; done_at is set or cleared accordingly. */
    suspend fun setDone(taskId: Long, done: Boolean) {
        database.withTransaction {
            val task = taskDao.getById(taskId)?.toTask() ?: return@withTransaction
            val changed = if (done) task.markedDone(clock.nowMs()) else task.markedOpen()
            taskDao.update(changed.toEntity())
        }
    }

    /** Whether [taskId] may be deleted right now, and how many blocks would go with it. */
    suspend fun checkDeletion(taskId: Long): TaskDeletion = checkTaskDeletion(
        sessionCount = taskDao.countEntries(taskId),
        blockCount = taskDao.countBlocks(taskId),
        timerIsOnTask = taskDao.countTimerOnTask(taskId) > 0,
    )

    /**
     * Deletes the task if the rules allow it, and reports the decision. The check runs again
     * inside the transaction, so a session saved a moment ago still protects the task.
     */
    suspend fun deleteTask(taskId: Long): TaskDeletion = database.withTransaction {
        val decision = checkDeletion(taskId)
        if (decision is TaskDeletion.Allowed) {
            taskDao.deleteById(taskId)
            tagDao.deleteUnused()
        }
        decision
    }

    /** Makes the task's tags exactly [tags]; creates new tag rows and removes unused ones. */
    private suspend fun replaceTags(taskId: Long, tags: List<String>) {
        val names = normalizeTags(tags)
        tagDao.unlinkAllFromTask(taskId)
        if (names.isNotEmpty()) {
            tagDao.insertIfMissing(names.map { TagEntity(name = it) })
            tagDao.linkToTask(tagDao.idsForNames(names).map { TaskTagEntity(taskId, it) })
        }
        tagDao.deleteUnused()
    }
}
