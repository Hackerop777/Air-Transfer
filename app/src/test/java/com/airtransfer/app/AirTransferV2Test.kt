package com.airtransfer.app

import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.airobject.AirObjectType
import com.airtransfer.app.gesture.*
import com.airtransfer.app.nearby.NearbyProtocol
import com.airtransfer.app.vision.NormalizedLandmark
import org.junit.Assert.*
import org.junit.Test

class AirTransferV2Test {

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
    fun testV2GestureClassification() {
        val palmResult = V2GestureClassifier.classify(createPalmLandmarks())
        assertEquals(HandGesture.OPEN_PALM, palmResult.gesture)
        assertTrue(palmResult.confidence >= 0.8f)
        assertNotNull(palmResult.centroid)

        val fistResult = V2GestureClassifier.classify(createFistLandmarks())
        assertEquals(HandGesture.FIST, fistResult.gesture)
        assertTrue(fistResult.confidence >= 0.8f)
        assertNotNull(fistResult.centroid)
    }

    @Test
    fun testTemporalGestureStateMachine_PalmToFistToAbortOnSameDevice() {
        val sm = TemporalGestureStateMachine(
            palmHoldThresholdMs = 180L,
            sequenceTimeoutMs = 3000L,
            cooldownMs = 1000L
        )

        var eventReceived: GestureEvent? = null
        sm.setEventListener { eventReceived = it }

        var t = 1000L

        // 1. Initial Palm
        val palm = GestureClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true, HandCentroid(0.5f, 0.5f))
        var state = sm.process(palm, t)
        assertEquals(InteractionState.PALM_DETECTING, state)

        // 2. Palm held for 180ms -> PALM_ARMED
        t += 200L
        state = sm.process(palm, t)
        assertEquals(InteractionState.PALM_ARMED, state)
        assertTrue(eventReceived is GestureEvent.PalmArmed)

        // 3. Fist closed -> GRABBED
        t += 100L
        val fist = GestureClassificationResult(HandGesture.FIST, 0.9f, 0, true, HandCentroid(0.52f, 0.48f))
        state = sm.process(fist, t)
        assertEquals(InteractionState.GRABBED, state)
        assertTrue(eventReceived is GestureEvent.ScreenGrabbed)

        // 4. Open Palm back in front of SAME device -> Aborted (clean cancel, no crash!)
        t += 200L
        val releasePalm = GestureClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true, HandCentroid(0.52f, 0.48f))
        state = sm.process(releasePalm, t)
        assertEquals(InteractionState.CANCELLED, state)
        assertTrue(eventReceived is GestureEvent.Aborted)
    }

    @Test
    fun testTemporalGestureStateMachine_ReceiverFistToPalmCatch() {
        val sm = TemporalGestureStateMachine(
            sequenceTimeoutMs = 3000L,
            cooldownMs = 1000L
        )

        var eventReceived: GestureEvent? = null
        sm.setEventListener { eventReceived = it }

        // Peer grabs an object; receiver enters expecting catch mode
        sm.setReceiverExpecting(true)
        assertEquals(InteractionState.RECEIVER_EXPECTING, sm.state)

        var t = 1000L

        // User brings closed fist to receiver
        val fist = GestureClassificationResult(HandGesture.FIST, 0.9f, 0, true, HandCentroid(0.5f, 0.5f))
        var state = sm.process(fist, t)
        assertEquals(InteractionState.RECEIVER_FIST_DETECTED, state)
        assertTrue(eventReceived is GestureEvent.ReceiverFistArrived)

        // User opens palm in front of receiver -> Catch triggered!
        t += 200L
        val palm = GestureClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true, HandCentroid(0.5f, 0.5f))
        state = sm.process(palm, t)
        assertEquals(InteractionState.COMPLETED, state)
        assertTrue(eventReceived is GestureEvent.Completed)
    }

    @Test
    fun testTemporalGestureStateMachine_Timeout() {
        val sm = TemporalGestureStateMachine(palmHoldThresholdMs = 100L, sequenceTimeoutMs = 1000L)
        var eventReceived: GestureEvent? = null
        sm.setEventListener { eventReceived = it }

        var t = 1000L
        val palm = GestureClassificationResult(HandGesture.OPEN_PALM, 0.9f, 5, true, HandCentroid(0.5f, 0.5f))
        sm.process(palm, t)
        t += 150L
        sm.process(palm, t)
        assertEquals(InteractionState.PALM_ARMED, sm.state)

        // Idle beyond timeout
        t += 1200L
        val idleResult = GestureClassificationResult(HandGesture.UNKNOWN, 0f, 0, false, null)
        val state = sm.process(idleResult, t)
        assertEquals(InteractionState.CANCELLED, state)
        assertTrue(eventReceived is GestureEvent.Cancelled)
    }

    @Test
    fun testNearbyProtocolSerialization() {
        val msg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_DEVICE_READY,
            senderId = "test-device-1",
            senderName = "Pixel_8_Pro",
            payload = "{\"width\":1080,\"height\":2400}"
        )

        val bytes = NearbyProtocol.encode(msg)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val decoded = NearbyProtocol.decode(bytes)
        assertNotNull(decoded)
        assertEquals(msg.type, decoded?.type)
        assertEquals(msg.senderId, decoded?.senderId)
        assertEquals(msg.senderName, decoded?.senderName)
        assertEquals(msg.payload, decoded?.payload)
    }

    @Test
    fun testAirObjectChecksumAndMetadata() {
        val sampleBytes = "test_screenshot_image_data_stream".toByteArray()
        val airObject = AirObject(
            type = AirObjectType.SCREENSHOT,
            encodedBytes = sampleBytes,
            thumbnail = null,
            width = 1080,
            height = 2400,
            sourceDeviceId = "device_A",
            sessionId = "session_123"
        )

        val meta = airObject.toMetadata()
        assertEquals("SCREENSHOT", meta.type)
        assertEquals(1080, meta.width)
        assertEquals(2400, meta.height)
        assertEquals(sampleBytes.size.toLong(), meta.encodedSize)
        assertEquals("device_A", meta.sourceDeviceId)
        assertEquals("session_123", meta.sessionId)
        assertNotNull(meta.checksum)
        assertEquals(64, meta.checksum.length) // SHA-256 hex string is 64 characters

        // Verify checksum stability
        val checksum2 = AirObject.computeChecksum(sampleBytes)
        assertEquals(meta.checksum, checksum2)
    }
}