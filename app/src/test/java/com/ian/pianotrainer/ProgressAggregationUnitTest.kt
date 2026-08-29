package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity
import com.ian.pianotrainer.data.repository.ProgressRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ProgressAggregationUnitTest {

    private lateinit var db: PianoTrainerDatabase
    private lateinit var repository: ProgressRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PianoTrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = ProgressRepositoryImpl(
            sessionDao = db.practiceSessionDao(),
            noteResultDao = db.practiceNoteResultDao(),
            lessonProgressDao = db.lessonProgressDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun weightedAccuracy_calculatesTotalCorrectOverTotalExpected() = runBlocking {
        val now = System.currentTimeMillis()

        // Session 1: 10/10 notes correct (100%)
        val s1 = PracticeSessionEntity(
            id = "s1",
            sourceType = "SONG",
            sourceId = "song1",
            practiceMode = "RHYTHM",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 100,
            startedAt = now - 10000,
            durationMs = 5000,
            totalExpectedNotes = 10,
            correctNotes = 10,
            wrongNotes = 0,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 1.0f
        )

        // Session 2: 10/90 notes correct (~11.1%)
        val s2 = PracticeSessionEntity(
            id = "s2",
            sourceType = "SONG",
            sourceId = "song2",
            practiceMode = "RHYTHM",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 120,
            startedAt = now - 5000,
            durationMs = 25000,
            totalExpectedNotes = 90,
            correctNotes = 10,
            wrongNotes = 80,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 0.111f
        )

        db.practiceSessionDao().insertSessions(listOf(s1, s2))

        val summary = repository.getProgressSummary(7).first()

        // Total correct = 20, Total expected = 100 -> Weighted accuracy = 0.20 (20%)
        assertEquals(0.20f, summary.weightedAccuracy, 0.001f)
        assertEquals(2, summary.totalSessionsCount)
    }

    @Test
    fun weakPitches_aggregatesTop5MistakeNotes() = runBlocking {
        val s1 = PracticeSessionEntity(
            id = "s1",
            sourceType = "SONG",
            sourceId = "song1",
            practiceMode = "RHYTHM",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 100,
            startedAt = System.currentTimeMillis() - 1000,
            durationMs = 5000,
            totalExpectedNotes = 10,
            correctNotes = 5,
            wrongNotes = 5,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 0.5f
        )
        db.practiceSessionDao().insertSessions(listOf(s1))

        val results = listOf(
            PracticeNoteResultEntity(id = 1, sessionId = "s1", expectedMidiNote = 60, playedMidiNote = 61, timingOffsetMs = 0L, resultType = "WRONG", occurredAtOffsetMs = 100),
            PracticeNoteResultEntity(id = 2, sessionId = "s1", expectedMidiNote = 60, playedMidiNote = 62, timingOffsetMs = 0L, resultType = "WRONG", occurredAtOffsetMs = 200),
            PracticeNoteResultEntity(id = 3, sessionId = "s1", expectedMidiNote = 60, playedMidiNote = null, timingOffsetMs = null, resultType = "MISSED", occurredAtOffsetMs = 300),
            PracticeNoteResultEntity(id = 4, sessionId = "s1", expectedMidiNote = 64, playedMidiNote = 65, timingOffsetMs = 0L, resultType = "WRONG", occurredAtOffsetMs = 400),
            PracticeNoteResultEntity(id = 5, sessionId = "s1", expectedMidiNote = 64, playedMidiNote = 65, timingOffsetMs = 0L, resultType = "WRONG", occurredAtOffsetMs = 500),
            PracticeNoteResultEntity(id = 6, sessionId = "s1", expectedMidiNote = 67, playedMidiNote = 67, timingOffsetMs = 0L, resultType = "CORRECT", occurredAtOffsetMs = 600)
        )

        db.practiceNoteResultDao().insertNoteResults(results)

        val summary = repository.getProgressSummary(null).first()
        val weakPitches = summary.weakPitches

        assertTrue(weakPitches.isNotEmpty())
        assertEquals(60, weakPitches[0].midiNote) // C4 with 3 mistakes
        assertEquals(3, weakPitches[0].totalMistakes)
        assertEquals(64, weakPitches[1].midiNote) // E4 with 2 mistakes
        assertEquals(2, weakPitches[1].totalMistakes)
    }

    @Test
    fun calculateStreak_detectsConsecutiveDays() = runBlocking {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)

        val s1 = PracticeSessionEntity(
            id = "str1",
            sourceType = "SONG",
            sourceId = "song1",
            practiceMode = "WAIT_FOR_NOTE",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 100,
            startedAt = twoDaysAgo.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            durationMs = 60000,
            totalExpectedNotes = 20,
            correctNotes = 20,
            wrongNotes = 0,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 1.0f
        )
        val s2 = PracticeSessionEntity(
            id = "str2",
            sourceType = "SONG",
            sourceId = "song1",
            practiceMode = "WAIT_FOR_NOTE",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 100,
            startedAt = yesterday.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            durationMs = 60000,
            totalExpectedNotes = 20,
            correctNotes = 20,
            wrongNotes = 0,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 1.0f
        )
        val s3 = PracticeSessionEntity(
            id = "str3",
            sourceType = "SONG",
            sourceId = "song1",
            practiceMode = "WAIT_FOR_NOTE",
            handMode = "BOTH",
            displayMode = "FALLING_NOTES",
            bpm = 100,
            startedAt = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            durationMs = 60000,
            totalExpectedNotes = 20,
            correctNotes = 20,
            wrongNotes = 0,
            missedNotes = 0,
            earlyNotes = 0,
            lateNotes = 0,
            accuracy = 1.0f
        )

        db.practiceSessionDao().insertSessions(listOf(s1, s2, s3))

        val summary = repository.getProgressSummary(7).first()
        assertEquals(3, summary.currentStreakDays)
        assertEquals(3, summary.longestStreakDays)
    }
}
