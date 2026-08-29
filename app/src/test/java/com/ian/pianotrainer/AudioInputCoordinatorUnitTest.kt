package com.ian.pianotrainer

import com.ian.pianotrainer.core.midi.AudioInputCoordinator
import com.ian.pianotrainer.core.midi.AudioRecordOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioInputCoordinatorUnitTest {

    @Before
    fun setUp() {
        AudioInputCoordinator.forceReleaseAll()
    }

    @Test
    fun singleOwner_canAcquireAndRelease() {
        assertTrue(AudioInputCoordinator.isAvailable(AudioRecordOwner.PITCH_DETECTOR))
        assertTrue(AudioInputCoordinator.requestAccess(AudioRecordOwner.PITCH_DETECTOR))
        assertEquals(AudioRecordOwner.PITCH_DETECTOR, AudioInputCoordinator.currentOwner.value)

        AudioInputCoordinator.releaseAccess(AudioRecordOwner.PITCH_DETECTOR)
        assertEquals(AudioRecordOwner.NONE, AudioInputCoordinator.currentOwner.value)
        assertTrue(AudioInputCoordinator.isAvailable(AudioRecordOwner.FREE_PLAY_RECORDER))
    }

    @Test
    fun conflictingOwners_secondOwnerIsRejectedUntilFirstReleases() {
        assertTrue(AudioInputCoordinator.requestAccess(AudioRecordOwner.FREE_PLAY_RECORDER))

        assertFalse(AudioInputCoordinator.isAvailable(AudioRecordOwner.PITCH_DETECTOR))
        assertFalse(AudioInputCoordinator.requestAccess(AudioRecordOwner.PITCH_DETECTOR))

        AudioInputCoordinator.releaseAccess(AudioRecordOwner.FREE_PLAY_RECORDER)
        assertTrue(AudioInputCoordinator.isAvailable(AudioRecordOwner.PITCH_DETECTOR))
        assertTrue(AudioInputCoordinator.requestAccess(AudioRecordOwner.PITCH_DETECTOR))
    }
}
