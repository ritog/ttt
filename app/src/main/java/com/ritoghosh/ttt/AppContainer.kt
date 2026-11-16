/**
 * AppContainer — builds and holds the app's long-lived objects; the whole wiring in one place (SPEC 4.4).
 *
 * Depends on: BuildConfig, core/time, data/db, data/repo, data/alarm.
 * Must not: use reflection or annotation-based injection, or hold any Activity or UI object
 * (only the application context, which lives as long as the process).
 *
 * Created once per process by TttApplication. ViewModels get what they need through
 * `viewModel { ... }` factories in ui/nav/AppNavHost.kt that read from this container.
 *
 * Grows milestone by milestone:
 *   M1  clock (done)
 *   M2  database, TaskRepository (done)
 *   M3  BlockRepository (done)
 *   M4  TimerRepository, PhaseAlarmScheduler, TimerNotifications, EntryRepository (done)
 *   M5  SettingsRepository
 *   M6  BackupService, RestoreService, AutoBackup
 */
package com.ritoghosh.ttt

import android.content.Context
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.DeviceClock
import com.ritoghosh.ttt.data.alarm.PhaseAlarmScheduler
import com.ritoghosh.ttt.data.alarm.TimerNotifications
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.repo.BlockRepository
import com.ritoghosh.ttt.data.repo.EntryRepository
import com.ritoghosh.ttt.data.repo.TaskRepository
import com.ritoghosh.ttt.data.repo.TimerRepository

class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** The only source of "now" in the app. Tests replace it with a fake. */
    val clock: Clock = DeviceClock()

    /** Shown on the About screen and written into the database's meta table. */
    val appVersionName: String = BuildConfig.VERSION_NAME

    /** Opened lazily by Room on the first query, so this does not slow down app start. */
    private val database: AppDatabase = AppDatabase.build(appContext, clock, appVersionName)

    val notifications = TimerNotifications(appContext)

    private val alarmScheduler = PhaseAlarmScheduler(appContext)

    val taskRepository = TaskRepository(database, clock)

    val blockRepository = BlockRepository(database, clock)

    val entryRepository = EntryRepository(database)

    val timerRepository = TimerRepository(database, clock, alarmScheduler, notifications)
}
