/**
 * TaskTest — title validation and the done/undone rules (SPEC 5.1, 6.1).
 *
 * Depends on: core/model/Task.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTest {

    private val openTask = Task(
        id = 1L,
        title = "Write outline",
        tags = listOf("book"),
        isDone = false,
        createdAtMs = 1_000L,
        doneAtMs = null,
        note = "",
    )

    @Test
    fun `marking done sets the done time`() {
        val done = openTask.markedDone(nowMs = 5_000L)
        assertTrue(done.isDone)
        assertEquals(5_000L, done.doneAtMs)
    }

    @Test
    fun `marking done again keeps the first done time`() {
        val done = openTask.markedDone(nowMs = 5_000L).markedDone(nowMs = 9_000L)
        assertEquals(5_000L, done.doneAtMs)
    }

    @Test
    fun `unchecking clears the done time`() {
        val reopened = openTask.markedDone(nowMs = 5_000L).markedOpen()
        assertFalse(reopened.isDone)
        assertNull(reopened.doneAtMs)
    }

    @Test
    fun `a title is trimmed`() {
        assertEquals(TitleResult.Valid("Write outline"), validateTaskTitle("  Write outline \n"))
    }

    @Test
    fun `an empty or blank title is rejected`() {
        assertEquals(TitleResult.Invalid(TitleError.EMPTY), validateTaskTitle(""))
        assertEquals(TitleResult.Invalid(TitleError.EMPTY), validateTaskTitle("   "))
    }

    @Test
    fun `two hundred characters is allowed, two hundred and one is too long`() {
        assertEquals(TitleResult.Valid("a".repeat(200)), validateTaskTitle("a".repeat(200)))
        assertEquals(TitleResult.Invalid(TitleError.TOO_LONG), validateTaskTitle("a".repeat(201)))
    }

    @Test
    fun `an emoji counts as one character`() {
        // "🍅" is two UTF-16 units but one character to the user.
        val title = "🍅".repeat(200)
        assertEquals(TitleResult.Valid(title), validateTaskTitle(title))
    }
}
