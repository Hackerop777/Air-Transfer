package com.airtransfer.app.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.airtransfer.app.airobject.AirObjectManager
import com.airtransfer.app.capture.ScreenCaptureController
import com.airtransfer.app.gesture.GestureEngine
import com.airtransfer.app.interaction.AirGestureRuntimeState
import com.airtransfer.app.interaction.AirInteractionSessionManager
import com.airtransfer.app.nearby.NearbyPresenceManager
import com.airtransfer.app.nearby.NearbyScreenshotTransport
import com.airtransfer.app.notification.AirTransferNotificationManager
import com.airtransfer.app.overlay.AirOverlayManager
import com.airtransfer.app.ui.components.HapticFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AirGestureService : LifecycleService() {

    companion object {
        const val ACTION_START_GESTURES = "com.airtransfer.app.ACTION_START_GESTURES"
        const val ACTION_STOP_GESTURES = "com.airtransfer.app.ACTION_STOP_GESTURES"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isServiceRunningFlow = MutableStateFlow(false)
        val isServiceRunningFlow: StateFlow<Boolean> = _isServiceRunningFlow.asStateFlow()

        private val _connectedPeersCountFlow = MutableStateFlow(0)
        val connectedPeersCountFlow: StateFlow<Int> = _connectedPeersCountFlow.asStateFlow()

        private val _primaryPeerNameFlow = MutableStateFlow<String?>(null)
        val primaryPeerNameFlow: StateFlow<String?> = _primaryPeerNameFlow.asStateFlow()

        var currentSessionManager: AirInteractionSessionManager? = null
            private set
    }

    private lateinit var notificationManager: AirTransferNotificationManager
    private var gestureEngine: GestureEngine? = null
    private var screenCaptureController: ScreenCaptureController? = null
    private var overlayManager: AirOverlayManager? = null
    private var presenceManager: NearbyPresenceManager? = null
    private var sessionManager: AirInteractionSessionManager? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AirTransferNotificationManager.ACTION_STOP_SERVICE) {
                Log.d("AirGestureService", "Disable requested from notification")
                stopAirGestures()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = AirTransferNotificationManager(this)

        val filter = IntentFilter(AirTransferNotificationManager.ACTION_STOP_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_GESTURES -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                startAirGestures(resultCode, resultData)
            }
            ACTION_STOP_GESTURES -> {
                stopAirGestures()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAirGestures(resultCode: Int, resultData: Intent?) {
        try {
            // 1. Promote to foreground immediately
            val notification = notificationManager.buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(AirTransferNotificationManager.NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(AirTransferNotificationManager.NOTIFICATION_ID, notification)
            }

            // 2. Initialize Overlay
            val overlay = AirOverlayManager(this).apply { attachOverlay() }
            overlayManager = overlay

            // 3. Initialize Nearby Presence & Transport
            val haptic = HapticFeedbackManager(this)
            val airObjectManager = AirObjectManager()

            var screenshotTransportRef: NearbyScreenshotTransport? = null
            val presence = NearbyPresenceManager(this) { endpointId, payload ->
                if (payload.type == com.google.android.gms.nearby.connection.Payload.Type.BYTES) {
                    val bytes = payload.asBytes() ?: return@NearbyPresenceManager
                    screenshotTransportRef?.handleIncomingBytes(endpointId, bytes) { uri, bitmap ->
                        sessionManager?.onIncomingScreenshot(bitmap)
                    }
                }
            }
            presenceManager = presence
            presence.startPresence()

            lifecycleScope.launch {
                presence.connectedPeers.collect { peers ->
                    _connectedPeersCountFlow.value = peers.size
                    _primaryPeerNameFlow.value = peers.values.firstOrNull()?.deviceName
                }
            }

            val screenshotTransport = NearbyScreenshotTransport(this, presence)
            screenshotTransportRef = screenshotTransport

            // 4. Initialize Session Coordinator
            val session = AirInteractionSessionManager(
                context = this,
                airObjectManager = airObjectManager,
                overlayManager = overlay,
                presenceManager = presence,
                screenshotTransport = screenshotTransport,
                hapticManager = haptic
            )
            sessionManager = session
            currentSessionManager = session
            session.setRuntimeState(AirGestureRuntimeState.ACTIVE)

            // 5. Initialize MediaProjection & ScreenCaptureController
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjection: MediaProjection = mpManager.getMediaProjection(resultCode, resultData)

                val captureController = ScreenCaptureController(this, mediaProjection) {
                    Log.i("AirGestureService", "ScreenCaptureController stopped")
                }
                screenCaptureController = captureController
                session.attachScreenCaptureController(captureController)
            } else {
                Log.w("AirGestureService", "MediaProjection result not OK, screen capture unavailable")
            }

            // 6. Initialize Background Camera Gesture Engine
            val engine = GestureEngine(this, this) { gestureEvent ->
                session.onGestureEvent(gestureEvent)
            }
            gestureEngine = engine
            session.attachGestureEngine(engine)
            engine.start()

            _isServiceRunningFlow.value = true
            Log.d("AirGestureService", "AirTransfer V2 Gesture Service running successfully in foreground")
        } catch (e: Exception) {
            Log.e("AirGestureService", "Failed to start AirGestureService", e)
            stopAirGestures()
        }
    }

    private fun stopAirGestures() {
        try {
            gestureEngine?.stop()
            gestureEngine = null

            screenCaptureController?.stop()
            screenCaptureController = null

            overlayManager?.detachOverlay()
            overlayManager = null

            presenceManager?.stopPresence()
            presenceManager = null

            sessionManager?.setRuntimeState(AirGestureRuntimeState.DISABLED)
            sessionManager?.reset()
            sessionManager = null
            currentSessionManager = null

            _isServiceRunningFlow.value = false
            _connectedPeersCountFlow.value = 0
            _primaryPeerNameFlow.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.d("AirGestureService", "AirGestureService stopped cleanly")
        } catch (e: Exception) {
            Log.e("AirGestureService", "Error stopping AirGestureService", e)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }
        stopAirGestures()
        super.onDestroy()
    }
}