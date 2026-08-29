package com.ian.pianotrainer.core.audio

/**
 * Defines how a track, hand, or note voice is scheduled during playback.
 */
enum class PlaybackRole {
    /** The user must play this part; app synth will NOT play it during practice */
    PRACTICE,

    /** The app synth automatically plays this part as accompaniment/backing */
    ACCOMPANIMENT,

    /** The part is completely muted (neither played nor evaluated) */
    MUTED,

    /** Demo mode: app synth plays all parts for listening/preview */
    DEMO
}
