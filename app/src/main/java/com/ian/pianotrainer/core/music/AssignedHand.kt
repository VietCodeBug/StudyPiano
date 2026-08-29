package com.ian.pianotrainer.core.music

/**
 * Hand assignment for individual notes.
 * Used by HandSeparationEngine to tag each note after analysis.
 */
enum class AssignedHand {
    LEFT,
    RIGHT,
    UNASSIGNED
}
