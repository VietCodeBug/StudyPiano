package com.ian.pianotrainer.domain.model

data class TempoSettings(
    val bpm: Int = 60,
    val minBpm: Int = 40,
    val maxBpm: Int = 120
)

data class PracticeConfiguration(
    val title: String,
    val sourceId: String,
    val sourceType: String, // "LESSON", "EXERCISE", "SONG", "GOAL"
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
    val noteResults: List<PracticeNoteResult> = emptyList()
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

data class ProgressSummary(
    val totalPracticeTimeMinutes: Long = 0,
    val totalSessionsCount: Int = 0,
    val averageAccuracy: Float = 0f,
    val bestBpm: Int = 0,
    val completedLessonsCount: Int = 0,
    val currentStreakDays: Int = 0,
    val recentSessions: List<PracticeSession> = emptyList()
)

data class UserSettings(
    val noteNamingMode: NoteNamingMode = NoteNamingMode.CDE,
    val defaultDisplayMode: DisplayMode = DisplayMode.FALLING_NOTES,
    val defaultBpm: Int = 60,
    val virtualPianoSoundEnabled: Boolean = true,
    val metronomeVolume: Float = 0.8f,
    val lastSelectedHandMode: HandMode = HandMode.RIGHT,
    val lastKnownMidiDeviceName: String = "",
    val onboardingCompleted: Boolean = true,
    val demoDataInitialized: Boolean = false
)
