package com.airtransfer.app

import com.airtransfer.app.vision.*
import org.junit.Assert.*
import org.junit.Test

class GestureEngineTest {

    private fun createPalmLandmarks(): List<NormalizedLandmark> {
        val lms = mutableListOf<NormalizedLandmark>()
        // Wrist
        lms.add(NormalizedLandmark(0.5f, 0.8f, 0f))
        // Thumb (extended out)
        lms.add(NormalizedLandmark(0.45f, 0.72f, 0f))
        lms.add(NormalizedLandmark(0.40f, 0.65f, 0f))
        lms.add(NormalizedLandmark(0.35f, 0.58f, 0f))
        lms.add(NormalizedLandmark(0.30f, 0.50f, 0f)) // Tip
        // Index (extended up)
        lms.add(NormalizedLandmark(0.45f, 0.55f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.45f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.35f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.25f, 0f)) // Tip
        // Middle (extended up)
        lms.add(NormalizedLandmark(0.50f, 0.52f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.42f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.30f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.20f, 0f)) // Tip
        // Ring (extended up)
        lms.add(NormalizedLandmark(0.55f, 0.55f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.45f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.36f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.26f, 0f)) // Tip
        // Pinky (extended up)
        lms.add(NormalizedLandmark(0.60f, 0.60f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.52f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.44f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.36f, 0f)) // Tip
        return lms
    }

    private fun createFistLandmarks(): List<NormalizedLandmark> {
        val lms = mutableListOf<NormalizedLandmark>()
        // Wrist
        lms.add(NormalizedLandmark(0.5f, 0.8f, 0f))
        // Thumb folded
        lms.add(NormalizedLandmark(0.46f, 0.75f, 0f))
        lms.add(NormalizedLandmark(0.44f, 0.70f, 0f))
        lms.add(NormalizedLandmark(0.46f, 0.67f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.68f, 0f))
        // Index curled
        lms.add(NormalizedLandmark(0.45f, 0.60f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.54f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.58f, 0f))
        lms.add(NormalizedLandmark(0.45f, 0.62f, 0f))
        // Middle curled
        lms.add(NormalizedLandmark(0.50f, 0.58f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.52f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.56f, 0f))
        lms.add(NormalizedLandmark(0.50f, 0.61f, 0f))
        // Ring curled
        lms.add(NormalizedLandmark(0.55f, 0.60f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.54f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.58f, 0f))
        lms.add(NormalizedLandmark(0.55f, 0.63f, 0f))
        // Pinky curled
        lms.add(NormalizedLandmark(0.60f, 0.64f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.58f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.62f, 0f))
        lms.add(NormalizedLandmark(0.60f, 0.66f, 0f))
        return lms
    }

    @Test
    fun testOpenPalmClassification() {
        val result = GestureClassifier.classify(createPalmLandmarks())
        assertEquals(HandGesture.OPEN_PALM, result.gesture)
        assertTrue("Confidence should be high", result.confidence >= 0.8f)
    }

    @Test
    fun testFistClassification() {
        val result = GestureClassifier.classify(createFistLandmarks())
        assertEquals(HandGesture.FIST, result.gesture)
        assertTrue("Confidence should be high", result.confidence >= 0.8f)
    }

    @Test
    fun testSenderGrabSequence() {
        val sm = GestureStateMachine(GestureRole.SENDER)
        var actionTriggered: String? = null
        sm.setOnActionListener { actionTriggered = it }

        var t = 1000L
        // Frame 1: Palm starts
        var s = sm.process(ClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true), t)
        assertEquals(GestureStep.STEP_1_HOLDING, s.currentStep)

        // Frame 2: Palm held for 500ms -> confirmed
        t += 500L
        s = sm.process(ClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true), t)
        assertEquals(GestureStep.STEP_1_CONFIRMED, s.currentStep)

        // Frame 3: Fist starts
        t += 100L
        s = sm.process(ClassificationResult(HandGesture.FIST, 0.9f, 0, true), t)
        assertEquals(GestureStep.STEP_2_HOLDING, s.currentStep)

        // Frame 4: Fist held for 500ms -> GRAB action triggered!
        t += 500L
        s = sm.process(ClassificationResult(HandGesture.FIST, 0.9f, 0, true), t)
        assertEquals(GestureStep.ACTION_TRIGGERED, s.currentStep)
        assertEquals("GRAB", actionTriggered)
    }
}
