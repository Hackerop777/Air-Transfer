package com.airtransfer.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.gesture.HandCentroid
import com.airtransfer.app.gesture.InteractionState

/**
 * Custom ultra-lightweight overlay view for system-wide spatial visualization.
 * Completely non-blocking and optimized for high-framerate spring animations.
 */
@SuppressLint("ViewConstructor")
class SpatialOverlayView(context: Context) : View(context) {

    private var currentState: InteractionState = InteractionState.IDLE
    private var activeAirObject: AirObject? = null
    private var receiverBitmap: Bitmap? = null

    // Hand tracking coordinates (interpolated for smooth motion)
    private var currentCardX = 0f
    private var currentCardY = 0f
    private var targetCardX = 0f
    private var targetCardY = 0f
    private var cardScale = 0f

    // Paints
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(220, 56, 189, 248) // Cyan glow
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 15, 17, 26)
    }
    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(80, 255, 255, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var cardAnimator: ValueAnimator? = null
    private var arrivalAnimator: ValueAnimator? = null
    private var arrivalProgress = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for BlurMaskFilter
    }

    fun updateState(state: InteractionState, centroid: HandCentroid?) {
        currentState = state
        invalidate()
    }

    fun onGrabTriggered(airObject: AirObject, centroid: HandCentroid) {
        activeAirObject = airObject
        val screenW = width.toFloat().coerceAtLeast(1f)
        currentCardX = screenW / 2f
        currentCardY = 320f
        targetCardX = currentCardX
        targetCardY = currentCardY

        // Shrink screen into docked preview card
        cardAnimator?.cancel()
        cardAnimator = ValueAnimator.ofFloat(0.1f, 1f).apply {
            duration = 340L
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                cardScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun onReleaseTriggered() {
        // Upward swoosh fly-off animation on transfer confirmation
        cardAnimator?.cancel()
        cardAnimator = ValueAnimator.ofFloat(cardScale, 0f).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                cardScale = it.animatedValue as Float
                currentCardY -= 20f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeAirObject = null
                    invalidate()
                }
            })
            start()
        }
    }

    fun onAbortTriggered() {
        // Gentle fade-out when aborted on same device
        cardAnimator?.cancel()
        cardAnimator = ValueAnimator.ofFloat(cardScale, 0f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                cardScale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeAirObject = null
                    currentState = InteractionState.IDLE
                    invalidate()
                }
            })
            start()
        }
    }

    fun onScreenshotArrived(bitmap: Bitmap?) {
        receiverBitmap = bitmap
        arrivalAnimator?.cancel()
        arrivalAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480L
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                arrivalProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Auto-dismiss arrival after 3.5 seconds
        postDelayed({
            arrivalAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 380L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    arrivalProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        receiverBitmap = null
                        invalidate()
                    }
                })
                start()
            }
        }, 3500L)
    }

    fun clear() {
        cardAnimator?.cancel()
        arrivalAnimator?.cancel()
        activeAirObject = null
        receiverBitmap = null
        cardScale = 0f
        arrivalProgress = 0f
        currentState = InteractionState.IDLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val screenW = width.toFloat()
        val screenH = height.toFloat()

        // 1. Draw Top Status Pill if active
        if (currentState != InteractionState.IDLE && currentState != InteractionState.COMPLETED) {
            drawTopStatusPill(canvas, screenW)
        }

        // 2. Draw Floating AirObject Card (Sender)
        val airObj = activeAirObject
        val thumb = airObj?.thumbnail
        if (thumb != null && !thumb.isRecycled && cardScale > 0.01f) {
            drawFloatingCard(canvas, thumb)
        }

        // 3. Draw Incoming Screenshot Arrival (Receiver)
        val arrivalBmp = receiverBitmap
        if (arrivalBmp != null && !arrivalBmp.isRecycled && arrivalProgress > 0.01f) {
            drawArrivalCard(canvas, arrivalBmp, screenW, screenH)
        }
    }

    private fun drawTopStatusPill(canvas: Canvas, screenW: Float) {
        val pillW = 540f
        val pillH = 82f
        val pillLeft = (screenW - pillW) / 2f
        val pillTop = 80f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)

        canvas.drawRoundRect(pillRect, 41f, 41f, pillBgPaint)
        canvas.drawRoundRect(pillRect, 41f, 41f, pillBorderPaint)

        val (icon, label) = when (currentState) {
            InteractionState.PALM_DETECTING -> "✋" to "Palm Detected"
            InteractionState.PALM_ARMED -> "✊" to "Close Fist to Grab"
            InteractionState.GRABBED, InteractionState.MOVING -> "✊" to "Holding Screen"
            InteractionState.RECEIVER_EXPECTING -> "📥" to "Incoming Screen: Show Fist"
            InteractionState.RECEIVER_FIST_DETECTED -> "🖐️" to "Open Hand to Catch"
            InteractionState.RECEIVER_CATCHING -> "✨" to "Screen Received!"
            InteractionState.RECEIVER_READY -> "🖐" to "Release to Transfer"
            InteractionState.RELEASING -> "🚀" to "Transferring..."
            else -> "✋" to "Air Transfer"
        }

        canvas.drawText("$icon  $label", screenW / 2f, pillTop + 54f, textPaint)
    }

    private fun drawFloatingCard(canvas: Canvas, thumb: Bitmap) {
        if (thumb.isRecycled) return

        try {
            val cardW = 280f * cardScale
            val cardH = 440f * cardScale
            val left = currentCardX - cardW / 2f
            val top = currentCardY - cardH / 2f
            val rect = RectF(left, top, left + cardW, top + cardH)

            // Drop shadow
            val shadowRect = RectF(rect).apply { offset(0f, 16f * cardScale) }
            canvas.drawRoundRect(shadowRect, 32f * cardScale, 32f * cardScale, shadowPaint)

            // Rounded thumbnail
            val path = Path().apply {
                addRoundRect(rect, 28f * cardScale, 28f * cardScale, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(thumb, null, rect, cardPaint)
            canvas.restore()

            // Glowing border
            canvas.drawRoundRect(rect, 28f * cardScale, 28f * cardScale, borderPaint)
        } catch (_: Exception) {
        }
    }

    private fun drawArrivalCard(canvas: Canvas, bmp: Bitmap, screenW: Float, screenH: Float) {
        if (bmp.isRecycled) return

        try {
            val scale = arrivalProgress
            val cardW = screenW * 0.75f * scale
            val cardH = screenH * 0.68f * scale
            val left = (screenW - cardW) / 2f
            val top = (screenH - cardH) / 2f
            val rect = RectF(left, top, left + cardW, top + cardH)

            val shadowRect = RectF(rect).apply { offset(0f, 22f) }
            canvas.drawRoundRect(shadowRect, 40f, 40f, shadowPaint)

            val path = Path().apply {
                addRoundRect(rect, 36f, 36f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, null, rect, cardPaint)
            canvas.restore()

            // Emerald arrival glow
            val emeraldBorder = Paint(borderPaint).apply {
                color = Color.argb(230, 16, 185, 129)
                strokeWidth = 6f
            }
            canvas.drawRoundRect(rect, 36f, 36f, emeraldBorder)
        } catch (_: Exception) {
        }
    }
}