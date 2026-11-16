/**
 * TimerTestSupport — shared fixtures for the timer tests: settings, a start time, and small helpers.
 *
 * Depends on: core/timer, core/model.
 * Must not: be used outside tests.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus

/** 25 / 5 / 15 minutes, long break after every 4th work phase. */
val SETTINGS = PomodoroSettings.fromMinutes(work = 25, shortBreak = 5, longBreak = 15, workPhasesBeforeLongBreak = 4)

const val TASK = 7L
const val OTHER_TASK = 8L

/** An arbitrary start instant; tests add minutes to it. */
const val T0 = 1_790_000_000_000L

fun min(count: Int): Long = count * 60_000L

fun sec(count: Int): Long = count * 1_000L

fun TimerState.apply(event: TimerEvent, atMs: Long): TransitionResult = transition(this, event, atMs, SETTINGS)

fun TimerState.then(event: TimerEvent, atMs: Long): TimerState = apply(event, atMs).state

/** Work started at [atMs] on [TASK]. */
fun workStartedAt(atMs: Long = T0): TimerState = TimerState.IDLE.then(TimerEvent.Start(TASK), atMs)

/**
 * From a state waiting for work (or idle), runs one full work phase finished at its planned
 * length, starts the break, and lets it run out. Returns the state (waiting for work) and the time.
 */
fun TimerState.oneFullPomodoro(atMs: Long): Pair<TimerState, Long> {
    val working = if (status == TimerStatus.IDLE) {
        then(TimerEvent.Start(TASK), atMs)
    } else {
        then(TimerEvent.StartNext, atMs)
    }
    val finishedAt = atMs + min(25)
    val onBreak = working
        .then(TimerEvent.Finish(keepOvertime = true), finishedAt)
        .then(TimerEvent.StartNext, finishedAt)
    val breakOverAt = finishedAt + onBreak.phaseLengthMs
    return onBreak.then(TimerEvent.Evaluate, breakOverAt) to breakOverAt
}
