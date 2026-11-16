/**
 * TodoUiStateTest — the Todo list's filter and sort rules (SPEC 6.5, 9.1).
 *
 * Depends on: ui/todo/TodoUiState.kt, core/model/Task.kt, JUnit 4.
 * Must not: start Android components (plain JVM unit test).
 */
package com.ritoghosh.ttt.ui.todo

import com.ritoghosh.ttt.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoUiStateTest {

    private fun task(id: Long, createdAt: Long, tags: List<String> = emptyList(), doneAt: Long? = null) = Task(
        id = id,
        title = "Task $id",
        tags = tags,
        isDone = doneAt != null,
        createdAtMs = createdAt,
        doneAtMs = doneAt,
        note = "",
    )

    private fun build(tasks: List<Task>, tags: List<String> = emptyList(), selectedTag: String? = null) =
        buildTodoUiState(tasks, tags, selectedTag, isDoneExpanded = false, message = null)

    @Test
    fun `open tasks are newest first`() {
        val state = build(listOf(task(1, createdAt = 100), task(2, createdAt = 300), task(3, createdAt = 200)))
        assertEquals(listOf(2L, 3L, 1L), state.openTasks.map { it.id })
    }

    @Test
    fun `done tasks are most recently completed first and kept out of the open list`() {
        val state = build(
            listOf(
                task(1, createdAt = 100, doneAt = 500),
                task(2, createdAt = 200, doneAt = 900),
                task(3, createdAt = 300),
            ),
        )
        assertEquals(listOf(3L), state.openTasks.map { it.id })
        assertEquals(listOf(2L, 1L), state.doneTasks.map { it.id })
    }

    @Test
    fun `the tag filter keeps only tasks with that tag, open and done`() {
        val tasks = listOf(
            task(1, createdAt = 100, tags = listOf("book")),
            task(2, createdAt = 200, tags = listOf("home")),
            task(3, createdAt = 300, tags = listOf("book", "home"), doneAt = 400),
        )
        val state = build(tasks, tags = listOf("book", "home"), selectedTag = "book")
        assertEquals("book", state.activeTag)
        assertEquals(listOf(1L), state.openTasks.map { it.id })
        assertEquals(listOf(3L), state.doneTasks.map { it.id })
    }

    @Test
    fun `a selected tag that no longer exists is ignored`() {
        val tasks = listOf(task(1, createdAt = 100, tags = listOf("home")))
        val state = build(tasks, tags = listOf("home"), selectedTag = "gone")
        assertNull(state.activeTag)
        assertEquals(listOf(1L), state.openTasks.map { it.id })
    }

    @Test
    fun `hasAnyTask ignores the filter, so a filtered-out list is not the first-run empty state`() {
        val tasks = listOf(task(1, createdAt = 100, tags = listOf("home")), task(2, createdAt = 200, tags = listOf("book")))
        val state = build(tasks, tags = listOf("book", "home"), selectedTag = "book")
        assertTrue(state.hasAnyTask)
        assertTrue(state.isLoaded)
        assertFalse(build(emptyList()).hasAnyTask)
    }
}
