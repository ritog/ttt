/**
 * PhaseAlarmReceiver — runs when a phase reaches its planned end (SPEC 10.2).
 *
 * Depends on: TttApplication (for the AppContainer), data/repo/TimerRepository.kt.
 * Must not: hold logic; TimerRepository.onPlannedEndAlarm decides what happens and what to post.
 *
 * goAsync() keeps the receiver alive while the database work finishes (a few milliseconds).
 */
package com.ritoghosh.ttt.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ritoghosh.ttt.TttApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhaseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as TttApplication).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.timerRepository.onPlannedEndAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}
