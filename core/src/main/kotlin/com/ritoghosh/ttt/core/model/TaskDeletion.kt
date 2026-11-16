/**
 * TaskDeletion — whether a task may be deleted, and what goes with it (SPEC 6.2).
 *
 * Depends on: nothing.
 * Must not: delete anything; it only answers the question. The repository acts on the answer.
 */
package com.ritoghosh.ttt.core.model

sealed interface TaskDeletion {
    /** Deleting is fine; [blocksToDelete] blocks go with the task (the editor states the number). */
    data class Allowed(val blocksToDelete: Int) : TaskDeletion

    /** The timer is on this task. The editor says "Stop the timer first". */
    data object TimerIsOnTask : TaskDeletion

    /** The task has recorded sessions; history is protected. The editor offers only "mark done". */
    data object HasSessions : TaskDeletion
}

/**
 * Decides whether a task can be deleted.
 *
 * The timer check comes first: while the timer is on the task, "stop the timer" is the
 * actionable message, even if the task also has sessions.
 */
fun checkTaskDeletion(sessionCount: Int, blockCount: Int, timerIsOnTask: Boolean): TaskDeletion = when {
    timerIsOnTask -> TaskDeletion.TimerIsOnTask
    sessionCount > 0 -> TaskDeletion.HasSessions
    else -> TaskDeletion.Allowed(blocksToDelete = blockCount)
}
