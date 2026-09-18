package com.airtransfer.app.vision

enum class HandGesture {
    OPEN_PALM,
    FIST,
    UNKNOWN
}

enum class GestureRole {
    SENDER,
    RECEIVER
}

enum class GestureStep {
    IDLE,
    STEP_1_HOLDING,
    STEP_1_CONFIRMED,
    STEP_2_HOLDING,
    ACTION_TRIGGERED
}

data class NormalizedLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

data class GestureConfig(
    val gestureHoldTimeMs: Long = 500L,
    val sequenceTimeoutMs: Long = 2500L,
    val gestureCooldownMs: Long = 1500L,
    val confidenceThreshold: Float = 0.75f
)

data class ClassificationResult(
    val gesture: HandGesture,
    val confidence: Float,
    val openFingersCount: Int,
    val handDetected: Boolean
)

data class GestureState(
    val currentStep: GestureStep = GestureStep.IDLE,
    val detectedGesture: HandGesture = HandGesture.UNKNOWN,
    val confidence: Float = 0f,
    val holdProgress: Float = 0f,
    val expectedGesture: HandGesture = HandGesture.OPEN_PALM,
    val statusMessage: String = "",
    val actionTriggered: Boolean = false,
    val lastAction: String? = null
)
