/**
 * TaskPickerTest — which tasks the picker offers, and when it offers "Create …".
 *
 * Depends on: ui/components/TaskPicker.kt, core/model/Task.kt, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.components

import com.ritoghosh.ttt.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPickerTest {

    private fun task(id: Long, title: String, tags: List<String> = emptyList(), done: Boolean = false) =
        Task(id, title, tags, isDone = done, createdAtMs = id, doneAtMs = if (done) id else null, note = "")

    private val tasks = listOf(
        task(1, "Write outline", listOf("book")),
        task(2, "Buy milk", listOf("home")),
        task(3, "Old chapter", listOf("book"), done = true),
        task(4, "Review notes"),
    )

    @Test
    fun `an empty query lists open tasks, newest first`() {
        assertEquals(listOf(4L, 2L, 1L), matchingTasks(tasks, "").map { it.id })
    }

    @Test
    fun `search matches title text, ignoring case`() {
        assertEquals(listOf(1L), matchingTasks(tasks, "OUTLINE").map { it.id })
    }

    @Test
    fun `search matches the start of a tag, with or without a hash`() {
        assertEquals(listOf(1L), matchingTasks(tasks, "bo").map { it.id })
        assertEquals(listOf(1L), matchingTasks(tasks, "#bo").map { it.id })
    }

    @Test
    fun `done tasks are never offered`() {
        assertTrue(matchingTasks(tasks, "chapter").isEmpty())
    }

    @Test
    fun `at most six options are listed`() {
        val many = (1L..10L).map { task(it, "Task $it") }
        assertEquals(TASK_PICKER_MAX_OPTIONS, matchingTasks(many, "").size)
    }

    @Test
    fun `create is offered unless the text is blank or exactly an existing title`() {
        val options = matchingTasks(tasks, "buy milk")
        assertFalse(shouldOfferCreate("Buy milk", options))
        assertTrue(shouldOfferCreate("Buy bread", matchingTasks(tasks, "Buy bread")))
        assertFalse(shouldOfferCreate("   ", tasks))
    }
}
