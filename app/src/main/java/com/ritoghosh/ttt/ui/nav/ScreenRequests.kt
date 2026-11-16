/**
 * ScreenRequests — one-shot requests passed from one screen to another, e.g. "schedule a block
 * for this task" from the Todo editor to the Timeblock screen.
 *
 * Depends on: AndroidX ViewModel, kotlinx.coroutines.
 * Must not: hold lasting state. A request is consumed by the screen that handles it.
 *
 * Activity-scoped like TagFilterHolder. Used instead of navigation arguments because tab
 * navigation restores each tab's saved state, which would drop a new argument.
 * Also "start the timer on this task" from the task editor or a block (opens Track with it selected).
 */
package com.ritoghosh.ttt.ui.nav

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenRequests : ViewModel() {

    private val scheduleBlock = MutableStateFlow<Long?>(null)

    /** Task id the Timeblock screen should open a new block for, or null. */
    val scheduleBlockForTask: StateFlow<Long?> = scheduleBlock.asStateFlow()

    fun requestBlockFor(taskId: Long) {
        scheduleBlock.value = taskId
    }

    fun consumeBlockRequest() {
        scheduleBlock.value = null
    }

    private val startTimer = MutableStateFlow<Long?>(null)

    /** Task id the Track screen should select, or null. */
    val startTimerForTask: StateFlow<Long?> = startTimer.asStateFlow()

    fun requestTimerFor(taskId: Long) {
        startTimer.value = taskId
    }

    fun consumeTimerRequest() {
        startTimer.value = null
    }
}
