/**
 * MigrationTest — every schema version opens, and every migration keeps the data (SPEC 5.2, 13).
 *
 * Depends on: a device or emulator, room-testing, the exported schemas in app/schemas.
 * Must not: be skipped when the schema changes. Each new Migration gets a test here that
 * creates the old version, inserts a row, migrates, and checks the row.
 */
package com.ritoghosh.ttt.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1SchemaCanBeCreated() {
        helper.createDatabase(TEST_DATABASE, 1).close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
