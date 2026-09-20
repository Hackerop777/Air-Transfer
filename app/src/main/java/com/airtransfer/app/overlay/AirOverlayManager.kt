package com.airtransfer.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.gesture.HandCentroid
import com.airtransfer.app.gesture.InteractionState

/**
 * Manages the transparent system-wide WindowManager overlay.
 * Non-blocking, non-focusable, and non-touchable so that the user's underlying apps
 * function without any interruption or input stealing.
 */
class AirOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: SpatialOverlayView? = null
    private var isAttached = false

    fun attachOverlay() {
        if (isAttached) return

        if (!Settings.canDrawOverlays(context)) {
            Log.w("AirOverlay", "Cannot attach overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            val view = SpatialOverlayView(context)
            overlayView = view

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager.addView(view, params)
            isAttached = true
            Log.d("AirOverlay", "AirOverlayManager successfully attached to WindowManager")
        } catch (e: Exception) {
            Log.e("AirOverlay", "Failed to attach overlay view", e)
        }
    }

    fun updateState(state: InteractionState, centroid: HandCentroid?) {
        overlayView?.post {
            overlayView?.updateState(state, centroid)
        }
    }

    fun showGrabbed(airObject: AirObject, centroid: HandCentroid) {
        overlayView?.post {
            overlayView?.onGrabTriggered(airObject, centroid)
        }
    }

    fun showReleased() {
        overlayView?.post {
            overlayView?.onReleaseTriggered()
        }
    }

    fun showAbort() {
        overlayView?.post {
            overlayView?.onAbortTriggered()
        }
    }

    fun showArrival(bitmap: Bitmap?) {
        overlayView?.post {
            overlayView?.onScreenshotArrived(bitmap)
        }
    }

    fun clear() {
        overlayView?.post {
            overlayView?.clear()
        }
    }

    fun detachOverlay() {
        if (!isAttached) return
        isAttached = false

        try {
            overlayView?.let {
                it.clear()
                windowManager.removeView(it)
            }
            overlayView = null
            Log.d("AirOverlay", "AirOverlayManager detached cleanly")
        } catch (e: Exception) {
            Log.e("AirOverlay", "Error detaching overlay view", e)
        }
    }
}