package com.novarehab.emotion.face

data class FaceObservation(
    val faceDetected: Boolean = false,
    val smileProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headEulerY: Float? = null,
    val headEulerZ: Float? = null,
    val attentionLevel: Float = 0f,
    val possibleFatigue: Boolean = false,
    val possibleFrustration: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class FaceObservationManager {
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun observe(
        faceDetected: Boolean,
        smileProbability: Float?,
        leftEyeOpenProbability: Float?,
        rightEyeOpenProbability: Float?,
        headEulerY: Float?,
        headEulerZ: Float?
    ): FaceObservation {
        if (!enabled) return FaceObservation()

        val eyesOpen = listOfNotNull(leftEyeOpenProbability, rightEyeOpenProbability).averageOrNull()
        val possibleFatigue = faceDetected && eyesOpen != null && eyesOpen < 0.35f
        val possibleFrustration = faceDetected &&
            smileProbability != null &&
            smileProbability < 0.15f &&
            kotlin.math.abs(headEulerZ ?: 0f) > 12f

        return FaceObservation(
            faceDetected = faceDetected,
            smileProbability = smileProbability,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            headEulerY = headEulerY,
            headEulerZ = headEulerZ,
            attentionLevel = calculateAttention(faceDetected, eyesOpen, headEulerY),
            possibleFatigue = possibleFatigue,
            possibleFrustration = possibleFrustration
        )
    }

    private fun calculateAttention(faceDetected: Boolean, eyesOpen: Float?, headEulerY: Float?): Float {
        if (!faceDetected) return 0f
        val eyeScore = eyesOpen ?: 0.5f
        val headPenalty = ((kotlin.math.abs(headEulerY ?: 0f) / 45f).coerceIn(0f, 1f))
        return (eyeScore * (1f - headPenalty)).coerceIn(0f, 1f)
    }

    private fun List<Float>.averageOrNull(): Float? {
        if (isEmpty()) return null
        return (sum() / size).coerceIn(0f, 1f)
    }
}
