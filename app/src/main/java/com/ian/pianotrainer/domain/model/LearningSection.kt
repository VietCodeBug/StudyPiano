package com.ian.pianotrainer.domain.model

enum class SectionPracticeStep(
    val stepIndex: Int,
    val title: String,
    val handMode: HandMode,
    val practiceMode: PracticeMode,
    val speedMultiplier: Float,
    val isDemo: Boolean
) {
    STEP_1_DEMO(1, "Nghe mẫu", HandMode.BOTH, PracticeMode.RHYTHM, 1.0f, true),
    STEP_2_RIGHT_WAIT(2, "Tay phải — Chờ nốt", HandMode.RIGHT, PracticeMode.WAIT_FOR_NOTE, 1.0f, false),
    STEP_3_LEFT_WAIT(3, "Tay trái — Chờ nốt", HandMode.LEFT, PracticeMode.WAIT_FOR_NOTE, 1.0f, false),
    STEP_4_BOTH_WAIT(4, "Hai tay — Chờ nốt", HandMode.BOTH, PracticeMode.WAIT_FOR_NOTE, 1.0f, false),
    STEP_5_BOTH_50(5, "Hai tay — 50% tốc độ", HandMode.BOTH, PracticeMode.RHYTHM, 0.5f, false),
    STEP_6_BOTH_70(6, "Hai tay — 70% tốc độ", HandMode.BOTH, PracticeMode.RHYTHM, 0.7f, false),
    STEP_7_BOTH_85(7, "Hai tay — 85% tốc độ", HandMode.BOTH, PracticeMode.RHYTHM, 0.85f, false),
    STEP_8_BOTH_100(8, "Hai tay — 100% tốc độ", HandMode.BOTH, PracticeMode.RHYTHM, 1.0f, false)
}

data class LearningSection(
    val id: String,
    val songId: String,
    val startMs: Long,
    val endMs: Long,
    val startMeasure: Int? = null,
    val endMeasure: Int? = null,
    val label: String,
    val difficulty: Int = 1,
    val noteCount: Int = 0,
    val completedSteps: Set<Int> = emptySet(),
    val currentStep: SectionPracticeStep = SectionPracticeStep.STEP_1_DEMO
)

data class WeakMeasureStat(
    val measureNumber: Int,
    val startMs: Long,
    val endMs: Long,
    val errorCount: Int,
    val accuracyPercent: Int
)
