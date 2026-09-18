package com.airtransfer.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    private val context: Context,
    private val onResult: (List<NormalizedLandmark>?, Long) -> Unit,
    private val onError: (String) -> Unit
) {
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setNumHands(1)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult, _: MPImage ->
                    val landmarks = if (result.landmarks().isNotEmpty()) {
                        result.landmarks()[0].map { landmark ->
                            NormalizedLandmark(landmark.x(), landmark.y(), landmark.z())
                        }
                    } else {
                        null
                    }
                    onResult(landmarks, SystemClock.uptimeMillis())
                }
                .setErrorListener { error ->
                    Log.e("HandLandmarkerHelper", "MediaPipe Error: ${error.message}")
                    onError(error.message ?: "MediaPipe detection error")
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("HandLandmarkerHelper", "MediaPipe HandLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Failed to initialize HandLandmarker", e)
            onError(e.message ?: "MediaPipe initialization failed")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        try {
            if (handLandmarker == null) {
                return
            }

            val frameTime = SystemClock.uptimeMillis()
            val bitmap = imageProxy.toBitmap()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error in detectLiveStream", e)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        try {
            handLandmarker?.close()
            handLandmarker = null
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error closing HandLandmarker", e)
        }
    }
}
