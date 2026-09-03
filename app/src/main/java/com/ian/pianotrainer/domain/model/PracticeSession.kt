package com.ian.pianotrainer.domain.model

data class TempoSettings(
    val bpm: Int = 60,
    val minBpm: Int = 40,
    val maxBpm: Int = 120
)

data class PracticeConfiguration(
    val title: String,
    val sourceId: String,
    val sourceType: String, // "LESSON", "EXERCISE", "SONG", "GOAL", "FREE_PLAY"
    val practiceMode: PracticeMode = PracticeMode.WAIT_FOR_NOTE,
    val handMode: HandMode = HandMode.RIGHT,
    val displayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val bpm: Int = 60,
    val notes: List<ExerciseNote> = emptyList()
)

data class PracticeSession(
    val id: String,
    val sourceType: String,
    val sourceId: String?,
    val practiceMode: PracticeMode,
    val handMode: HandMode,
    val displayMode: DisplayMode,
    val bpm: Int,
    val startedAt: Long,
    val durationMs: Long,
    val totalExpectedNotes: Int,
    val correctNotes: Int,
    val wrongNotes: Int,
    val missedNotes: Int,
    val earlyNotes: Int,
    val lateNotes: Int,
    val accuracy: Float,
    val noteResults: List<PracticeNoteResult> = emptyList(),
    val sourceTitleSnapshot: String? = null,
    val score: Int = 0,
    val maxStreak: Int = 0,
    val inputSource: String = "VIRTUAL_KEYBOARD",
    val effectiveSpeed: Float = 1.0f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null
)

data class PracticeNoteResult(
    val id: Long = 0,
    val sessionId: String,
    val expectedMidiNote: Int?,
    val playedMidiNote: Int?,
    val timingOffsetMs: Long?,
    val resultType: NoteResultType,
    val occurredAtOffsetMs: Long
)

data class PracticeResult(
    val totalScore: Int,
    val accuracy: Float,
    val totalExpectedNotes: Int,
    val correctNotes: Int,
    val wrongNotes: Int,
    val missedNotes: Int,
    val earlyNotes: Int,
    val lateNotes: Int,
    val maxStreak: Int,
    val durationMs: Long,
    val session: PracticeSession? = null
)

data class DailyPracticeStat(
    val dayLabel: String,
    val dateIso: String,
    val durationMinutes: Long
)

data class WeakPitchStat(
    val midiNote: Int,
    val noteName: String,
    val wrongCount: Int,
    val missedCount: Int,
    val totalMistakes: Int
)

data class ProgressSummary(
    val totalPracticeTimeMinutes: Long = 0,
    val todayPracticeTimeMinutes: Long = 0,
    val totalSessionsCount: Int = 0,
    val averageAccuracy: Float = 0f,
    val weightedAccuracy: Float = 0f,
    val bestBpm: Int = 0,
    val completedLessonsCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val weeklyHistory: List<DailyPracticeStat> = emptyList(),
    val recentSessions: List<PracticeSession> = emptyList(),
    val weakPitches: List<WeakPitchStat> = emptyList(),
    val mostPracticedSongTitle: String? = null,
    val mostPracticedSongCount: Int = 0
)

data class UserSettings(
    val noteNamingMode: NoteNamingMode = NoteNamingMode.CDE,
    val defaultDisplayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val defaultBpm: Int = 60,
    val virtualPianoSoundEnabled: Boolean = false,
    val metronomeVolume: Float = 0.8f,
    val lastSelectedHandMode: HandMode = HandMode.RIGHT,
    val lastKnownMidiDeviceName: String = "",
    val onboardingCompleted: Boolean = true,
    val demoDataInitialized: Boolean = false,
    val dailyGoalMinutes: Int = 20,
    val countInOption: String = "OFF", // OFF, ONE_MEASURE, TWO_MEASURES
    val autoReconnectMidi: Boolean = true,
    val keyboardRangeMode: KeyboardRangeMode = KeyboardRangeMode.FULL_88_KEYS,
    val defaultLookAheadMs: Long = 4000L
)
