package com.ian.pianotrainer

import com.ian.pianotrainer.feature.practice.ToolbarAutoHideController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolbarAutoHideUnitTest {
    @Test fun `running rhythm and demo hide after three seconds`() = runTest {
        var visible = false
        val controller = ToolbarAutoHideController(this) { visible = it }
        controller.showAndSchedule(running = true)
        assertTrue(visible)
        advanceTimeBy(3000L); runCurrent()
        assertFalse(visible)
        controller.showAndSchedule(running = true)
        advanceTimeBy(3000L); runCurrent()
        assertFalse(visible)
    }
    @Test fun `pause and settings cancel hide and closing schedules again`() = runTest {
        var visible = false
        val controller = ToolbarAutoHideController(this) { visible = it }
        controller.showAndSchedule(true)
        controller.cancelAndShow()
        advanceTimeBy(4000L); runCurrent()
        assertTrue(visible)
        controller.showAndSchedule(true)
        advanceTimeBy(3000L); runCurrent()
        assertFalse(visible)
    }
}