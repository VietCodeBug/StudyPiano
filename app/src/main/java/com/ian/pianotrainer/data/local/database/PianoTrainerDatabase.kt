package com.ian.pianotrainer.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ian.pianotrainer.data.local.database.dao.FreePlayRecordingDao
import com.ian.pianotrainer.data.local.database.dao.ImportedSongDao
import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.dao.PracticeNoteResultDao
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.data.local.database.dao.SongNoteDao
import com.ian.pianotrainer.data.local.database.dao.SongPracticePresetDao
import com.ian.pianotrainer.data.local.database.dao.SongTempoDao
import com.ian.pianotrainer.data.local.database.dao.SongTimeSignatureDao
import com.ian.pianotrainer.data.local.database.dao.SongTrackDao
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordedEventEntity
import com.ian.pianotrainer.data.local.database.entity.FreePlayRecordingEntity
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongPracticePresetEntity
import com.ian.pianotrainer.data.local.database.entity.SongTempoEntity
import com.ian.pianotrainer.data.local.database.entity.SongTimeSignatureEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity

@Database(
    entities = [
        ImportedSongEntity::class,
        SongTrackEntity::class,
        SongNoteEntity::class,
        SongTempoEntity::class,
        SongTimeSignatureEntity::class,
        LessonProgressEntity::class,
        PracticeSessionEntity::class,
        PracticeNoteResultEntity::class,
        FreePlayRecordingEntity::class,
        FreePlayRecordedEventEntity::class,
        SongPracticePresetEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class PianoTrainerDatabase : RoomDatabase() {
    abstract fun importedSongDao(): ImportedSongDao
    abstract fun songTrackDao(): SongTrackDao
    abstract fun songNoteDao(): SongNoteDao
    abstract fun songTempoDao(): SongTempoDao
    abstract fun songTimeSignatureDao(): SongTimeSignatureDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun practiceNoteResultDao(): PracticeNoteResultDao
    abstract fun freePlayRecordingDao(): FreePlayRecordingDao
    abstract fun songPracticePresetDao(): SongPracticePresetDao

    companion object {
        const val DATABASE_NAME = "piano_trainer.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Update imported_songs table
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN fileHashSha256 TEXT")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN fileSizeBytes INTEGER")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN midiFormatType INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN ticksPerQuarterNote INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN trackCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN parseStatus TEXT NOT NULL DEFAULT 'READY'")
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN parseErrorMessage TEXT")

                // 2. Create song_tracks table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_tracks (
                        songId TEXT NOT NULL,
                        trackIndex INTEGER NOT NULL,
                        trackName TEXT NOT NULL,
                        channelSummary TEXT NOT NULL,
                        instrumentNumber INTEGER,
                        noteCount INTEGER NOT NULL,
                        minMidiNote INTEGER NOT NULL,
                        maxMidiNote INTEGER NOT NULL,
                        isSelectedForPractice INTEGER NOT NULL,
                        assignedHand TEXT NOT NULL,
                        PRIMARY KEY(songId, trackIndex),
                        FOREIGN KEY(songId) REFERENCES imported_songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_tracks_songId ON song_tracks(songId)")

                // 3. Create song_notes table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        trackIndex INTEGER NOT NULL,
                        channel INTEGER NOT NULL,
                        midiNote INTEGER NOT NULL,
                        velocity INTEGER NOT NULL,
                        startTick INTEGER NOT NULL,
                        endTick INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        assignedHand TEXT NOT NULL,
                        chordId TEXT,
                        FOREIGN KEY(songId) REFERENCES imported_songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_notes_songId ON song_notes(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_notes_songId_startMs ON song_notes(songId, startMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_notes_songId_trackIndex ON song_notes(songId, trackIndex)")

                // 4. Create song_tempos table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_tempos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        startTick INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        microsecondsPerQuarterNote INTEGER NOT NULL,
                        bpm INTEGER NOT NULL,
                        FOREIGN KEY(songId) REFERENCES imported_songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_tempos_songId ON song_tempos(songId)")

                // 5. Update practice_sessions table
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN endedAt INTEGER")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN pausedDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN sessionStatus TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN resumeCheckpointMs INTEGER")

                // 6. Clean up known fake demo data
                db.execSQL("DELETE FROM imported_songs WHERE id LIKE 'song_demo_%'")
                db.execSQL("DELETE FROM practice_sessions WHERE id LIKE 'session_demo_%'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS freeplay_recordings (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        noteCount INTEGER NOT NULL,
                        hasAudio INTEGER NOT NULL DEFAULT 0,
                        audioFilePath TEXT,
                        midiFilePath TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS freeplay_recorded_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordingId TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        midiNote INTEGER NOT NULL,
                        velocity INTEGER NOT NULL,
                        channel INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(recordingId) REFERENCES freeplay_recordings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_freeplay_recorded_events_recordingId ON freeplay_recorded_events(recordingId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE imported_songs ADD COLUMN noteCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE imported_songs SET noteCount = (SELECT COUNT(*) FROM song_notes WHERE song_notes.songId = imported_songs.id)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_time_signatures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId TEXT NOT NULL,
                        startTick INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        numerator INTEGER NOT NULL,
                        denominator INTEGER NOT NULL,
                        FOREIGN KEY(songId) REFERENCES imported_songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_time_signatures_songId ON song_time_signatures(songId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. practice_sessions new columns
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN sourceTitleSnapshot TEXT")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN score INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN maxStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN inputSource TEXT NOT NULL DEFAULT 'VIRTUAL_KEYBOARD'")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN effectiveSpeed REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN loopStartMs INTEGER")
                db.execSQL("ALTER TABLE practice_sessions ADD COLUMN loopEndMs INTEGER")

                // 2. freeplay_recordings new columns
                db.execSQL("ALTER TABLE freeplay_recordings ADD COLUMN inputSource TEXT NOT NULL DEFAULT 'VIRTUAL_KEYBOARD'")
                db.execSQL("ALTER TABLE freeplay_recordings ADD COLUMN bpm INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE freeplay_recordings ADD COLUMN fileStatus TEXT NOT NULL DEFAULT 'READY'")

                // 3. freeplay_recorded_events new columns
                db.execSQL("ALTER TABLE freeplay_recorded_events ADD COLUMN controlNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE freeplay_recorded_events ADD COLUMN controlValue INTEGER NOT NULL DEFAULT 0")

                // 4. song_practice_presets table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS song_practice_presets (
                        id TEXT PRIMARY KEY NOT NULL,
                        songId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        loopStartMs INTEGER,
                        loopEndMs INTEGER,
                        handMode TEXT NOT NULL DEFAULT 'BOTH',
                        practiceMode TEXT NOT NULL DEFAULT 'WAIT_FOR_NOTE',
                        targetBpm INTEGER NOT NULL DEFAULT 120,
                        speedMultiplier REAL NOT NULL DEFAULT 1.0,
                        lookAhead INTEGER NOT NULL DEFAULT 4000,
                        noteDisplaySize TEXT NOT NULL DEFAULT 'MEDIUM',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(songId) REFERENCES imported_songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_practice_presets_songId ON song_practice_presets(songId)")
            }
        }

        @Volatile
        private var INSTANCE: PianoTrainerDatabase? = null

        fun getInstance(context: Context): PianoTrainerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PianoTrainerDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
