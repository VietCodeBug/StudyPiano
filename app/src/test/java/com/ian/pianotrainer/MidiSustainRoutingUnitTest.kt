package com.ian.pianotrainer

import com.ian.pianotrainer.core.audio.PianoAudioAvailability
import com.ian.pianotrainer.core.audio.PianoAudioEngine
import com.ian.pianotrainer.core.audio.PianoAudioState
import com.ian.pianotrainer.domain.model.MidiControlEvent
import com.ian.pianotrainer.feature.practice.MidiSustainRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiSustainRoutingUnitTest {
    private class Audio : PianoAudioEngine {
        override val state: StateFlow<PianoAudioState> = MutableStateFlow(PianoAudioState())
        override val availability: StateFlow<PianoAudioAvailability> = MutableStateFlow(PianoAudioAvailability.Ready(1))
        val sustain = mutableListOf<Boolean>(); var allOff=0
        override suspend fun prepare(){}; override fun noteOn(midiNote:Int,velocity:Int,channel:Int){}
        override fun noteOff(midiNote:Int,channel:Int){}; override fun sustainPedal(isDown:Boolean){sustain+=isDown}
        override fun allNotesOff(){allOff++}; override fun setMasterVolume(volume:Float){}; override suspend fun release(){}
    }
    @Test fun `CC64 down and up route to audio engine`() {
        val audio=Audio(); val router=MidiSustainRouter(audio)
        router.onControlEvent(MidiControlEvent(controllerNumber=64,value=127)); router.onControlEvent(MidiControlEvent(controllerNumber=64,value=0))
        assertEquals(listOf(true,false),audio.sustain)
    }
    @Test fun `reset releases sustain and all voices`() {
        val audio=Audio(); MidiSustainRouter(audio).reset(); assertEquals(listOf(false),audio.sustain); assertEquals(1,audio.allOff)
    }
}