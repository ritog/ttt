/**
 * Migrations — every schema change, one Migration object per version step, in order.
 *
 * Depends on: Room migration API.
 * Must not: ever be replaced by fallbackToDestructiveMigration: that would wipe the user's records.
 *
 * Version history:
 *   1  initial schema (all tables of SPEC 5.1)
 *
 * To change the schema: bump the version in AppDatabase, add a Migration here (and to
 * ALL_MIGRATIONS), commit the new app/schemas/…/N.json file, add a test in MigrationTest,
 * and update docs/DATA.md. Recipe: docs/CHANGING.md.
 */
package com.ritoghosh.ttt.data.db

import androidx.room.migration.Migration

/** Current schema version. */
const val DATABASE_VERSION = 1

/** All migrations, oldest first. Empty until the first schema change. */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
