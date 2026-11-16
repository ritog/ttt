/**
 * PhaseAlarmScheduler — schedules or cancels the one "planned end" alarm for the running phase (SPEC 10.2).
 *
 * Depends on: Android AlarmManager, PhaseAlarmReceiver.
 * Must not: decide when to alarm; PomodoroMachine says so through AlarmAction.
 *
 * Exact alarms need SCHEDULE_EXACT_ALARM, which Android 14+ denies by default for new installs
 * (USE_EXACT_ALARM is meant for alarm-clock and calendar apps, not timers). Without it we fall
 * back to an inexact alarm, which may arrive a little late; the Track screen tells the user.
 */
package com.ritoghosh.ttt.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** One fixed request code: there is only ever one phase alarm, so scheduling replaces it. */
private const val PHASE_ALARM_REQUEST_CODE = 1

class PhaseAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** True if exact alarms are allowed (always on Android 11; a user setting from Android 12). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Wakes the device at [atMs] (wall-clock) even in Doze; replaces any earlier phase alarm. */
    fun schedule(atMs: Long) {
        val intent = pendingIntent()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
                return
            } catch (revokedJustNow: SecurityException) {
                // The permission was withdrawn between the check and the call; fall through to inexact.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        PHASE_ALARM_REQUEST_CODE,
        Intent(context, PhaseAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
