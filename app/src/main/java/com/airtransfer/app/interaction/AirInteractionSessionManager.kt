package com.airtransfer.app.interaction

import android.content.Context
import android.util.Log
import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.airobject.AirObjectManager
import com.airtransfer.app.capture.ScreenCaptureController
import com.airtransfer.app.gesture.GestureEngine
import com.airtransfer.app.gesture.GestureEvent
import com.airtransfer.app.gesture.HandCentroid
import com.airtransfer.app.gesture.InteractionState
import com.airtransfer.app.nearby.NearbyPresenceManager
import com.airtransfer.app.nearby.NearbyScreenshotTransport
import com.airtransfer.app.overlay.AirOverlayManager
import com.airtransfer.app.ui.components.HapticFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class AirGestureRuntimeState {
    DISABLED,
    STARTING,
    ACTIVE,
    PAUSED,
    ERROR
}

/**
 * Authoritative coordinator of the spatial Grab-Move-Release interaction session.
 */
class AirInteractionSessionManager(
    private val context: Context,
    private val airObjectManager: AirObjectManager,
    private val overlayManager: AirOverlayManager,
    private val presenceManager: NearbyPresenceManager,
    private val screenshotTransport: NearbyScreenshotTransport,
    private val hapticManager: HapticFeedbackManager
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _runtimeState = MutableStateFlow(AirGestureRuntimeState.DISABLED)
    val runtimeState: StateFlow<AirGestureRuntimeState> = _runtimeState.asStateFlow()

    private val _interactionState = MutableStateFlow(InteractionState.IDLE)
    val interactionState: StateFlow<InteractionState> = _interactionState.asStateFlow()

    private var screenCaptureController: ScreenCaptureController? = null
    private var gestureEngine: GestureEngine? = null
    private var currentSessionId: String = ""
    private var beaconJob: kotlinx.coroutines.Job? = null

    init {
        // Wire up transport callbacks
        screenshotTransport.onPeerGrabbed = { senderId ->
            Log.d("AirSession", "Peer $senderId grabbed an object. Arming receiver...")
            _interactionState.value = InteractionState.RECEIVER_EXPECTING
            gestureEngine?.setReceiverExpecting(true)
            hapticManager.performTick()
            overlayManager.updateState(InteractionState.RECEIVER_EXPECTING, null)
        }

        screenshotTransport.onPeerAborted = { senderId ->
            Log.d("AirSession", "Peer $senderId aborted grab. Disarming receiver...")
            gestureEngine?.setReceiverExpecting(false)
            if (_interactionState.value == InteractionState.RECEIVER_EXPECTING ||
                _interactionState.value == InteractionState.RECEIVER_FIST_DETECTED) {
                _interactionState.value = InteractionState.IDLE
                overlayManager.clear()
            }
        }

        screenshotTransport.onTransferConfirmed = { receiverId ->
            Log.d("AirSession", "Receiver $receiverId confirmed transfer! Dispatching card...")
            beaconJob?.cancel()
            _interactionState.value = InteractionState.COMPLETED
            hapticManager.performSuccess()
            overlayManager.showReleased()
            airObjectManager.clear()
        }

        screenshotTransport.onScreenshotReadyToPresent = { uri, bitmap ->
            Log.d("AirSession", "Screenshot ready to present! Showing arrival...")
            onIncomingScreenshot(bitmap, uri)
        }
    }

    fun attachScreenCaptureController(controller: ScreenCaptureController) {
        screenCaptureController = controller
    }

    fun attachGestureEngine(engine: GestureEngine) {
        gestureEngine = engine
    }

    fun setRuntimeState(state: AirGestureRuntimeState) {
        _runtimeState.value = state
    }

    fun onGestureEvent(event: GestureEvent) {
        when (event) {
            is GestureEvent.PalmArmed -> {
                Log.d("AirSession", "Palm Armed!")
                _interactionState.value = InteractionState.PALM_ARMED
                hapticManager.performTick()
                overlayManager.updateState(InteractionState.PALM_ARMED, null)
            }

            is GestureEvent.ScreenGrabbed -> {
                Log.d("AirSession", "Screen Grabbed!")
                _interactionState.value = InteractionState.GRABBED
                hapticManager.performGrabHaptic()

                currentSessionId = UUID.randomUUID().toString()
                val capturedObject = screenCaptureController?.grabScreenshot(
                    sourceDeviceId = presenceManager.localDeviceId,
                    sessionId = currentSessionId
                )

                if (capturedObject != null) {
                    airObjectManager.setActiveAirObject(capturedObject)
                    overlayManager.showGrabbed(capturedObject, event.centroid)

                    // Pre-buffer and beacon periodically to connected nearby peers
                    beaconJob?.cancel()
                    beaconJob = scope.launch(Dispatchers.IO) {
                        screenshotTransport.broadcastGrab(capturedObject)
                        while (_interactionState.value == InteractionState.GRABBED || _interactionState.value == InteractionState.MOVING) {
                            kotlinx.coroutines.delay(1500L)
                            if (_interactionState.value == InteractionState.GRABBED || _interactionState.value == InteractionState.MOVING) {
                                val metaJson = kotlinx.serialization.json.Json.encodeToString(
                                    com.airtransfer.app.airobject.AirObjectMetadata.serializer(),
                                    capturedObject.toMetadata()
                                )
                                val metaMsg = com.airtransfer.app.nearby.NearbyProtocol.ControlMessage(
                                    type = com.airtransfer.app.nearby.NearbyProtocol.TYPE_AIR_GRABBED,
                                    senderId = presenceManager.localDeviceId,
                                    payload = metaJson
                                )
                                presenceManager.sendControlMessageToAll(metaMsg)
                            }
                        }
                    }
                } else {
                    Log.w("AirSession", "Screen capture failed during grab")
                }
            }

            is GestureEvent.HandMoved -> {
                // In Huawei UX, card stays docked; status stays GRABBED/Holding
                if (_interactionState.value == InteractionState.GRABBED) {
                    overlayManager.updateState(InteractionState.GRABBED, null)
                }
            }

            is GestureEvent.ReceiverFistArrived -> {
                Log.d("AirSession", "Receiver detected approaching fist!")
                _interactionState.value = InteractionState.RECEIVER_FIST_DETECTED
                hapticManager.performTick()
                overlayManager.updateState(InteractionState.RECEIVER_FIST_DETECTED, null)
            }

            is GestureEvent.CatchTriggered -> {
                Log.d("AirSession", "Receiver catch triggered (Fist -> Palm)!")
                _interactionState.value = InteractionState.RECEIVER_CATCHING
                hapticManager.performSuccess()
                screenshotTransport.executeCatch()
            }

            is GestureEvent.Aborted -> {
                Log.d("AirSession", "Grab aborted on same device! Fading card...")
                beaconJob?.cancel()
                _interactionState.value = InteractionState.CANCELLED
                hapticManager.performTick()
                overlayManager.showAbort()
                scope.launch(Dispatchers.IO) {
                    screenshotTransport.broadcastAbort()
                }
                airObjectManager.clear()
            }

            is GestureEvent.Released -> {
                // Fallback release if fired
                Log.d("AirSession", "Released gesture event")
            }

            is GestureEvent.Completed -> {
                Log.d("AirSession", "Session Completed")
                beaconJob?.cancel()
                _interactionState.value = InteractionState.COMPLETED
            }

            is GestureEvent.Cancelled -> {
                Log.d("AirSession", "Session Cancelled")
                beaconJob?.cancel()
                _interactionState.value = InteractionState.CANCELLED
                overlayManager.showAbort()
                airObjectManager.clear()
            }
        }
    }

    fun onIncomingScreenshot(bitmap: android.graphics.Bitmap?, uri: android.net.Uri? = null) {
        hapticManager.performSuccess()
        overlayManager.showArrival(bitmap, uri)
    }

    fun reset() {
        beaconJob?.cancel()
        beaconJob = null
        _interactionState.value = InteractionState.IDLE
        airObjectManager.clear()
        overlayManager.clear()
    }
}