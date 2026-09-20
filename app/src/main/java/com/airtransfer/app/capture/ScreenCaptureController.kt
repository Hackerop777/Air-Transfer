package com.airtransfer.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.airtransfer.app.airobject.AirObject

/**
 * Manages the Android MediaProjection session, VirtualDisplay, and ImageReader.
 * Continuously streams the latest usable screen frame into [LatestFrameBuffer]
 * so that when GRAB occurs, the screen is captured instantaneously without waiting for a new projection session.
 */
class ScreenCaptureController(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val onStoppedCallback: () -> Unit = {}
) {
    private val frameBuffer = LatestFrameBuffer()
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isRunning = false

    init {
        setupCapturePipeline()
    }

    private fun setupCapturePipeline() {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val densityDpi = metrics.densityDpi

            // Background handler thread for non-blocking frame ingestion
            val thread = HandlerThread("ScreenCaptureThread").apply { start() }
            backgroundThread = thread
            val handler = Handler(thread.looper)
            backgroundHandler = handler

            // Bounded ImageReader: maxImages = 2
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                val image = try {
                    ir.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }
                if (image != null) {
                    frameBuffer.onNewImage(image)
                }
            }, handler)

            // Register MediaProjection callback
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i("ScreenCapture", "MediaProjection was stopped by the system or user")
                    stop()
                    onStoppedCallback()
                }
            }, handler)

            // Create VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "AirTransferCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            isRunning = true
            Log.d("ScreenCapture", "ScreenCaptureController initialized successfully ($width x $height @ $densityDpi dpi)")
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to setup screen capture pipeline", e)
            stop()
        }
    }

    /**
     * Instantly grabs the latest screen frame from memory and converts it into a compressed [AirObject].
     */
    fun grabScreenshot(sourceDeviceId: String, sessionId: String): AirObject? {
        val frameBitmap = frameBuffer.getLatestFrameCopy() ?: run {
            Log.w("ScreenCapture", "grabScreenshot called but no frame was available in buffer")
            return null
        }

        return try {
            AirObject.fromScreenshotBitmap(
                bitmap = frameBitmap,
                sourceDeviceId = sourceDeviceId,
                sessionId = sessionId
            )
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to create AirObject from screenshot", e)
            null
        } finally {
            if (!frameBitmap.isRecycled) {
                frameBitmap.recycle()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection.stop()
            frameBuffer.clear()

            backgroundThread?.quitSafely()
            backgroundThread = null
            backgroundHandler = null

            Log.d("ScreenCapture", "ScreenCaptureController stopped and resources released")
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Error stopping ScreenCaptureController", e)
        }
    }
}