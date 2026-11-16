/**
 * EntryTest — worked time of a session (SPEC 5.1).
 *
 * Depends on: core/model/Entry.kt, core/testing/TestTime.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.model

import com.ritoghosh.ttt.core.testing.at
import com.ritoghosh.ttt.core.testing.minutes
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryTest {

    @Test
    fun `worked time is the wall-clock length minus pauses`() {
        val entry = Entry(
            id = 1L,
            taskId = 1L,
            startedAtMs = at("2026-11-16", "14:00"),
            endedAtMs = at("2026-11-16", "14:35"),
            pausedMs = minutes(10),
            note = "",
        )
        assertEquals(minutes(25), entry.workedMs)
        assertEquals(minutes(35), entry.wallClockRange.durationMs)
    }
}
