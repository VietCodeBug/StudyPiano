package com.ian.pianotrainer

import com.ian.pianotrainer.core.contentpack.ImportFileClassifier
import com.ian.pianotrainer.core.contentpack.ImportFileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportFileClassifierUnitTest {
    @Test fun `content uri without extension classifies MIDI by header`() {
        assertEquals(ImportFileKind.MIDI, ImportFileClassifier.classify(null, "application/octet-stream", "MThdmore".toByteArray()))
    }

    @Test fun `zip signature wins without display name`() {
        assertEquals(ImportFileKind.PIANO_PACK, ImportFileClassifier.classify(null, null, byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
    }

    @Test fun `misleading extension cannot make invalid bytes MIDI`() {
        assertEquals(ImportFileKind.UNSUPPORTED, ImportFileClassifier.classify("fake.mid", "audio/midi", "not-midi".toByteArray()))
    }
}
