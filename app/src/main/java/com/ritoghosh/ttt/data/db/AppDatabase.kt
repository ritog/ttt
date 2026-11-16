/**
 * AppDatabase — the Room database "ttt.db": table list, version, and how it is opened.
 *
 * Depends on: Entities.kt, Daos.kt, Migrations.kt, core/time/Clock.kt.
 * Must not: be opened anywhere except AppContainer (one instance per process).
 */
package com.ritoghosh.ttt.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ritoghosh.ttt.core.time.Clock

@Database(
    entities = [
        TaskEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        BlockEntity::class,
        EntryEntity::class,
        TimerStateEntity::class,
        SettingEntity::class,
        MetaEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun tagDao(): TagDao

    abstract fun blockDao(): BlockDao

    abstract fun entryDao(): EntryDao

    abstract fun timerStateDao(): TimerStateDao

    companion object {
        const val FILE_NAME = "ttt.db"

        /** Opens (or creates) the app's database file. Room enables foreign keys for us. */
        fun build(context: Context, clock: Clock, appVersionName: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(WriteMetaOnCreate(clock, appVersionName))
                .build()
    }
}

/** Fills the meta table once, when the database file is first created. */
private class WriteMetaOnCreate(
    private val clock: Clock,
    private val appVersionName: String,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val insert = "INSERT INTO meta (`key`, `value`) VALUES (?, ?)"
        db.execSQL(insert, arrayOf<Any?>("created_at", clock.nowMs().toString()))
        db.execSQL(insert, arrayOf<Any?>("app_version_at_creation", appVersionName))
    }
}
