package com.airtransfer.app.vision

class GestureStateMachine(
    private var role: GestureRole,
    private var config: GestureConfig = GestureConfig()
) {
    private var currentStep: GestureStep = GestureStep.IDLE
    private var holdStartTime: Long? = null
    private var step1ConfirmedTime: Long? = null
    private var lastActionTime: Long? = null
    private var lastAction: String? = null
    private var onActionCallback: ((String) -> Unit)? = null

    fun setRole(newRole: GestureRole) {
        if (role != newRole) {
            role = newRole
            reset()
        }
    }

    fun setOnActionListener(callback: (String) -> Unit) {
        onActionCallback = callback
    }

    fun reset() {
        currentStep = GestureStep.IDLE
        holdStartTime = null
        step1ConfirmedTime = null
    }

    fun process(
        result: ClassificationResult,
        currentTime: Long = System.currentTimeMillis()
    ): GestureState {
        val (gesture, confidence, _, handDetected) = result
        val isConfident = confidence >= config.confidenceThreshold

        // Cooldown check
        lastActionTime?.let { lastTime ->
            if (currentTime - lastTime < config.gestureCooldownMs) {
                val remainingSec = ((config.gestureCooldownMs - (currentTime - lastTime)) / 1000L).coerceAtLeast(1)
                return GestureState(
                    currentStep = GestureStep.ACTION_TRIGGERED,
                    detectedGesture = gesture,
                    confidence = confidence,
                    holdProgress = 0f,
                    expectedGesture = HandGesture.UNKNOWN,
                    statusMessage = "Ready in ${remainingSec}s...",
                    actionTriggered = false,
                    lastAction = lastAction
                )
            }
        }

        val step1Target = if (role == GestureRole.SENDER) HandGesture.OPEN_PALM else HandGesture.FIST
        val step2Target = if (role == GestureRole.SENDER) HandGesture.FIST else HandGesture.OPEN_PALM
        val actionName = if (role == GestureRole.SENDER) "GRAB" else "RELEASE"

        // Sequence timeout check
        if (currentStep == GestureStep.STEP_1_CONFIRMED || currentStep == GestureStep.STEP_2_HOLDING) {
            step1ConfirmedTime?.let { step1Time ->
                if (currentTime - step1Time > config.sequenceTimeoutMs) {
                    reset()
                    return GestureState(
                        currentStep = GestureStep.IDLE,
                        detectedGesture = gesture,
                        confidence = confidence,
                        holdProgress = 0f,
                        expectedGesture = step1Target,
                        statusMessage = "Sequence timed out. Try again.",
                        actionTriggered = false,
                        lastAction = lastAction
                    )
                }
            }
        }

        if (!handDetected) {
            if (currentStep == GestureStep.STEP_1_HOLDING) {
                currentStep = GestureStep.IDLE
                holdStartTime = null
            }
            return GestureState(
                currentStep = currentStep,
                detectedGesture = HandGesture.UNKNOWN,
                confidence = 0f,
                holdProgress = 0f,
                expectedGesture = if (currentStep == GestureStep.STEP_1_CONFIRMED) step2Target else step1Target,
                statusMessage = "Place hand in camera view",
                actionTriggered = false,
                lastAction = lastAction
            )
        }

        var holdProgress = 0f
        var statusMessage = ""
        var actionTriggered = false

        when (currentStep) {
            GestureStep.IDLE, GestureStep.STEP_1_HOLDING -> {
                if (gesture == step1Target && isConfident) {
                    if (holdStartTime == null) {
                        holdStartTime = currentTime
                        currentStep = GestureStep.STEP_1_HOLDING
                    }
                    val elapsed = currentTime - (holdStartTime ?: currentTime)
                    holdProgress = (elapsed.toFloat() / config.gestureHoldTimeMs).coerceIn(0f, 1f)

                    if (elapsed >= config.gestureHoldTimeMs) {
                        currentStep = GestureStep.STEP_1_CONFIRMED
                        step1ConfirmedTime = currentTime
                        holdStartTime = null
                        holdProgress = 0f
                        statusMessage = if (role == GestureRole.SENDER)
                            "✋ Palm confirmed! Close fist ✊ to grab"
                        else
                            "✊ Fist confirmed! Open palm 🖐 to release"
                    } else {
                        statusMessage = if (role == GestureRole.SENDER)
                            "Hold open palm steady..."
                        else
                            "Hold closed fist steady..."
                    }
                } else {
                    currentStep = GestureStep.IDLE
                    holdStartTime = null
                    statusMessage = if (role == GestureRole.SENDER)
                        "Show an open palm ✋ to begin"
                    else
                        "Show a closed fist ✊ to begin"
                }
            }

            GestureStep.STEP_1_CONFIRMED, GestureStep.STEP_2_HOLDING -> {
                if (gesture == step2Target && isConfident) {
                    if (holdStartTime == null) {
                        holdStartTime = currentTime
                        currentStep = GestureStep.STEP_2_HOLDING
                    }
                    val elapsed = currentTime - (holdStartTime ?: currentTime)
                    holdProgress = (elapsed.toFloat() / config.gestureHoldTimeMs).coerceIn(0f, 1f)

                    if (elapsed >= config.gestureHoldTimeMs) {
                        currentStep = GestureStep.ACTION_TRIGGERED
                        lastActionTime = currentTime
                        lastAction = actionName
                        actionTriggered = true
                        holdStartTime = null
                        holdProgress = 1f
                        statusMessage = if (role == GestureRole.SENDER)
                            "Files Grabbed!"
                        else
                            "Files Released & Accepted!"

                        onActionCallback?.invoke(actionName)
                    } else {
                        statusMessage = if (role == GestureRole.SENDER)
                            "Holding fist to grab..."
                        else
                            "Holding open palm to accept..."
                    }
                } else {
                    holdStartTime = null
                    val elapsedSinceStep1 = currentTime - (step1ConfirmedTime ?: currentTime)
                    val remainingMs = (config.sequenceTimeoutMs - elapsedSinceStep1).coerceAtLeast(0L)
                    val remainingSec = (remainingMs / 1000L).coerceAtLeast(1)
                    statusMessage = if (role == GestureRole.SENDER)
                        "Now close fist ✊ (${remainingSec}s left)"
                    else
                        "Now open palm 🖐 (${remainingSec}s left)"
                }
            }

            GestureStep.ACTION_TRIGGERED -> {
                statusMessage = "$actionName completed"
            }
        }

        return GestureState(
            currentStep = currentStep,
            detectedGesture = gesture,
            confidence = confidence,
            holdProgress = holdProgress,
            expectedGesture = if (currentStep == GestureStep.STEP_1_CONFIRMED || currentStep == GestureStep.STEP_2_HOLDING) step2Target else step1Target,
            statusMessage = statusMessage,
            actionTriggered = actionTriggered,
            lastAction = lastAction
        )
    }
}
