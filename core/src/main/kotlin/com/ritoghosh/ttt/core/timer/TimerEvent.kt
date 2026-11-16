/**
 * TimerEvent — everything that can happen to the timer, and what a transition produces (SPEC 7.3).
 *
 * Depends on: core/model (TimerState, Entry).
 * Must not: contain logic; the rules are in PomodoroMachine.kt.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.model.Entry
import com.ritoghosh.ttt.core.model.TimerState

sealed interface TimerEvent {
    data class Start(val taskId: Long) : TimerEvent
    data object Pause : TimerEvent
    data object Resume : TimerEvent

    /** "Has a break run out?" Sent on app open/resume and by the planned-end alarm. */
    data object Evaluate : TimerEvent

    /** End a work phase that reached its planned length. [keepOvertime] = false cuts it back to plan. */
    data class Finish(val keepOvertime: Boolean) : TimerEvent

    /** End now: a work phase is saved with its real length (if ≥ 1 minute); a break is dropped. */
    data class Stop(val keepOvertime: Boolean) : TimerEvent

    /** Begin the queued phase (nothing starts without a tap). */
    data object StartNext : TimerEvent
    data object SkipBreak : TimerEvent

    /** Pick another task; only while idle or waiting between phases, never mid-work. */
    data class ChangeTask(val taskId: Long) : TimerEvent

    /** The note card was saved or skipped. */
    data object ClearNotePrompt : TimerEvent
}

/** What to do with the planned-end alarm after a transition. */
sealed interface AlarmAction {
    data object Unchanged : AlarmAction
    data object Cancel : AlarmAction
    data class ScheduleAt(val atMs: Long) : AlarmAction
}

data class TransitionResult(
    val state: TimerState,
    /**
     * A finished or stopped session to save (id = NEW_ID). The repository saves it in the same
     * transaction as [state] and sets state.notePromptEntryId to the new entry's id.
     */
    val entryToSave: Entry? = null,
    val alarm: AlarmAction = AlarmAction.Unchanged,
)
