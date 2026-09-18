package com.airtransfer.app.session

import android.content.Context
import android.os.Build
import android.util.Log
import com.airtransfer.app.connection.NearbyConnectionManager
import com.airtransfer.app.discovery.NearbyDiscoveryEngine
import com.airtransfer.app.files.FileRepository
import com.airtransfer.app.files.SelectedFile
import com.airtransfer.app.transfer.EndpointMetadata
import com.airtransfer.app.transfer.NearbyTransferManager
import com.airtransfer.app.ui.components.HapticFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransferOrchestrator(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val fileRepository = FileRepository(context)
    val transferManager = NearbyTransferManager(context, fileRepository)
    val discoveryEngine = NearbyDiscoveryEngine(context)
    val connectionManager = NearbyConnectionManager(context, transferManager)
    val hapticManager = HapticFeedbackManager(context)

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var selectedFiles: List<SelectedFile> = emptyList()
    private var targetEndpointId: String? = null

    // Two-phase handshake buffers for receiver
    private var pendingReceiverEndpointId: String? = null
    private var isReceiverGestureArmed: Boolean = false

    init {
        transferManager.setOnBatchCompletedListener { completedList ->
            Log.d("Orchestrator", "Batch completed callback invoked with ${completedList.size} files")
            hapticManager.transferSuccess()
            _sessionState.value = SessionState.Completed(completedList)
        }

        // Collect transfer progress
        scope.launch {
            transferManager.progressFlow.collect { progressList ->
                if (progressList.isNotEmpty()) {
                    if (progressList.all { it.isCompleted }) {
                        hapticManager.transferSuccess()
                        _sessionState.value = SessionState.Completed(progressList)
                    } else {
                        _sessionState.value = SessionState.Transferring(progressList)
                    }
                }
            }
        }
    }

    // ================= SENDER FLOW =================

    fun onFilesSelected(files: List<SelectedFile>) {
        selectedFiles = files
    }

    fun onGrabGestureTriggered() {
        if (selectedFiles.isEmpty()) return
        hapticManager.actionConfirmed()

        val totalBytes = selectedFiles.sumOf { it.size }
        _sessionState.value = SessionState.Armed(selectedFiles.size, totalBytes)

        // Start discovery for compatible receivers
        _sessionState.value = SessionState.Discovering
        discoveryEngine.startDiscovery { endpointId, metadata ->
            if (metadata.role == "RECEIVER" && metadata.state == "READY") {
                Log.d("Orchestrator", "Compatible receiver found: ${metadata.deviceName} ($endpointId)")
                targetEndpointId = endpointId
                _sessionState.value = SessionState.DeviceFound(endpointId, metadata.deviceName)

                // Auto-connect touchlessly!
                connectToReceiver(endpointId, metadata.deviceName)
            }
        }
    }

    private fun connectToReceiver(endpointId: String, deviceName: String) {
        _sessionState.value = SessionState.Connecting(deviceName)
        val localDeviceName = Build.MODEL ?: "Android Device"

        connectionManager.requestConnection(
            endpointId = endpointId,
            localName = localDeviceName,
            onConnected = {
                discoveryEngine.stop()
                hapticManager.actionConfirmed()
                // Stream files immediately!
                transferManager.sendFiles(endpointId, selectedFiles) {
                    Log.d("Orchestrator", "File streaming started successfully")
                }
            },
            onDisconnected = {
                _sessionState.value = SessionState.Error("Connection lost or declined.")
            }
        )
    }

    // ================= RECEIVER FLOW =================

    fun startReceiverMode() {
        _sessionState.value = SessionState.Advertising
        pendingReceiverEndpointId = null
        isReceiverGestureArmed = false

        val localDeviceName = Build.MODEL ?: "Android Device"
        val metadata = EndpointMetadata(
            role = "RECEIVER",
            state = "READY",
            deviceName = localDeviceName
        )

        val advertiserCallback = connectionManager.createAdvertiserCallback(
            onIncomingConnection = { endpointId, senderName ->
                Log.d("Orchestrator", "Incoming connection request from: $senderName ($endpointId)")
                pendingReceiverEndpointId = endpointId
                hapticManager.stepComplete()

                if (isReceiverGestureArmed) {
                    Log.d("Orchestrator", "Receiver release gesture already completed! Accepting immediately.")
                    acceptReceiverConnection(endpointId)
                } else {
                    _sessionState.value = SessionState.IncomingRequest(endpointId, senderName)
                }
            },
            onConnected = {
                Log.d("Orchestrator", "Receiver connected with sender!")
                discoveryEngine.stop()
                hapticManager.actionConfirmed()
                _sessionState.value = SessionState.Transferring(emptyList())
            },
            onDisconnected = {
                _sessionState.value = SessionState.Error("Disconnected from sender.")
            }
        )

        discoveryEngine.startAdvertising(metadata, advertiserCallback)
    }

    fun onReleaseGestureTriggered() {
        Log.d("Orchestrator", "Release gesture confirmed on receiver!")
        hapticManager.actionConfirmed()
        isReceiverGestureArmed = true

        val pendingId = pendingReceiverEndpointId
        if (pendingId != null) {
            Log.d("Orchestrator", "Accepting pending sender connection: $pendingId")
            acceptReceiverConnection(pendingId)
        } else {
            Log.d("Orchestrator", "Receiver armed before sender request. Waiting for sender...")
            _sessionState.value = SessionState.GestureArmed
        }
    }

    private fun acceptReceiverConnection(endpointId: String) {
        _sessionState.value = SessionState.Connecting("Sender")
        connectionManager.acceptConnection(
            endpointId = endpointId,
            onConnected = {
                Log.d("Orchestrator", "Accept connection call initiated successfully")
            },
            onDisconnected = {
                _sessionState.value = SessionState.Error("Failed to accept connection.")
            }
        )
    }

    fun reset() {
        discoveryEngine.stop()
        connectionManager.disconnect()
        _sessionState.value = SessionState.Idle
        selectedFiles = emptyList()
        targetEndpointId = null
        pendingReceiverEndpointId = null
        isReceiverGestureArmed = false
    }
}
