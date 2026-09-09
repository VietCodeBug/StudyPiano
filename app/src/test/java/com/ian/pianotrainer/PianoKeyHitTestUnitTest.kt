package com.ian.pianotrainer

import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PianoKeyHitTestUnitTest {
    private val range = PianoGeometryCalculator.calculateRangeGeometries(60, 72, 700f)

    @Test
    fun upperKeyboard_prefersBlackKeyWhenKeysOverlap() {
        val black = range.geometries.getValue(61)
        assertEquals(61, PianoGeometryCalculator.findNoteAt(
            range.geometries.values, black.centerX, 20f, 120f, 75f
        ))
    }

    @Test
    fun lowerKeyboard_resolvesWhiteKeyBelowBlackKeys() {
        val black = range.geometries.getValue(61)
        assertEquals(60, PianoGeometryCalculator.findNoteAt(
            range.geometries.values, black.centerX - 1f, 100f, 120f, 75f
        ))
    }

    @Test
    fun outsideKeyboard_returnsNoNote() {
        assertNull(PianoGeometryCalculator.findNoteAt(
            range.geometries.values, 10f, 121f, 120f, 75f
        ))
    }
}
