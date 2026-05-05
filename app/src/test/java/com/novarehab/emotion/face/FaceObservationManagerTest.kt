package com.novarehab.emotion.face

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceObservationManagerTest {
    @Test
    fun disabledManagerReturnsNeutralObservation() {
        val manager = FaceObservationManager()

        val observation = manager.observe(
            faceDetected = true,
            smileProbability = 0.0f,
            leftEyeOpenProbability = 0.1f,
            rightEyeOpenProbability = 0.1f,
            headEulerY = 0f,
            headEulerZ = 20f
        )

        assertFalse(observation.faceDetected)
        assertFalse(observation.possibleFatigue)
        assertFalse(observation.possibleFrustration)
    }

    @Test
    fun enabledManagerOnlyProducesPassiveFlags() {
        val manager = FaceObservationManager()
        manager.setEnabled(true)

        val observation = manager.observe(
            faceDetected = true,
            smileProbability = 0.0f,
            leftEyeOpenProbability = 0.1f,
            rightEyeOpenProbability = 0.1f,
            headEulerY = 5f,
            headEulerZ = 20f
        )

        assertTrue(observation.possibleFatigue)
        assertTrue(observation.possibleFrustration)
    }
}
