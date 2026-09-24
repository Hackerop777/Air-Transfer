package com.airtransfer.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.gesture.HandCentroid
import com.airtransfer.app.gesture.InteractionState

/**
 * Custom ultra-lightweight overlay view for system-wide spatial visualization.
 * Features:
 * - Top-center sleek frosted circular badge with morphing vector hand icon (NO cheap text/emojis)
 * - Fluid luminous white circle and concentric ripples for incoming screen reception
 * - Center-blooming spring animation for received screenshots with smooth gallery transition
 */
@SuppressLint("ViewConstructor")
class SpatialOverlayView(context: Context) : View(context) {

    private var currentState: InteractionState = InteractionState.IDLE
    private var activeAirObject: AirObject? = null
    private var receiverBitmap: Bitmap? = null
    private var receiverUri: Uri? = null

    // Sender floating card coordinates
    private var currentCardX = 0f
    private var currentCardY = 0f
    private var cardScale = 0f

    // Animators
    private var cardAnimator: ValueAnimator? = null
    private var arrivalAnimator: ValueAnimator? = null
    private var rippleAnimator: ValueAnimator? = null
    private var morphAnimator: ValueAnimator? = null

    private var arrivalProgress = 0f
    private var rippleProgress = 0f
    private var fingerCurlProgress = 0f // 0f = fully open palm, 1f = closed fist

    // Paints
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
        maskFilter = BlurMaskFilter(32f, BlurMaskFilter.Blur.NORMAL)
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.argb(220, 255, 255, 255) // Clean luminous white border
    }

    // Badge Paints
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255) // Sleek frosted white glass
    }
    private val badgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 15, 23, 42)
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(160, 226, 232, 240) // Slate-200 border
    }
    private val vectorHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(225, 30, 41, 59) // Deep slate-800
    }

    // Ripple Paints (Luminous White)
    private val rippleCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
    }
    private val rippleAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        maskFilter = BlurMaskFilter(36f, BlurMaskFilter.Blur.NORMAL)
    }
    private val rippleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for BlurMaskFilter
    }

    fun updateState(state: InteractionState, centroid: HandCentroid?) {
        val oldState = currentState
        currentState = state

        // Manage Ripple Animator for receiver states
        val isReceiving = state == InteractionState.RECEIVER_EXPECTING ||
                state == InteractionState.RECEIVER_FIST_DETECTED ||
                state == InteractionState.RECEIVER_CATCHING

        if (isReceiving) {
            startRippleAnimation()
        } else if (receiverBitmap == null) {
            stopRippleAnimation()
        }

        // Animate hand vector morphing between open palm (0f) and closed fist (1f)
        val targetCurl = when (state) {
            InteractionState.PALM_DETECTING, InteractionState.PALM_ARMED -> 0f
            InteractionState.GRABBED, InteractionState.MOVING, InteractionState.RECEIVER_FIST_DETECTED -> 1f
            InteractionState.RECEIVER_EXPECTING -> 0.75f
            InteractionState.RECEIVER_CATCHING -> 0f
            else -> 0.2f
        }

        if (targetCurl != fingerCurlProgress) {
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(fingerCurlProgress, targetCurl).apply {
                duration = 240L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    fingerCurlProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        invalidate()
    }

    private fun startRippleAnimation() {
        if (rippleAnimator?.isRunning == true) return
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                rippleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRippleAnimation() {
        rippleAnimator?.cancel()
        rippleAnimator = null
    }

    fun onGrabTriggered(airObject: AirObject, centroid: HandCentroid) {
        activeAirObject = airObject
        val screenW = width.toFloat().coerceAtLeast(1f)
        currentCardX = screenW / 2f
        currentCardY = 320f

        updateState(InteractionState.GRABBED, centroid)

        // Shrink screen into floating docked preview card
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
        // Upward fly-off animation on transfer confirmation
        cardAnimator?.cancel()
        cardAnimator = ValueAnimator.ofFloat(cardScale, 0f).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                cardScale = it.animatedValue as Float
                currentCardY -= 24f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeAirObject = null
                    currentState = InteractionState.COMPLETED
                    invalidate()
                }
            })
            start()
        }
    }

    fun onAbortTriggered() {
        cardAnimator?.cancel()
        cardAnimator = ValueAnimator.ofFloat(cardScale, 0f).apply {
            duration = 220L
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

    fun onScreenshotArrived(bitmap: Bitmap?, uri: Uri? = null) {
        stopRippleAnimation()
        receiverBitmap = bitmap
        receiverUri = uri
        currentState = InteractionState.RECEIVER_CATCHING

        arrivalAnimator?.cancel()
        arrivalAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener {
                arrivalProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Smoothly launch gallery viewer after bloom animation finishes (~1100ms)
        postDelayed({
            launchGalleryView()
        }, 1100L)

        // Smoothly fade out overlay card
        postDelayed({
            arrivalAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 320L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    arrivalProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        receiverBitmap = null
                        receiverUri = null
                        currentState = InteractionState.IDLE
                        invalidate()
                    }
                })
                start()
            }
        }, 2200L)
    }

    private fun launchGalleryView() {
        val uri = receiverUri ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Log.d("SpatialOverlay", "Seamlessly transitioned received image to Gallery: $uri")
        } catch (e: Exception) {
            Log.w("SpatialOverlay", "Failed to launch gallery intent", e)
        }
    }

    fun clear() {
        cardAnimator?.cancel()
        arrivalAnimator?.cancel()
        stopRippleAnimation()
        morphAnimator?.cancel()
        activeAirObject = null
        receiverBitmap = null
        receiverUri = null
        cardScale = 0f
        arrivalProgress = 0f
        currentState = InteractionState.IDLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val screenW = width.toFloat()
        val screenH = height.toFloat()

        // 1. Draw Luminous White Circle & Fluid Ripples when expecting or catching incoming screen
        val isReceiving = currentState == InteractionState.RECEIVER_EXPECTING ||
                currentState == InteractionState.RECEIVER_FIST_DETECTED ||
                (currentState == InteractionState.RECEIVER_CATCHING && arrivalProgress < 0.5f)

        if (isReceiving) {
            drawLuminousWhiteRipples(canvas, screenW, screenH)
        }

        // 2. Draw Top Sleek Frosted Badge with Morphing Vector Hand
        if (currentState != InteractionState.IDLE && currentState != InteractionState.COMPLETED) {
            drawTopVectorBadge(canvas, screenW)
        }

        // 3. Draw Floating AirObject Card (Sender)
        val airObj = activeAirObject
        val thumb = airObj?.thumbnail
        if (thumb != null && !thumb.isRecycled && cardScale > 0.01f) {
            drawFloatingCard(canvas, thumb)
        }

        // 4. Draw Incoming Screenshot Arrival Card (Receiver)
        val arrivalBmp = receiverBitmap
        if (arrivalBmp != null && !arrivalBmp.isRecycled && arrivalProgress > 0.01f) {
            drawArrivalCard(canvas, arrivalBmp, screenW, screenH)
        }
    }

    /**
     * Draws the Huawei-style luminous white circular core and concentric fluid ripples.
     */
    private fun drawLuminousWhiteRipples(canvas: Canvas, screenW: Float, screenH: Float) {
        val cx = screenW / 2f
        val cy = screenH / 2f

        // Soft outer ambient aura
        canvas.drawCircle(cx, cy, 54f, rippleAuraPaint)

        // 3 fluid expanding concentric rings
        for (i in 0..2) {
            val ringProgress = (rippleProgress + i * 0.333f) % 1f
            val radius = 45f + ringProgress * 230f
            val alpha = ((1f - ringProgress) * 190).toInt().coerceIn(0, 255)
            val strokeW = (4.5f * (1f - ringProgress * 0.5f)).coerceAtLeast(1.5f)

            rippleRingPaint.color = Color.argb(alpha, 255, 255, 255)
            rippleRingPaint.strokeWidth = strokeW
            canvas.drawCircle(cx, cy, radius, rippleRingPaint)
        }

        // Central bright luminous core
        canvas.drawCircle(cx, cy, 32f, rippleCorePaint)
    }

    /**
     * Draws a top-center minimalist frosted circular badge with a morphing vector hand icon.
     * Clean, quiet, and completely free of cheap text captions or emojis.
     */
    private fun drawTopVectorBadge(canvas: Canvas, screenW: Float) {
        val badgeRadius = 38f
        val badgeCx = screenW / 2f
        val badgeCy = 135f // Positioned cleanly below front camera cutout

        // Ambient drop shadow
        canvas.drawCircle(badgeCx, badgeCy + 6f, badgeRadius, badgeShadowPaint)

        // Frosted white circular disc
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBgPaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBorderPaint)

        // Draw Morphing Vector Hand
        drawMorphingHand(canvas, badgeCx, badgeCy, fingerCurlProgress)
    }

    /**
     * Vector draws an ultra-clean hand that smoothly morphs between an open palm (curl = 0)
     * and a closed fist (curl = 1).
     */
    private fun drawMorphingHand(canvas: Canvas, cx: Float, cy: Float, curl: Float) {
        canvas.save()
        // Center the hand coordinates around (cx, cy)
        canvas.translate(cx, cy)

        // 1. Palm base (rounded pill)
        val palmRect = RectF(-14f, -4f, 14f, 16f)
        canvas.drawRoundRect(palmRect, 10f, 10f, vectorHandPaint)

        // 2. 4 Fingers (morphing length/curl based on curl parameter: 0f = open, 1f = curled)
        val fingerWidth = 4.2f
        val maxFingerLen = 18f
        val minFingerLen = 6f
        val currentFingerLen = maxFingerLen - curl * (maxFingerLen - minFingerLen)

        val fingerOffsets = floatArrayOf(-9f, -3f, 3f, 9f)
        val heightOffsets = floatArrayOf(2f, 0f, 1f, 3f) // Middle finger longest

        for (i in 0..3) {
            val fx = fingerOffsets[i]
            val len = currentFingerLen - heightOffsets[i]
            val topY = -4f - len + (curl * 7f)
            val bottomY = 2f
            val fingerRect = RectF(fx - fingerWidth / 2f, topY, fx + fingerWidth / 2f, bottomY)
            canvas.drawRoundRect(fingerRect, fingerWidth / 2f, fingerWidth / 2f, vectorHandPaint)
        }

        // 3. Thumb (curls across the palm when closed)
        val thumbPath = Path().apply {
            val thumbX = -13f + curl * 4f
            val thumbY = 6f - curl * 3f
            moveTo(thumbX, thumbY)
            lineTo(thumbX - 6f * (1f - curl * 0.7f), thumbY - 8f * (1f - curl * 0.6f))
            lineTo(thumbX - 2f, thumbY - 10f * (1f - curl * 0.6f))
            close()
        }
        canvas.drawPath(thumbPath, vectorHandPaint)

        canvas.restore()
    }

    /**
     * Draws the floating card on the sender device (Huawei-accurate docked position).
     */
    private fun drawFloatingCard(canvas: Canvas, thumb: Bitmap) {
        if (thumb.isRecycled) return

        try {
            val cardW = 280f * cardScale
            val cardH = 440f * cardScale
            val left = currentCardX - cardW / 2f
            val top = currentCardY - cardH / 2f
            val rect = RectF(left, top, left + cardW, top + cardH)

            // Drop shadow
            val shadowRect = RectF(rect).apply { offset(0f, 18f * cardScale) }
            canvas.drawRoundRect(shadowRect, 32f * cardScale, 32f * cardScale, shadowPaint)

            // Rounded screenshot card
            val path = Path().apply {
                addRoundRect(rect, 28f * cardScale, 28f * cardScale, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(thumb, null, rect, cardPaint)
            canvas.restore()

            // Luminous white rim border
            canvas.drawRoundRect(rect, 28f * cardScale, 28f * cardScale, cardBorderPaint)
        } catch (_: Exception) {
        }
    }

    /**
     * Center-blooming spring animation for incoming screenshot card on receiver.
     */
    private fun drawArrivalCard(canvas: Canvas, bmp: Bitmap, screenW: Float, screenH: Float) {
        if (bmp.isRecycled) return

        try {
            val scale = arrivalProgress
            val cardW = screenW * 0.76f * scale
            val cardH = screenH * 0.68f * scale
            val left = (screenW - cardW) / 2f
            val top = (screenH - cardH) / 2f
            val rect = RectF(left, top, left + cardW, top + cardH)

            // Ambient natural shadow
            val shadowRect = RectF(rect).apply { offset(0f, 24f * scale) }
            canvas.drawRoundRect(shadowRect, 40f * scale, 40f * scale, shadowPaint)

            // Screen clip
            val path = Path().apply {
                addRoundRect(rect, 34f * scale, 34f * scale, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, null, rect, cardPaint)
            canvas.restore()

            // Crisp white luminous rim
            canvas.drawRoundRect(rect, 34f * scale, 34f * scale, cardBorderPaint)
        } catch (_: Exception) {
        }
    }
}