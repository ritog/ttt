/**
 * AppContainer — builds and holds the app's long-lived objects; the whole wiring in one place (SPEC 4.4).
 *
 * Depends on: BuildConfig, core/time, data/db, data/repo.
 * Must not: use reflection or annotation-based injection, or hold any Activity or UI object
 * (only the application context, which lives as long as the process).
 *
 * Created once per process by TttApplication. ViewModels get what they need through
 * `viewModel { ... }` factories in ui/nav/AppNav.kt that read from this container.
 *
 * Grows milestone by milestone:
 *   M1  clock (done)
 *   M2  database, TaskRepository (done)
 *   M3  BlockRepository
 *   M4  TimerRepository, PhaseAlarmScheduler, Notifications
 *   M5  EntryRepository, SettingsRepository
 *   M6  BackupService, RestoreService, AutoBackup
 */
package com.ritoghosh.ttt

import android.content.Context
import com.ritoghosh.ttt.core.time.Clock
import com.ritoghosh.ttt.core.time.DeviceClock
import com.ritoghosh.ttt.data.db.AppDatabase
import com.ritoghosh.ttt.data.repo.TaskRepository

class AppContainer(context: Context) {

    /** The only source of "now" in the app. Tests replace it with a fake. */
    val clock: Clock = DeviceClock()

    /** Shown on the About screen and written into the database's meta table. */
    val appVersionName: String = BuildConfig.VERSION_NAME

    /** Opened lazily by Room on the first query, so this does not slow down app start. */
    private val database: AppDatabase = AppDatabase.build(context.applicationContext, clock, appVersionName)

    val taskRepository = TaskRepository(database, clock)
}
