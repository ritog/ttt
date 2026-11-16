/**
 * Notifications — every notification the app shows, built from the timer state (SPEC 10.2, 10.3).
 *
 * Depends on: AndroidX NotificationCompat, core/timer (TimerMath), MainActivity (tap target).
 * Must not: run code to update the time. The ongoing notification uses the system's built-in
 * chronometer, so nothing ticks in the app; there is no foreground service.
 *
 * Two channels: "Timer alerts" (default importance, with sound) for phase-end alerts, and
 * "Running timer" (low importance, silent) for the ongoing notification. Without the
 * notification permission everything here quietly does nothing; the timer works the same.
 */
package com.ritoghosh.ttt.data.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ritoghosh.ttt.MainActivity
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerState
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.timer.isActive
import com.ritoghosh.ttt.core.timer.overtimeMs
import com.ritoghosh.ttt.core.timer.remainingMs

private const val CHANNEL_ALERTS = "timer_alerts"
private const val CHANNEL_RUNNING = "timer_running"
private const val ID_RUNNING = 1
private const val ID_ALERT = 2

class TimerNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Creates both channels; safe to call on every app start. */
    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_alerts))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_running))
                .setSound(null, null)
                .build(),
        )
    }

    /**
     * Shows, updates or removes the ongoing notification. Counting down before the planned end,
     * counting up from it in overtime, "Paused" without a clock; removed when idle or waiting.
     */
    fun showTimer(state: TimerState, taskTitle: String, nowMs: Long) {
        if (!state.isActive) {
            manager.cancel(ID_RUNNING)
            return
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_tab_track)
            .setContentTitle(taskTitle.ifEmpty { phaseLabel(state.phase) })
            .setContentIntent(openTrackIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        when {
            state.status == TimerStatus.PAUSED -> builder
                .setContentText(context.getString(R.string.timer_paused))
                .setShowWhen(false)
            state.remainingMs(nowMs) > 0 -> builder
                .setContentText(phaseLabel(state.phase))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(nowMs + state.remainingMs(nowMs))
            else -> builder // overtime: count up from the moment the planned length was reached
                .setContentText(context.getString(R.string.timer_overtime))
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setWhen(nowMs - state.overtimeMs(nowMs))
        }
        notifyIfAllowed(ID_RUNNING, builder)
    }

    fun postBreakOver() {
        postAlert(context.getString(R.string.alert_break_over))
    }

    fun postPlannedTimeReached(taskTitle: String) {
        postAlert(context.getString(R.string.alert_planned_time_reached, taskTitle))
    }

    private fun postAlert(text: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_tab_track)
            .setContentTitle(text)
            .setContentIntent(openTrackIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        notifyIfAllowed(ID_ALERT, builder)
    }

    // Lint cannot see that the permission is checked just above the notify call.
    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(id: Int, builder: NotificationCompat.Builder) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsPermission && !hasPostPermission()) return
        manager.notify(id, builder.build())
    }

    private fun hasPostPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun phaseLabel(phase: TimerPhase): String = when (phase) {
        TimerPhase.WORK -> context.getString(R.string.timer_phase_work)
        TimerPhase.SHORT_BREAK -> context.getString(R.string.timer_phase_short_break)
        TimerPhase.LONG_BREAK -> context.getString(R.string.timer_phase_long_break)
        TimerPhase.NONE -> context.getString(R.string.tab_track)
    }

    /** Tapping any timer notification opens the Track screen (MainActivity reads the extra). */
    private fun openTrackIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TRACK, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
