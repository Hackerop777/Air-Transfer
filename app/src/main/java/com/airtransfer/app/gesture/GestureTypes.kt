package com.airtransfer.app.gesture

import com.airtransfer.app.vision.NormalizedLandmark

enum class HandGesture {
    NO_HAND,
    OPEN_PALM,
    FIST,
    UNKNOWN
}

enum class InteractionState {
    IDLE,
    PALM_DETECTING,
    PALM_ARMED,
    GRABBED,
    MOVING,
    RECEIVER_EXPECTING,
    RECEIVER_FIST_DETECTED,
    RECEIVER_CATCHING,
    RECEIVER_READY,
    RELEASING,
    COMPLETED,
    CANCELLED
}

data class HandCentroid(
    val x: Float, // Normalized 0.0 to 1.0 (screen width)
    val y: Float, // Normalized 0.0 to 1.0 (screen height)
    val z: Float = 0f
)

data class GestureClassificationResult(
    val gesture: HandGesture,
    val confidence: Float,
    val openFingersCount: Int,
    val handDetected: Boolean,
    val centroid: HandCentroid? = null,
    val landmarks: List<NormalizedLandmark>? = null
)

sealed class GestureEvent {
    object PalmArmed : GestureEvent()
    data class ScreenGrabbed(val centroid: HandCentroid) : GestureEvent()
    data class HandMoved(val centroid: HandCentroid) : GestureEvent()
    object ReceiverFistArrived : GestureEvent()
    object CatchTriggered : GestureEvent()
    object Aborted : GestureEvent()
    object Released : GestureEvent()
    object Cancelled : GestureEvent()
    object Completed : GestureEvent()
}