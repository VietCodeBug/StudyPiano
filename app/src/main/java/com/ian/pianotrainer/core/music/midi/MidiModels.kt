package com.ian.pianotrainer.core.music.midi

data class ParsedMidiFile(
    val format: Int,
    val ticksPerQuarterNote: Int,
    val durationMs: Long,
    val defaultBpm: Int,
    val tracks: List<ParsedMidiTrack>,
    val tempos: List<ParsedTempoEvent>,
    val timeSignatures: List<ParsedTimeSignatureEvent>
)

data class ParsedMidiTrack(
    val trackIndex: Int,
    val trackName: String,
    val channelSummary: String,
    val instrumentNumber: Int? = null,
    val noteCount: Int,
    val minMidiNote: Int,
    val maxMidiNote: Int,
    val defaultHand: String, // LEFT, RIGHT, BOTH, IGNORE
    val notes: List<ParsedMidiNote>
)

data class ParsedMidiNote(
    val trackIndex: Int,
    val channel: Int,
    val midiNote: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long,
    val startMs: Long,
    val durationMs: Long,
    var assignedHand: String = "BOTH",
    val chordId: String? = null
)

data class ParsedTempoEvent(
    val tick: Long,
    val startMs: Long,
    val microsecondsPerQuarterNote: Long,
    val bpm: Int
)

data class ParsedTimeSignatureEvent(
    val tick: Long,
    val numerator: Int,
    val denominator: Int
)
