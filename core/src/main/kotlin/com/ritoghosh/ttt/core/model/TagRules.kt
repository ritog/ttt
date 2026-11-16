/**
 * TagRules — what a valid tag is, and parsing "#tags" out of a quick-add title (SPEC 6.1).
 *
 * Depends on: core/Constants.kt.
 * Must not: store tags or decide which tags exist; that is the data layer's job.
 *
 * A tag is 1..30 characters of a-z, 0-9, '-' and '_'. Input is lowercased.
 * Tag lists are always distinct and sorted alphabetically, so "the first tag" is stable.
 */
package com.ritoghosh.ttt.core.model

import com.ritoghosh.ttt.core.TAG_MAX_LENGTH

enum class TagError { EMPTY, TOO_LONG, INVALID_CHARACTERS }

sealed interface TagResult {
    data class Valid(val name: String) : TagResult
    data class Invalid(val error: TagError) : TagResult
}

/** Result of reading a quick-add line such as "Write outline #book #writing". */
data class QuickAdd(
    /** The text with tag words removed and spaces collapsed; still needs validateTaskTitle. */
    val title: String,
    /** Valid tags found, distinct and sorted. */
    val tags: List<String>,
    /** Words that looked like tags but were not valid (e.g. "#c++"); they stay in the title. */
    val rejectedTagWords: List<String>,
)

private const val TAG_PREFIX = "#"
private val TAG_CHARACTERS = Regex("[a-z0-9_-]+")
private val WHITESPACE = Regex("\\s+")

/** Punctuation that may follow a tag in ordinary writing ("#book," or "(see #book)"); not part of the tag. */
private const val TRAILING_PUNCTUATION = ".,;:!?)]}\"'"

/**
 * Normalises one tag typed into the editor's tag field: trims, drops one leading '#', lowercases,
 * then validates. Invalid input is rejected, never silently changed into something else.
 */
fun normalizeTag(raw: String): TagResult {
    val name = raw.trim().removePrefix(TAG_PREFIX).lowercase()
    return when {
        name.isEmpty() -> TagResult.Invalid(TagError.EMPTY)
        name.length > TAG_MAX_LENGTH -> TagResult.Invalid(TagError.TOO_LONG)
        !TAG_CHARACTERS.matches(name) -> TagResult.Invalid(TagError.INVALID_CHARACTERS)
        else -> TagResult.Valid(name)
    }
}

/** Makes a tag list distinct and sorted (the one canonical order used everywhere). */
fun normalizeTags(tags: Collection<String>): List<String> = tags.distinct().sorted()

/**
 * Splits a quick-add line into title and tags.
 *
 * A word is a tag word only if it *starts* with '#', so "C#" or "F#" stay in the title.
 * A lone "#" (or "#" followed only by punctuation) is plain text. Spaces between the
 * remaining words are collapsed to one.
 */
fun parseQuickAdd(input: String): QuickAdd {
    val titleWords = mutableListOf<String>()
    val tags = mutableListOf<String>()
    val rejected = mutableListOf<String>()
    for (word in input.trim().split(WHITESPACE)) {
        if (word.isEmpty()) continue // splitting an empty line yields one empty word
        when (val result = tagFromWord(word)) {
            null -> titleWords.add(word)
            is TagResult.Valid -> tags.add(result.name)
            is TagResult.Invalid -> {
                rejected.add(word)
                titleWords.add(word)
            }
        }
    }
    return QuickAdd(titleWords.joinToString(" "), normalizeTags(tags), rejected)
}

/** The tag a word stands for, or null if the word is ordinary text. */
private fun tagFromWord(word: String): TagResult? {
    if (!word.startsWith(TAG_PREFIX)) return null
    val body = word.removePrefix(TAG_PREFIX).trimEnd { it in TRAILING_PUNCTUATION }
    if (body.isEmpty()) return null
    return normalizeTag(body)
}
