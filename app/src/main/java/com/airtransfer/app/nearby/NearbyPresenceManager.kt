package com.airtransfer.app.nearby

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class NearbyPeer(
    val endpointId: String,
    val deviceName: String,
    val isConnected: Boolean = false,
    val isReady: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Maintains warm nearby presence via Google Nearby Connections.
 * Keeps advertising and discovery active in the background while Air Gestures is enabled
 * so that devices are already known and connected before the user performs a grab.
 * Includes a 4-second KeepAlive Heartbeat ping and an auto-healing reconnection watchdog.
 */
class NearbyPresenceManager(
    private val context: Context,
    private val onIncomingPayload: (endpointId: String, payload: Payload) -> Unit = { _, _ -> }
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    val localDeviceId: String = UUID.randomUUID().toString().take(8)
    val localDeviceName: String = "Air_${Build.MODEL.replace(" ", "_")}_$localDeviceId"

    private val presenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var watchdogJob: Job? = null

    private val _connectedPeers = MutableStateFlow<Map<String, NearbyPeer>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, NearbyPeer>> = _connectedPeers.asStateFlow()

    private val _primaryTargetPeer = MutableStateFlow<NearbyPeer?>(null)
    val primaryTargetPeer: StateFlow<NearbyPeer?> = _primaryTargetPeer.asStateFlow()

    private var isPresenceActive = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                if (bytes != null) {
                    val msg = NearbyProtocol.decode(bytes)
                    if (msg?.type == NearbyProtocol.TYPE_HEARTBEAT_PING) {
                        // Immediately answer keepalive ping with pong
                        sendControlMessage(
                            endpointId,
                            NearbyProtocol.ControlMessage(
                                type = NearbyProtocol.TYPE_HEARTBEAT_PONG,
                                senderId = localDeviceId
                            )
                        )
                        return
                    } else if (msg?.type == NearbyProtocol.TYPE_HEARTBEAT_PONG) {
                        // Peer is actively alive
                        return
                    }
                }
            }
            onIncomingPayload(endpointId, payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used for progress tracking
        }
    }

    var onPeerConnected: ((endpointId: String, peerName: String) -> Unit)? = null
    var onPeerDisconnected: ((endpointId: String) -> Unit)? = null

    fun startPresence() {
        if (isPresenceActive) return
        isPresenceActive = true
        Log.d("NearbyPresence", "Starting warm presence for $localDeviceName")

        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localDeviceName,
            NearbyProtocol.SERVICE_ID,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                    Log.d("NearbyPresence", "Incoming connection from $endpointId (${connectionInfo.endpointName}), auto-accepting")
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                }

                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        Log.d("NearbyPresence", "Connected to peer $endpointId")
                        val peer = NearbyPeer(endpointId, "Nearby Device", isConnected = true, isReady = true)
                        updatePeer(peer)
                        onPeerConnected?.invoke(endpointId, peer.deviceName)
                    } else {
                        Log.w("NearbyPresence", "Connection failed with $endpointId: ${result.status.statusCode}")
                    }
                }

                override fun onDisconnected(endpointId: String) {
                    Log.d("NearbyPresence", "Disconnected from $endpointId")
                    removePeer(endpointId)
                    onPeerDisconnected?.invoke(endpointId)
                }
            },
            options
        ).addOnFailureListener { e ->
            Log.e("NearbyPresence", "Failed to start advertising", e)
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            NearbyProtocol.SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("NearbyPresence", "Discovered endpoint: $endpointId (${info.endpointName})")

                    // Tie-breaker: only the device with lexicographically greater deviceName initiates requestConnection
                    // to prevent dual simultaneous connection collisions
                    val shouldInitiate = localDeviceName > info.endpointName
                    if (shouldInitiate) {
                        Log.d("NearbyPresence", "Initiating connection to $endpointId ($localDeviceName > ${info.endpointName})")
                        connectionsClient.requestConnection(
                            localDeviceName,
                            endpointId,
                            object : ConnectionLifecycleCallback() {
                                override fun onConnectionInitiated(id: String, cInfo: ConnectionInfo) {
                                    Log.d("NearbyPresence", "Outbound connection initiated with $id, auto-accepting")
                                    connectionsClient.acceptConnection(id, payloadCallback)
                                }

                                override fun onConnectionResult(id: String, res: ConnectionResolution) {
                                    if (res.status.isSuccess) {
                                        Log.d("NearbyPresence", "Outbound connection established with $id")
                                        val peer = NearbyPeer(id, info.endpointName, isConnected = true, isReady = true)
                                        updatePeer(peer)
                                        onPeerConnected?.invoke(id, info.endpointName)
                                    } else {
                                        Log.w("NearbyPresence", "Outbound connection failed with $id: ${res.status.statusCode}")
                                    }
                                }

                                override fun onDisconnected(id: String) {
                                    removePeer(id)
                                    onPeerDisconnected?.invoke(id)
                                }
                            }
                        )
                    } else {
                        Log.d("NearbyPresence", "Waiting for incoming connection from $endpointId (${info.endpointName} > $localDeviceName)")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("NearbyPresence", "Endpoint lost: $endpointId")
                    removePeer(endpointId)
                    onPeerDisconnected?.invoke(endpointId)
                }
            },
            options
        ).addOnFailureListener { e ->
            Log.e("NearbyPresence", "Failed to start discovery", e)
        }
    }

    private fun updatePeer(peer: NearbyPeer) {
        val current = _connectedPeers.value.toMutableMap()
        current[peer.endpointId] = peer
        _connectedPeers.value = current
        _primaryTargetPeer.value = current.values.firstOrNull { it.isConnected }
        startHeartbeat()
    }

    private fun removePeer(endpointId: String) {
        val current = _connectedPeers.value.toMutableMap()
        current.remove(endpointId)
        _connectedPeers.value = current
        _primaryTargetPeer.value = current.values.firstOrNull { it.isConnected }
        if (current.isEmpty()) {
            heartbeatJob?.cancel()
            scheduleAutoHealing()
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = presenceScope.launch {
            while (isActive && isPresenceActive) {
                delay(4000L)
                val connected = _connectedPeers.value.values.filter { it.isConnected }
                if (connected.isNotEmpty()) {
                    val ping = NearbyProtocol.ControlMessage(
                        type = NearbyProtocol.TYPE_HEARTBEAT_PING,
                        senderId = localDeviceId
                    )
                    sendControlMessageToAll(ping)
                }
            }
        }
    }

    private fun scheduleAutoHealing() {
        if (!isPresenceActive) return
        watchdogJob?.cancel()
        watchdogJob = presenceScope.launch {
            delay(1500L)
            if (isPresenceActive && _connectedPeers.value.isEmpty()) {
                Log.d("NearbyPresence", "Watchdog: 0 connected peers. Re-initiating discovery & advertising mesh...")
                try {
                    connectionsClient.stopDiscovery()
                    connectionsClient.stopAdvertising()
                } catch (_: Exception) {}
                delay(500L)
                if (isPresenceActive && _connectedPeers.value.isEmpty()) {
                    startAdvertising()
                    startDiscovery()
                }
            }
        }
    }

    fun sendControlMessage(endpointId: String, message: NearbyProtocol.ControlMessage) {
        val bytes = NearbyProtocol.encode(message)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun sendControlMessageToAll(message: NearbyProtocol.ControlMessage) {
        val endpoints = _connectedPeers.value.values.filter { it.isConnected }.map { it.endpointId }
        if (endpoints.isNotEmpty()) {
            val bytes = NearbyProtocol.encode(message)
            connectionsClient.sendPayload(endpoints, Payload.fromBytes(bytes))
        }
    }

    fun sendBytePayload(endpointId: String, bytes: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun sendBytePayloadToAll(bytes: ByteArray) {
        val endpoints = _connectedPeers.value.values.filter { it.isConnected }.map { it.endpointId }
        if (endpoints.isNotEmpty()) {
            connectionsClient.sendPayload(endpoints, Payload.fromBytes(bytes))
        }
    }

    fun stopPresence() {
        if (!isPresenceActive) return
        isPresenceActive = false

        try {
            heartbeatJob?.cancel()
            watchdogJob?.cancel()
            presenceScope.coroutineContext.cancelChildren()

            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            _connectedPeers.value = emptyMap()
            _primaryTargetPeer.value = null
            Log.d("NearbyPresence", "NearbyPresence stopped")
        } catch (e: Exception) {
            Log.e("NearbyPresence", "Error stopping NearbyPresence", e)
        }
    }
}