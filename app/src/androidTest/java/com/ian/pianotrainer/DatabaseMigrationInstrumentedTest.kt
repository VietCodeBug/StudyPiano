package com.ian.pianotrainer

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PianoTrainerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration5To6_preservesSongAndCreatesMidiAsset() {
        val name = "migration-5-6-${System.nanoTime()}"
        helper.createDatabase(name, 5).apply {
            execSQL(
                """INSERT INTO imported_songs
                    (id, displayName, originalFileName, localFilePath, fileHashSha256, fileSizeBytes,
                     midiFormatType, ticksPerQuarterNote, trackCount, noteCount, durationMs, defaultBpm,
                     difficulty, importedAt, lastPracticedAt, isFavorite, parseStatus, parseErrorMessage)
                    VALUES ('legacy', 'Legacy', 'legacy.mid', '/safe/legacy.mid', 'abc', 123,
                            1, 480, 1, 9, 1000, 90, 'Cơ bản', 10, 20, 1, 'READY', NULL)"""
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, PianoTrainerDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT displayName, isFavorite, lastPracticedAt FROM imported_songs WHERE id='legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy", cursor.getString(0)); assertEquals(1, cursor.getInt(1)); assertEquals(20L, cursor.getLong(2))
            }
            db.query("SELECT songId, type, localFilePath FROM song_assets WHERE songId='legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy", cursor.getString(0)); assertEquals("MIDI", cursor.getString(1)); assertEquals("/safe/legacy.mid", cursor.getString(2))
            }
        }
    }

    @Test
    fun migration6To7_preservesAssetsAndCreatesRestoreCommitTable() {
        val name = "migration-6-7-${System.nanoTime()}"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO imported_songs (id, displayName, originalFileName, localFilePath, midiFormatType, ticksPerQuarterNote, trackCount, noteCount, durationMs, defaultBpm, difficulty, importedAt, isFavorite, parseStatus) VALUES ('kept', 'Kept', 'kept.mid', '/songs/kept/source.mid', 1, 480, 1, 1, 10, 120, 'TEST', 1, 0, 'READY')")
            execSQL("INSERT INTO song_assets (id, songId, type, originalFileName, localFilePath, fileSizeBytes, mimeType) VALUES ('kept_midi', 'kept', 'MIDI', 'kept.mid', '/songs/kept/source.mid', 10, 'audio/midi')")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, PianoTrainerDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT COUNT(*) FROM song_assets WHERE songId='kept'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
            db.execSQL("INSERT INTO restore_commits(operationId, committedAt) VALUES ('op-test', 123)")
            db.query("SELECT committedAt FROM restore_commits WHERE operationId='op-test'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(123L, cursor.getLong(0)) }
        }
    }
}
