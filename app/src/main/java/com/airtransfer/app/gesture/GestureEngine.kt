package com.airtransfer.app.gesture

import android.content.Context
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.airtransfer.app.vision.HandLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background gesture perception engine.
 * Orchestrates CameraX (no UI preview), Stage 1 frame gating,
 * MediaPipe 3D Hand Landmarker, and the TemporalGestureStateMachine.
 */
class GestureEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onGestureEvent: (GestureEvent) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    val stateMachine = TemporalGestureStateMachine().apply {
        setEventListener(onGestureEvent)
    }

    private val _interactionState = MutableStateFlow(InteractionState.IDLE)
    val interactionState: StateFlow<InteractionState> = _interactionState.asStateFlow()

    private val _latestCentroid = MutableStateFlow<HandCentroid?>(null)
    val latestCentroid: StateFlow<HandCentroid?> = _latestCentroid.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var frameCounter = 0

    fun setReceiverExpecting(expecting: Boolean) {
        stateMachine.setReceiverExpecting(expecting)
        _interactionState.value = stateMachine.state
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        Log.d("GestureEngine", "Starting GestureEngine background perception...")
        setupMediaPipe()
        setupCamera()
    }

    private fun setupMediaPipe() {
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            onResult = { landmarks, _ ->
                val classification = V2GestureClassifier.classify(landmarks)
                val newState = stateMachine.process(classification)
                _interactionState.value = newState
                _latestCentroid.value = classification.centroid
            },
            onError = { errorMsg ->
                Log.e("GestureEngine", "MediaPipe error: $errorMsg")
            }
        )
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isRunning.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    frameCounter++
                    val currentState = stateMachine.state

                    // Stage 1 Gating: Throttle inference when idle to save battery
                    if (currentState == InteractionState.IDLE && frameCounter % 3 != 0) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    handLandmarkerHelper?.detectLiveStream(
                        imageProxy = imageProxy,
                        isFrontCamera = true
                    )
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
                Log.d("GestureEngine", "CameraX ImageAnalysis successfully bound to foreground service lifecycle")
            } catch (e: Exception) {
                Log.e("GestureEngine", "Failed to bind CameraX ImageAnalysis", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            cameraExecutor.shutdown()

            handLandmarkerHelper?.close()
            handLandmarkerHelper = null

            stateMachine.reset()
            _interactionState.value = InteractionState.IDLE
            _latestCentroid.value = null

            Log.d("GestureEngine", "GestureEngine stopped")
        } catch (e: Exception) {
            Log.e("GestureEngine", "Error stopping GestureEngine", e)
        }
    }
}