package com.ian.pianotrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.practice.RealMetronomeController
import com.ian.pianotrainer.domain.service.MetronomeSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealMetronomeControllerUnitTest {
    @Test fun settingsPersistAndRepeatedStartDoesNotCreateASecondRunState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("metronome_audio", Context.MODE_PRIVATE).edit().clear().commit()
        val first = RealMetronomeController(context, isAudioEnabled = false)
        first.setBpm(200)
        first.setBeatsPerBar(6)
        first.setAccentEnabled(false)
        first.setSound(MetronomeSound.WOOD)
        first.setVolume(0.35f)
        first.start(200)
        first.start(120)
        assertTrue(first.isRunning.value)
        assertEquals(120, first.bpm.value)
        first.stop()
        assertFalse(first.isRunning.value)
        first.release()

        val restored = RealMetronomeController(context, isAudioEnabled = false)
        assertEquals(120, restored.bpm.value)
        assertEquals(6, restored.beatsPerBar.value)
        assertFalse(restored.accentEnabled.value)
        assertEquals(MetronomeSound.WOOD, restored.getSound())
        assertEquals(0.35f, restored.volume.value)
        restored.release()
    }
}
