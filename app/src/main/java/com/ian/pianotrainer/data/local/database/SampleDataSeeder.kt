package com.ian.pianotrainer.data.local.database

import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SampleDataSeeder(private val database: PianoTrainerDatabase) {

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val songCount = database.importedSongDao().getSongCount()
        if (songCount == 0) {
            forceSeed()
        }
    }

    suspend fun forceSeed() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86400000L
        val twoDaysAgo = now - 172800000L

        // 1. Seed Demo Imported Songs
        val sampleSongs = listOf(
            ImportedSongEntity(
                id = "song_demo_canon_d",
                displayName = "Canon in D - Đoạn mở đầu (Bản mẫu)",
                originalFileName = "canon_in_d_intro.mid",
                localFilePath = null,
                durationMs = 45000L,
                defaultBpm = 60,
                difficulty = "Cơ bản",
                importedAt = twoDaysAgo,
                lastPracticedAt = oneDayAgo,
                isFavorite = true
            ),
            ImportedSongEntity(
                id = "song_demo_twinkle",
                displayName = "Twinkle Twinkle Little Star (Bản mẫu)",
                originalFileName = "twinkle_star.mid",
                localFilePath = null,
                durationMs = 30000L,
                defaultBpm = 70,
                difficulty = "Dễ",
                importedAt = oneDayAgo,
                lastPracticedAt = now,
                isFavorite = false
            ),
            ImportedSongEntity(
                id = "song_demo_fur_elise",
                displayName = "Für Elise - Đoạn đầu A (Bản mẫu)",
                originalFileName = "fur_elise_theme.mid",
                localFilePath = null,
                durationMs = 50000L,
                defaultBpm = 65,
                difficulty = "Trung bình",
                importedAt = now,
                lastPracticedAt = null,
                isFavorite = true
            )
        )
        database.importedSongDao().insertSongs(sampleSongs)

        // 2. Seed Demo Lesson Progress
        val sampleProgress = listOf(
            LessonProgressEntity(
                lessonId = "lesson_1_1_posture",
                completionPercent = 100,
                isCompleted = true,
                bestAccuracy = 100f,
                bestBpm = 60,
                lastPosition = 1,
                updatedAt = twoDaysAgo
            ),
            LessonProgressEntity(
                lessonId = "lesson_1_2_black_keys",
                completionPercent = 100,
                isCompleted = true,
                bestAccuracy = 95f,
                bestBpm = 65,
                lastPosition = 3,
                updatedAt = oneDayAgo
            ),
            LessonProgressEntity(
                lessonId = "lesson_2_1_c_d_e",
                completionPercent = 80,
                isCompleted = false,
                bestAccuracy = 88f,
                bestBpm = 60,
                lastPosition = 4,
                updatedAt = now
            )
        )
        database.lessonProgressDao().insertOrUpdateProgressList(sampleProgress)

        // 3. Seed Demo Practice Sessions
        val session1 = PracticeSessionEntity(
            id = "session_demo_1",
            sourceType = "LESSON",
            sourceId = "lesson_1_1_posture",
            practiceMode = PracticeMode.WAIT_FOR_NOTE.name,
            handMode = HandMode.RIGHT.name,
            displayMode = DisplayMode.FALLING_NOTES.name,
            bpm = 60,
            startedAt = twoDaysAgo,
            durationMs = 120000L,
            totalExpectedNotes = 10,
            correctNotes = 10,
            wrongNotes = 0,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 100.0f
        )
        val session2 = PracticeSessionEntity(
            id = "session_demo_2",
            sourceType = "LESSON",
            sourceId = "lesson_1_2_black_keys",
            practiceMode = PracticeMode.IN_TEMPO.name,
            handMode = HandMode.RIGHT.name,
            displayMode = DisplayMode.FALLING_NOTES.name,
            bpm = 65,
            startedAt = oneDayAgo,
            durationMs = 180000L,
            totalExpectedNotes = 15,
            correctNotes = 14,
            wrongNotes = 1,
            missedNotes = 0,
            earlyNotes = 1,
            lateNotes = 0,
            accuracy = 93.3f
        )
        database.practiceSessionDao().insertSessions(listOf(session1, session2))

        val noteResults1 = listOf(
            PracticeNoteResultEntity(
                sessionId = "session_demo_1",
                expectedMidiNote = 60,
                playedMidiNote = 60,
                timingOffsetMs = 12L,
                resultType = NoteResultType.CORRECT.name,
                occurredAtOffsetMs = 1500L
            )
        )
        database.practiceNoteResultDao().insertNoteResults(noteResults1)
    }

    suspend fun clearAndReset() = withContext(Dispatchers.IO) {
        database.practiceSessionDao().clearAll()
        database.importedSongDao().clearAll()
        database.lessonProgressDao().clearAll()
        forceSeed()
    }
}
