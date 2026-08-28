package com.ian.pianotrainer.domain.model

data class MidiNoteEvent(
    val channel: Int = 0,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class MidiControlEvent(
    val channel: Int = 0,
    val controllerNumber: Int,
    val value: Int,
    val timestampMs: Long = System.currentTimeMillis()
)
