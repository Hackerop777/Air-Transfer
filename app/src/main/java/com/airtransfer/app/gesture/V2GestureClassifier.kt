package com.airtransfer.app.gesture

import com.airtransfer.app.vision.NormalizedLandmark
import kotlin.math.hypot

object V2GestureClassifier {

    private fun distance3D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = (a.z - b.z) * 0.8f
        return hypot(hypot(dx, dy), dz)
    }

    fun classify(landmarks: List<NormalizedLandmark>?): GestureClassificationResult {
        if (landmarks == null || landmarks.size < 21) {
            return GestureClassificationResult(
                gesture = HandGesture.NO_HAND,
                confidence = 0f,
                openFingersCount = 0,
                handDetected = false,
                centroid = null,
                landmarks = null
            )
        }

        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val palmScale = distance3D(wrist, middleMcp)

        // Hand Centroid calculation (average of wrist and 4 finger base knuckles)
        val centroidX = (wrist.x + landmarks[5].x + landmarks[9].x + landmarks[13].x + landmarks[17].x) / 5f
        val centroidY = (wrist.y + landmarks[5].y + landmarks[9].y + landmarks[13].y + landmarks[17].y) / 5f
        val centroidZ = (wrist.z + landmarks[5].z + landmarks[9].z + landmarks[13].z + landmarks[17].z) / 5f
        val centroid = HandCentroid(centroidX, centroidY, centroidZ)

        if (palmScale < 0.04f) {
            return GestureClassificationResult(
                gesture = HandGesture.UNKNOWN,
                confidence = 0f,
                openFingersCount = 0,
                handDetected = true,
                centroid = centroid,
                landmarks = landmarks
            )
        }

        val fingerIndices = listOf(
            Triple(2, 3, 4),    // Thumb: MCP, IP, TIP
            Triple(5, 6, 8),    // Index: MCP, PIP, TIP
            Triple(9, 10, 12),  // Middle: MCP, PIP, TIP
            Triple(13, 14, 16), // Ring: MCP, PIP, TIP
            Triple(17, 18, 20)  // Pinky: MCP, PIP, TIP
        )

        var openFingersCount = 0
        val extendedStates = mutableListOf<Boolean>()

        // 1. Thumb extension evaluation
        val thumbTipToPinkyMcp = distance3D(landmarks[4], landmarks[17])
        val thumbIpToPinkyMcp = distance3D(landmarks[3], landmarks[17])
        val thumbTipToWrist = distance3D(landmarks[4], wrist)
        val thumbMcpToWrist = distance3D(landmarks[2], wrist)
        val thumbExtended = thumbTipToPinkyMcp > thumbIpToPinkyMcp * 1.15f &&
                thumbTipToWrist > thumbMcpToWrist * 1.2f
        extendedStates.add(thumbExtended)
        if (thumbExtended) openFingersCount++

        // 2. Index, Middle, Ring, Pinky extension evaluation
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
        val curledCount = extendedStates.subList(1, 5).count { !it }

        var gesture = HandGesture.UNKNOWN
        var confidence = 0f

        if (fourFingersOpen) {
            gesture = HandGesture.OPEN_PALM
            confidence = if (thumbExtended) 0.96f else 0.88f
        } else if (openFingersCount >= 4) {
            gesture = HandGesture.OPEN_PALM
            confidence = 0.85f
        } else if (curledCount >= 3) {
            var curlScore = 0
            for (i in 1..4) {
                val tip = landmarks[fingerIndices[i].third]
                val mcp = landmarks[fingerIndices[i].first]
                if (distance3D(tip, mcp) < palmScale * 1.05f) {
                    curlScore++
                }
            }

            if (curledCount == 4 && curlScore >= 2) {
                gesture = HandGesture.FIST
                confidence = if (thumbExtended) 0.88f else 0.96f
            } else if (curledCount >= 3 && curlScore >= 2) {
                gesture = HandGesture.FIST
                confidence = 0.84f
            }
        }

        return GestureClassificationResult(
            gesture = gesture,
            confidence = confidence,
            openFingersCount = openFingersCount,
            handDetected = true,
            centroid = centroid,
            landmarks = landmarks
        )
    }
}