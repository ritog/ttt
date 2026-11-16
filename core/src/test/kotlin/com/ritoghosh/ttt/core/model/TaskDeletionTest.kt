/**
 * TaskDeletionTest — when a task may be deleted (SPEC 6.2).
 *
 * Depends on: core/model/TaskDeletion.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDeletionTest {

    @Test
    fun `a task with no sessions can be deleted and its blocks go with it`() {
        assertEquals(
            TaskDeletion.Allowed(blocksToDelete = 3),
            checkTaskDeletion(sessionCount = 0, blockCount = 3, timerIsOnTask = false),
        )
    }

    @Test
    fun `a task with sessions cannot be deleted`() {
        assertEquals(
            TaskDeletion.HasSessions,
            checkTaskDeletion(sessionCount = 1, blockCount = 0, timerIsOnTask = false),
        )
    }

    @Test
    fun `a task cannot be deleted while the timer is on it`() {
        assertEquals(
            TaskDeletion.TimerIsOnTask,
            checkTaskDeletion(sessionCount = 0, blockCount = 0, timerIsOnTask = true),
        )
    }

    @Test
    fun `the timer message wins when the task also has sessions`() {
        assertEquals(
            TaskDeletion.TimerIsOnTask,
            checkTaskDeletion(sessionCount = 5, blockCount = 2, timerIsOnTask = true),
        )
    }
}
