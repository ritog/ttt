/**
 * TagRulesTest — tag format, normalising, rejecting, and quick-add parsing (SPEC 6.1).
 *
 * Depends on: core/model/TagRules.kt, JUnit 4.
 * Must not: use Android classes.
 */
package com.ritoghosh.ttt.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagRulesTest {

    // --- normalizeTag: the editor's tag field ---

    @Test
    fun `a valid tag is lowercased and trimmed`() {
        assertEquals(TagResult.Valid("deep-work_2"), normalizeTag("  Deep-Work_2 "))
    }

    @Test
    fun `one leading hash is dropped so typing #book in the tag field works`() {
        assertEquals(TagResult.Valid("book"), normalizeTag("#book"))
    }

    @Test
    fun `an empty tag is rejected`() {
        assertEquals(TagResult.Invalid(TagError.EMPTY), normalizeTag("   "))
        assertEquals(TagResult.Invalid(TagError.EMPTY), normalizeTag("#"))
    }

    @Test
    fun `thirty characters is allowed, thirty-one is too long`() {
        assertEquals(TagResult.Valid("a".repeat(30)), normalizeTag("a".repeat(30)))
        assertEquals(TagResult.Invalid(TagError.TOO_LONG), normalizeTag("a".repeat(31)))
    }

    @Test
    fun `spaces, punctuation and non-ascii letters are rejected, not silently changed`() {
        listOf("deep work", "c++", "a.b", "café", "tag!").forEach { input ->
            assertEquals(input, TagResult.Invalid(TagError.INVALID_CHARACTERS), normalizeTag(input))
        }
    }

    @Test
    fun `tag lists are made distinct and sorted`() {
        assertEquals(listOf("a", "book", "zen"), normalizeTags(listOf("zen", "book", "a", "book")))
    }

    // --- parseQuickAdd: the "Add a task..." field ---

    @Test
    fun `the spec example splits title and tags`() {
        val result = parseQuickAdd("Write outline #book #writing")
        assertEquals("Write outline", result.title)
        assertEquals(listOf("book", "writing"), result.tags)
        assertEquals(emptyList<String>(), result.rejectedTagWords)
    }

    @Test
    fun `tags can appear anywhere and come out sorted`() {
        val result = parseQuickAdd("#writing Draft #book chapter one")
        assertEquals("Draft chapter one", result.title)
        assertEquals(listOf("book", "writing"), result.tags)
    }

    @Test
    fun `duplicate tags count once, whatever their case`() {
        assertEquals(listOf("book"), parseQuickAdd("Read #book #Book #BOOK").tags)
    }

    @Test
    fun `trailing punctuation after a tag is not part of it`() {
        val result = parseQuickAdd("Plan (see #book), then #writing.")
        assertEquals(listOf("book", "writing"), result.tags)
        assertEquals("Plan (see then", result.title)
    }

    @Test
    fun `a lone hash is plain text`() {
        val result = parseQuickAdd("Fix issue # 12")
        assertEquals("Fix issue # 12", result.title)
        assertEquals(emptyList<String>(), result.tags)
    }

    @Test
    fun `C-sharp style words do not start with a hash and stay in the title`() {
        val result = parseQuickAdd("Learn C# and F#")
        assertEquals("Learn C# and F#", result.title)
        assertEquals(emptyList<String>(), result.tags)
    }

    @Test
    fun `an invalid tag word stays in the title and is reported`() {
        val result = parseQuickAdd("Study #c++ basics #study")
        assertEquals("Study #c++ basics", result.title)
        assertEquals(listOf("study"), result.tags)
        assertEquals(listOf("#c++"), result.rejectedTagWords)
    }

    @Test
    fun `extra spaces are collapsed`() {
        assertEquals("Call mum", parseQuickAdd("  Call   #home  mum  ").title)
    }

    @Test
    fun `a line of only tags leaves an empty title, which validation then rejects`() {
        val result = parseQuickAdd("#book #writing")
        assertEquals("", result.title)
        assertEquals(TitleResult.Invalid(TitleError.EMPTY), validateTaskTitle(result.title))
    }

    @Test
    fun `an empty line gives nothing`() {
        assertEquals(QuickAdd("", emptyList(), emptyList()), parseQuickAdd(""))
    }
}
