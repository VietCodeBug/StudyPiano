package com.ian.pianotrainer.domain.model

/**
 * Declares what a song/piece can actually do, based on what files/data are present.
 * UI must only show features when the corresponding capability is true.
 */
data class SongCapabilities(
    /** True if the song has a parsed MIDI file (enables falling notes, scoring, speed control) */
    val hasMidi: Boolean,
    /** True if the app can synthesize audio from MIDI via PianoAudioEngine */
    val hasInternalSynthPlayback: Boolean,
    /** True if the song has an attached reference audio file (.ogg/.mp3/.m4a) */
    val hasReferenceAudio: Boolean,
    /** True if the song has a valid MusicXML/MXL file for sheet rendering */
    val hasMusicXml: Boolean,
    /** True if the song has a PDF or image for reference viewing (not interactive scoring) */
    val hasReferencePdfOrImage: Boolean,
    /** True if scoring (correct/wrong/missed) is possible — requires MIDI */
    val supportsScoring: Boolean,
    /** True if hand separation data exists (either auto or manual) */
    val supportsHandSeparation: Boolean
) {
    companion object {
        /** A song with only MIDI imported and synth engine available */
        fun midiOnly(hasHandSeparation: Boolean = false): SongCapabilities = SongCapabilities(
            hasMidi = true,
            hasInternalSynthPlayback = true,
            hasReferenceAudio = false,
            hasMusicXml = false,
            hasReferencePdfOrImage = false,
            supportsScoring = true,
            supportsHandSeparation = hasHandSeparation
        )

        /** Nothing loaded yet */
        val EMPTY = SongCapabilities(
            hasMidi = false,
            hasInternalSynthPlayback = false,
            hasReferenceAudio = false,
            hasMusicXml = false,
            hasReferencePdfOrImage = false,
            supportsScoring = false,
            supportsHandSeparation = false
        )
    }
}
