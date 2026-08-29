package com.ian.pianotrainer.domain.model

/**
 * Single source of truth for Practice Player lifecycle state machine.
 */
enum class PlayerPhase {
    LOADING,
    READY,
    COUNT_IN,
    PLAYING,
    WAITING_FOR_CHORD,
    PAUSED,
    SEEKING,
    FINISHED,
    ERROR
}
