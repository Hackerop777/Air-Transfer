package com.airtransfer.app.capture

import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, bounded, zero-leak buffer that maintains only the latest frame captured by MediaProjection.
 * Implements a "latest frame wins" policy with immediate Image closing and safe Bitmap lifecycle management.
 */
class LatestFrameBuffer {

    private val latestBitmapRef = AtomicReference<Bitmap?>(null)
    private val frameLock = Any()

    /**
     * Extracts the pixel buffer from the MediaProjection Image, handling rowStride and padding.
     * Guaranteed to close the Image even if bitmap extraction fails.
     */
    fun onNewImage(image: Image) {
        try {
            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            // Calculate row padding
            val rowPadding = rowStride - pixelStride * width

            // Create temporary bitmap accommodating the padded rowStride
            val paddedBitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            paddedBitmap.copyPixelsFromBuffer(buffer)

            // Crop out padding to get the exact screen resolution
            val finalBitmap = if (rowPadding == 0) {
                paddedBitmap
            } else {
                val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                paddedBitmap.recycle()
                cropped
            }

            synchronized(frameLock) {
                val old = latestBitmapRef.getAndSet(finalBitmap)
                if (old != null && !old.isRecycled) {
                    old.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("LatestFrameBuffer", "Error capturing screen frame", e)
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Returns a thread-safe copy of the latest valid screen frame.
     */
    fun getLatestFrameCopy(): Bitmap? {
        synchronized(frameLock) {
            val current = latestBitmapRef.get() ?: return null
            if (current.isRecycled) return null
            return try {
                current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                Log.e("LatestFrameBuffer", "Failed to copy latest frame", e)
                null
            }
        }
    }

    fun clear() {
        synchronized(frameLock) {
            val old = latestBitmapRef.getAndSet(null)
            if (old != null && !old.isRecycled) {
                old.recycle()
            }
        }
    }
}