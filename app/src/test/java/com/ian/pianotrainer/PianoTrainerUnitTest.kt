package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.domain.model.NoteNamingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PianoTrainerUnitTest {
    @Test
    fun noteHelper_correctMidiToNoteName() {
        assertEquals("C4", NoteHelper.formatNoteName(60, NoteNamingMode.CDE))
        assertEquals("Đô4", NoteHelper.formatNoteName(60, NoteNamingMode.DOREMI))
        assertEquals("A4", NoteHelper.formatNoteName(69, NoteNamingMode.CDE))
        assertEquals("La4", NoteHelper.formatNoteName(69, NoteNamingMode.DOREMI))
    }

    @Test
    fun noteHelper_detectsBlackKeys() {
        assertEquals(false, NoteHelper.isBlackKey(60)) // C4
        assertEquals(true, NoteHelper.isBlackKey(61))  // C#4
        assertEquals(false, NoteHelper.isBlackKey(62)) // D4
        assertEquals(true, NoteHelper.isBlackKey(63))  // D#4
        assertEquals(false, NoteHelper.isBlackKey(64)) // E4
        assertEquals(false, NoteHelper.isBlackKey(65)) // F4
        assertEquals(true, NoteHelper.isBlackKey(66))  // F#4
    }

    @Test
    fun noteHelper_detectsMiddleC() {
        assertEquals(true, NoteHelper.isMiddleC(60))
        assertEquals(false, NoteHelper.isMiddleC(61))
    }
}
