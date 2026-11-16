/**
 * TimerRepository — the one door to the timer: applies PomodoroMachine transitions, saves sessions,
 * and keeps the alarm and the ongoing notification in step (SPEC 7, 10).
 *
 * Depends on: core/timer (PomodoroMachine), data/db, data/alarm, core/time/Clock.kt.
 * Must not: contain timer rules (they are in PomodoroMachine) or count time.
 *
 * Every event is applied in one database transaction: read state → transition → save the
 * session (if any) → save the new state. So a session is never saved without the state that
 * produced it, or the other way round (SPEC 7.3 atomicity). A mutex applies quick taps in order.
 */
package com.ritoghosh.ttt.data.repo

import androidx.room.withTransaction
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.timer.AlarmAction
import com.ritoghosh.ttt.core.timer.PomodoroSettings
import com.ritoghosh.ttt.core.timer.TimerEvent
import com.ritoghosh.ttt.core.timer.isBreak
import com.ritoghosh.ttt.core.timer.isInOvertime
import com.ritoghosh.ttt.core.timer.remainingMs
import com.ritoghosh.ttt.core.timer.transition
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.data.alarm.PhaseAlarmScheduler
import com.ritoghosh.ttt.data.alarm.TimerNotifications
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.db.toEntity
import com.ritoghosh.ttt.data.db.toTimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerRepository(
    private val database: AppDatabase,
    private val clock: Clock,
    private val alarms: PhaseAlarmScheduler,
    private val notifications: TimerNotifications,
) {
    private val timerDao = database.timerStateDao()
    private val entryDao = database.entryDao()
    private val taskDao = database.taskDao()
    private val mutex = Mutex()

    /** Pomodoro lengths. Fixed defaults until Settings arrive in M5. */
    private val settings = PomodoroSettings.DEFAULT

    /** The timer state, kept up to date. A missing row is IDLE. */
    fun observeState(): Flow<TimerState> = timerDao.observe().map { it?.toTimerState() ?: TimerState.IDLE }

    /** Applies [event] now and returns the new state. */
    suspend fun dispatch(event: TimerEvent): TimerState = mutex.withLock {
        val nowMs = clock.nowMs()
        val result = database.withTransaction {
            val transitioned = transition(currentState(), event, nowMs, settings)
            val savedEntryId = transitioned.entryToSave?.let { entryDao.insert(it.toEntity()) }
            val newState = if (savedEntryId != null) {
                transitioned.state.copy(notePromptEntryId = savedEntryId) // the note card asks about this session
            } else {
                transitioned.state
            }
            timerDao.save(newState.toEntity())
            transitioned.copy(state = newState)
        }
        applyAlarm(result.alarm)
        refreshNotification(result.state, nowMs)
        result.state
    }

    /**
     * On every app open: end a break that ran out, and re-schedule the alarm for a running phase
     * (alarms are lost on reboot) (SPEC 10.2).
     */
    suspend fun recoverOnAppStart() {
        val state = dispatch(TimerEvent.Evaluate)
        val nowMs = clock.nowMs()
        if (state.status == TimerStatus.RUNNING && state.remainingMs(nowMs) > 0) {
            alarms.schedule(nowMs + state.remainingMs(nowMs))
        }
    }

    /**
     * Called by PhaseAlarmReceiver. A break ends (Evaluate) and "Break over" is posted. A work phase
     * changes nothing (it continues as overtime) and "Planned time reached" is posted.
     */
    suspend fun onPlannedEndAlarm() {
        val before = currentState()
        val after = dispatch(TimerEvent.Evaluate)
        val breakJustEnded = before.isBreak && before.status == TimerStatus.RUNNING &&
            after.status == TimerStatus.AWAITING_START
        when {
            breakJustEnded -> notifications.postBreakOver()
            after.status == TimerStatus.RUNNING && after.isInOvertime(clock.nowMs()) ->
                notifications.postPlannedTimeReached(taskTitle(after))
        }
    }

    /** Saves the note card's text on its session and closes the card. */
    suspend fun saveNote(entryId: Long, note: String) {
        entryDao.updateNote(entryId, note.trim())
        dispatch(TimerEvent.ClearNotePrompt)
    }

    fun canScheduleExactAlarms(): Boolean = alarms.canScheduleExact()

    private suspend fun currentState(): TimerState = timerDao.get()?.toTimerState() ?: TimerState.IDLE

    private fun applyAlarm(action: AlarmAction) {
        when (action) {
            AlarmAction.Unchanged -> Unit
            AlarmAction.Cancel -> alarms.cancel()
            is AlarmAction.ScheduleAt -> alarms.schedule(action.atMs)
        }
    }

    private suspend fun refreshNotification(state: TimerState, nowMs: Long) {
        notifications.showTimer(state, taskTitle(state), nowMs)
    }

    private suspend fun taskTitle(state: TimerState): String =
        state.taskId?.let { taskDao.getById(it)?.task?.title }.orEmpty()
}
