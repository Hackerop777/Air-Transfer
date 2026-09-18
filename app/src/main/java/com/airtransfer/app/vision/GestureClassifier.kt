package com.airtransfer.app.vision

import kotlin.math.hypot

object GestureClassifier {

    private fun distance3D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = (a.z - b.z) * 0.8f
        return hypot(hypot(dx, dy), dz)
    }

    fun classify(landmarks: List<NormalizedLandmark>?): ClassificationResult {
        if (landmarks == null || landmarks.size < 21) {
            return ClassificationResult(
                gesture = HandGesture.UNKNOWN,
                confidence = 0f,
                openFingersCount = 0,
                handDetected = false
            )
        }

        val wrist = landmarks[0]
        val palmScale = distance3D(wrist, landmarks[9])
        if (palmScale < 0.05f) {
            return ClassificationResult(
                gesture = HandGesture.UNKNOWN,
                confidence = 0f,
                openFingersCount = 0,
                handDetected = true
            )
        }

        val fingerIndices = listOf(
            Triple(2, 3, 4),    // Thumb: MCP, PIP/IP, TIP
            Triple(5, 6, 8),    // Index: MCP, PIP, TIP
            Triple(9, 10, 12),  // Middle: MCP, PIP, TIP
            Triple(13, 14, 16), // Ring: MCP, PIP, TIP
            Triple(17, 18, 20)  // Pinky: MCP, PIP, TIP
        )

        var openFingersCount = 0
        val extendedStates = mutableListOf<Boolean>()

        // Thumb
        val thumbTipToPinkyMcp = distance3D(landmarks[4], landmarks[17])
        val thumbIpToPinkyMcp = distance3D(landmarks[3], landmarks[17])
        val thumbTipToWrist = distance3D(landmarks[4], wrist)
        val thumbMcpToWrist = distance3D(landmarks[2], wrist)
        val thumbExtended = thumbTipToPinkyMcp > thumbIpToPinkyMcp * 1.15f &&
                thumbTipToWrist > thumbMcpToWrist * 1.2f
        extendedStates.add(thumbExtended)
        if (thumbExtended) openFingersCount++

        // Other 4 fingers
        for (i in 1..4) {
            val (mcpIdx, pipIdx, tipIdx) = fingerIndices[i]
            val tip = landmarks[tipIdx]
            val pip = landmarks[pipIdx]
            val mcp = landmarks[mcpIdx]

            val distTipWrist = distance3D(tip, wrist)
            val distPipWrist = distance3D(pip, wrist)
            val distTipMcp = distance3D(tip, mcp)
            val distPipMcp = distance3D(pip, mcp)

            val isExtended = distTipWrist > distPipWrist * 1.15f && distTipMcp > distPipMcp * 1.1f
            extendedStates.add(isExtended)
            if (isExtended) openFingersCount++
        }

        val fourFingersOpen = extendedStates.subList(1, 5).all { it }
        val fourFingersCurled = extendedStates.subList(1, 5).all { !it }

        var gesture = HandGesture.UNKNOWN
        var confidence = 0f

        if (fourFingersOpen) {
            gesture = HandGesture.OPEN_PALM
            confidence = if (thumbExtended) 0.95f else 0.82f
        } else if (openFingersCount >= 4) {
            gesture = HandGesture.OPEN_PALM
            confidence = 0.78f
        } else if (fourFingersCurled) {
            var curlScore = 0
            for (i in 1..4) {
                val tip = landmarks[fingerIndices[i].third]
                val mcp = landmarks[fingerIndices[i].first]
                if (distance3D(tip, mcp) < palmScale * 0.9f) {
                    curlScore++
                }
            }

            if (curlScore >= 3) {
                gesture = HandGesture.FIST
                confidence = if (thumbExtended) 0.84f else 0.96f
            }
        }

        return ClassificationResult(
            gesture = gesture,
            confidence = confidence,
            openFingersCount = openFingersCount,
            handDetected = true
        )
    }
}
