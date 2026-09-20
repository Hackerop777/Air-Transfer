package com.airtransfer.app.gesture

class TemporalGestureStateMachine(
    private val palmHoldThresholdMs: Long = 180L,
    private val sequenceTimeoutMs: Long = 4000L,
    private val grabLostTimeoutMs: Long = 2500L,
    private val cooldownMs: Long = 1200L
) {
    var state: InteractionState = InteractionState.IDLE
        private set

    private var palmDetectStartTime: Long? = null
    private var armedTime: Long? = null
    private var grabTime: Long? = null
    private var lastValidHandTime: Long? = null
    private var lastCompletedTime: Long? = null

    private var smoothedCentroid: HandCentroid? = null
    private val smoothingAlpha = 0.65f // Low-pass filter for smooth hand tracking

    private var eventListener: ((GestureEvent) -> Unit)? = null

    fun setEventListener(listener: (GestureEvent) -> Unit) {
        eventListener = listener
    }

    fun reset() {
        state = InteractionState.IDLE
        palmDetectStartTime = null
        armedTime = null
        grabTime = null
        lastValidHandTime = null
        smoothedCentroid = null
    }

    private val grabHoldingTimeoutMs: Long = 45000L // 45 seconds to carry to another device
    private var fistDetectStartTime: Long? = null

    fun setReceiverExpecting(expecting: Boolean) {
        if (expecting) {
            if (state == InteractionState.IDLE) {
                state = InteractionState.RECEIVER_EXPECTING
            }
        } else {
            if (state == InteractionState.RECEIVER_EXPECTING || state == InteractionState.RECEIVER_FIST_DETECTED) {
                reset()
            }
        }
    }

    fun setReceiverReady() {
        if (state == InteractionState.MOVING || state == InteractionState.GRABBED) {
            state = InteractionState.RECEIVER_READY
        }
    }

    fun process(
        result: GestureClassificationResult,
        currentTime: Long = System.currentTimeMillis()
    ): InteractionState {
        // 1. Cooldown check after successful completion
        lastCompletedTime?.let {
            if (currentTime - it < cooldownMs) {
                return state
            } else {
                lastCompletedTime = null
                if (state == InteractionState.COMPLETED || state == InteractionState.CANCELLED) {
                    reset()
                }
            }
        }

        val gesture = result.gesture
        val isConfident = result.confidence >= 0.70f
        val centroid = result.centroid

        if (result.handDetected && centroid != null) {
            lastValidHandTime = currentTime
            val currentSmoothed = smoothedCentroid
            smoothedCentroid = if (currentSmoothed == null) {
                centroid
            } else {
                HandCentroid(
                    x = currentSmoothed.x * smoothingAlpha + centroid.x * (1f - smoothingAlpha),
                    y = currentSmoothed.y * smoothingAlpha + centroid.y * (1f - smoothingAlpha),
                    z = currentSmoothed.z * smoothingAlpha + centroid.z * (1f - smoothingAlpha)
                )
            }
        }

        when (state) {
            InteractionState.IDLE -> {
                // Check FIST first: if user approaches with fist, never enter grab mode
                if (gesture == HandGesture.FIST && (isConfident || result.confidence >= 0.70f)) {
                    state = InteractionState.RECEIVER_FIST_DETECTED
                    fistDetectStartTime = currentTime
                    eventListener?.invoke(GestureEvent.ReceiverFistArrived)
                } else if (gesture == HandGesture.OPEN_PALM && isConfident) {
                    state = InteractionState.PALM_DETECTING
                    palmDetectStartTime = currentTime
                }
            }

            InteractionState.PALM_DETECTING -> {
                if (gesture == HandGesture.FIST && (isConfident || result.confidence >= 0.70f)) {
                    // Hand was arriving as fist, brief transient palm detection corrected
                    state = InteractionState.RECEIVER_FIST_DETECTED
                    fistDetectStartTime = currentTime
                    eventListener?.invoke(GestureEvent.ReceiverFistArrived)
                } else if (gesture == HandGesture.OPEN_PALM && isConfident) {
                    val elapsed = currentTime - (palmDetectStartTime ?: currentTime)
                    if (elapsed >= palmHoldThresholdMs) {
                        state = InteractionState.PALM_ARMED
                        armedTime = currentTime
                        eventListener?.invoke(GestureEvent.PalmArmed)
                    }
                } else {
                    reset()
                }
            }

            InteractionState.PALM_ARMED -> {
                val elapsedSinceArmed = currentTime - (armedTime ?: currentTime)
                if (elapsedSinceArmed > sequenceTimeoutMs) {
                    // Timeout waiting for grab
                    state = InteractionState.CANCELLED
                    lastCompletedTime = currentTime
                    eventListener?.invoke(GestureEvent.Cancelled)
                } else if (gesture == HandGesture.FIST && (isConfident || result.confidence >= 0.70f)) {
                    // Grab transition!
                    state = InteractionState.GRABBED
                    grabTime = currentTime
                    val grabCentroid = smoothedCentroid ?: HandCentroid(0.5f, 0.5f)
                    eventListener?.invoke(GestureEvent.ScreenGrabbed(grabCentroid))
                }
            }

            InteractionState.GRABBED, InteractionState.MOVING, InteractionState.RECEIVER_READY -> {
                // If user opens hand back in front of the SAME sender device -> abort/cancel grab cleanly
                if (gesture == HandGesture.OPEN_PALM && isConfident) {
                    state = InteractionState.CANCELLED
                    lastCompletedTime = currentTime
                    eventListener?.invoke(GestureEvent.Aborted)
                } else {
                    // Check holding timeout (generous 45s window to carry to another device)
                    val elapsedSinceGrab = currentTime - (grabTime ?: currentTime)
                    if (elapsedSinceGrab > grabHoldingTimeoutMs) {
                        state = InteractionState.CANCELLED
                        lastCompletedTime = currentTime
                        eventListener?.invoke(GestureEvent.Cancelled)
                    }
                }
            }

            InteractionState.RECEIVER_EXPECTING -> {
                // In RECEIVER_EXPECTING mode: ONLY accept approaching fist.
                // Disallow OPEN_PALM from ever triggering a grab on this receiving device!
                if (gesture == HandGesture.FIST && (isConfident || result.confidence >= 0.65f)) {
                    state = InteractionState.RECEIVER_FIST_DETECTED
                    fistDetectStartTime = currentTime
                    eventListener?.invoke(GestureEvent.ReceiverFistArrived)
                }
            }

            InteractionState.RECEIVER_FIST_DETECTED -> {
                // User brought fist to receiver; now opens hand to release/catch
                val isReleaseGesture = (gesture == HandGesture.OPEN_PALM && (isConfident || result.confidence >= 0.65f)) ||
                        result.openFingersCount >= 3
                if (isReleaseGesture) {
                    state = InteractionState.RECEIVER_CATCHING
                    eventListener?.invoke(GestureEvent.CatchTriggered)
                    state = InteractionState.COMPLETED
                    lastCompletedTime = currentTime
                    eventListener?.invoke(GestureEvent.Completed)
                } else {
                    val elapsedSinceFist = currentTime - (fistDetectStartTime ?: currentTime)
                    if (elapsedSinceFist > sequenceTimeoutMs) {
                        state = InteractionState.CANCELLED
                        lastCompletedTime = currentTime
                        eventListener?.invoke(GestureEvent.Cancelled)
                    }
                }
            }

            InteractionState.RECEIVER_CATCHING, InteractionState.RELEASING -> {
                state = InteractionState.COMPLETED
                lastCompletedTime = currentTime
                eventListener?.invoke(GestureEvent.Completed)
            }

            InteractionState.COMPLETED, InteractionState.CANCELLED -> {
                // Wait for cooldown
            }
        }

        return state
    }
}