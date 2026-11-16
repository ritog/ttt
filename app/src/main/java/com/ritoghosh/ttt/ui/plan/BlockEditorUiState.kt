/**
 * BlockEditorUiState — what the open block editor shows, and the problems it can report.
 *
 * Depends on: core/model, core/plan/BlockRules.kt.
 * Must not: hold Android types or text; the sheet turns problems into strings.
 */
package com.ritoghosh.ttt.ui.plan

import com.ritoghosh.ttt.core.model.Task
import com.ritoghosh.ttt.core.model.TitleError
import com.ritoghosh.ttt.core.plan.BlockProblem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class BlockEditorUiState(
    /** Null for a new block. */
    val blockId: Long?,
    /** Null until a task is chosen or created. */
    val task: Task?,
    val date: LocalDate,
    val start: LocalTime,
    /** 00:00 means midnight at the end of [date] (see core/plan/BlockTimes.kt). */
    val end: LocalTime,
    val problem: BlockEditorProblem? = null,
)

sealed interface BlockEditorProblem {
    data object NoTask : BlockEditorProblem

    /** A rule from BlockRules (order, grid, midnight, minimum length). */
    data class Rule(val problem: BlockProblem) : BlockEditorProblem

    /** Clashes with another block; the message names it (SPEC 6.3). [zone] formats its times. */
    data class Overlap(val taskTitle: String, val startMs: Long, val endMs: Long, val zone: ZoneId) : BlockEditorProblem

    /** The inline "Create …" task had an invalid title (e.g. only tags). */
    data class InvalidNewTask(val error: TitleError) : BlockEditorProblem
}
