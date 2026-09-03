package com.ian.pianotrainer

import com.ian.pianotrainer.core.audio.PianoAudioAvailability
import com.ian.pianotrainer.feature.practice.PianoAudioCapabilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAvailabilityUnitTest {
    @Test fun `missing samples are unavailable and app sound cannot be enabled`() {
        val state=PianoAudioAvailability.Unavailable("missing")
        assertFalse(PianoAudioCapabilityPolicy.canEnable(state))
        assertEquals("Xem chuyển động mẫu",PianoAudioCapabilityPolicy.demoLabel(state))
    }
    @Test fun `ready is only capability that enables audio`() {
        assertFalse(PianoAudioCapabilityPolicy.canEnable(PianoAudioAvailability.Loading))
        assertFalse(PianoAudioCapabilityPolicy.canEnable(PianoAudioAvailability.Error("load")))
        assertTrue(PianoAudioCapabilityPolicy.canEnable(PianoAudioAvailability.Ready(7)))
    }
}