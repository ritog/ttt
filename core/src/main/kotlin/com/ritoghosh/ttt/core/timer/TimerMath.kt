/**
 * TimerMath — elapsed, remaining and overtime, always computed from timestamps (SPEC 7.2).
 *
 * Depends on: core/model/TimerState.kt, core/Constants.kt.
 * Must not: keep or count time itself; "now" is always passed in.
 */
package com.ritoghosh.ttt.core.timer

import com.ritoghosh.ttt.core.LONG_OVERTIME_MINUTES
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus

private const val MS_PER_MINUTE = 60_000L

/** Time counted in this phase: earlier run segments plus the current one. */
fun TimerState.elapsedMs(nowMs: Long): Long {
    val since = runningSinceMs
    val current = if (status == TimerStatus.RUNNING && since != null) nowMs - since else 0L
    return accumulatedMs + current
}

/** Time left before the planned length; 0 once it is reached. */
fun TimerState.remainingMs(nowMs: Long): Long = maxOf(0L, phaseLengthMs - elapsedMs(nowMs))

/** Work past the planned length; always 0 for breaks. */
fun TimerState.overtimeMs(nowMs: Long): Long =
    if (phase == TimerPhase.WORK) maxOf(0L, elapsedMs(nowMs) - phaseLengthMs) else 0L

val TimerState.isBreak: Boolean
    get() = phase == TimerPhase.SHORT_BREAK || phase == TimerPhase.LONG_BREAK

/** Running or paused (as opposed to idle or waiting for a tap). */
val TimerState.isActive: Boolean
    get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED

/** A work phase is running or paused and has reached its planned length. */
fun TimerState.isInOvertime(nowMs: Long): Boolean =
    phase == TimerPhase.WORK && isActive && elapsedMs(nowMs) >= phaseLengthMs

/** A running break whose time is up; Evaluate will end it. */
fun TimerState.isBreakDue(nowMs: Long): Boolean =
    isBreak && status == TimerStatus.RUNNING && elapsedMs(nowMs) >= phaseLengthMs

/** More than an hour of overtime: ask "keep all" or "keep the planned time only" (SPEC 7.3). */
fun TimerState.needsOvertimeChoice(nowMs: Long): Boolean =
    isInOvertime(nowMs) && overtimeMs(nowMs) > LONG_OVERTIME_MINUTES * MS_PER_MINUTE
