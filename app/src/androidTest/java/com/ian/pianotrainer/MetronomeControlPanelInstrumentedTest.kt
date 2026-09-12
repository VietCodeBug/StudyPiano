package com.ian.pianotrainer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ian.pianotrainer.core.ui.MetronomeControlPanel
import com.ian.pianotrainer.domain.service.MetronomeSound
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetronomeControlPanelInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun bpmTextCanBeClearedAndOnlyAppliesOnDone() {
        var bpm by mutableIntStateOf(60)
        var applyCalls = 0
        compose.setContent {
            MetronomeControlPanel(false, bpm, 1, 4, true, MetronomeSound.MECHANICAL, 0.8f, false,
                {}, { bpm = it; applyCalls++ }, {}, {}, {}, {}, {}, {})
        }
        listOf(40, 60, 120, 200).forEachIndexed { index, value ->
            compose.onNodeWithTag("metronome_bpm_input").performTextClearance()
            compose.onNodeWithTag("metronome_bpm_input").performTextInput(value.toString())
            compose.runOnIdle { assertEquals(index, applyCalls) }
            compose.onNodeWithTag("metronome_bpm_input").performImeAction()
            compose.runOnIdle { assertEquals(value, bpm); assertEquals(index + 1, applyCalls) }
            compose.onNodeWithTag("metronome_bpm_input").assertTextEquals(value.toString(), "BPM 30–240")
        }
    }
}
