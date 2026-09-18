package com.airtransfer.app.connection

import android.content.Context
import android.util.Log
import com.airtransfer.app.transfer.NearbyTransferManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyConnectionManager(
    private val context: Context,
    private val transferManager: NearbyTransferManager
) : ConnectionManager {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var activeEndpointId: String? = null

    override fun requestConnection(
        endpointId: String,
        localName: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ) {
        activeEndpointId = endpointId
        connectionsClient.requestConnection(
            localName,
            endpointId,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                    Log.d("NearbyConn", "Connection initiated with: $endpointId, auto-accepting on sender side")
                    connectionsClient.acceptConnection(endpointId, transferManager.payloadCallback)
                }

                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        Log.d("NearbyConn", "Connected successfully to: $endpointId")
                        onConnected()
                    } else {
                        Log.w("NearbyConn", "Connection failed with: $endpointId")
                        onDisconnected()
                    }
                }

                override fun onDisconnected(endpointId: String) {
                    Log.d("NearbyConn", "Disconnected from: $endpointId")
                    onDisconnected()
                }
            }
        ).addOnFailureListener { e ->
            Log.e("NearbyConn", "requestConnection failed", e)
            onDisconnected()
        }
    }

    private var advertiserConnectedCallback: (() -> Unit)? = null
    private var advertiserDisconnectedCallback: (() -> Unit)? = null

    override fun createAdvertiserCallback(
        onIncomingConnection: (endpointId: String, deviceName: String) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ): ConnectionLifecycleCallback {
        advertiserConnectedCallback = onConnected
        advertiserDisconnectedCallback = onDisconnected

        return object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                Log.d("NearbyConn", "Advertiser received connection request from: $endpointId (${connectionInfo.endpointName})")
                onIncomingConnection(endpointId, connectionInfo.endpointName)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    Log.d("NearbyConn", "Advertiser successfully connected to: $endpointId")
                    activeEndpointId = endpointId
                    advertiserConnectedCallback?.invoke()
                } else {
                    Log.w("NearbyConn", "Advertiser connection failed with: $endpointId (code=${result.status.statusCode})")
                    advertiserDisconnectedCallback?.invoke()
                }
            }

            override fun onDisconnected(endpointId: String) {
                Log.d("NearbyConn", "Advertiser disconnected from: $endpointId")
                activeEndpointId = null
                advertiserDisconnectedCallback?.invoke()
            }
        }
    }

    override fun acceptConnection(
        endpointId: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ) {
        activeEndpointId = endpointId
        advertiserConnectedCallback = onConnected
        advertiserDisconnectedCallback = onDisconnected

        connectionsClient.acceptConnection(endpointId, transferManager.payloadCallback)
            .addOnSuccessListener {
                Log.d("NearbyConn", "Successfully called acceptConnection for: $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e("NearbyConn", "Failed to call acceptConnection for: $endpointId", e)
                onDisconnected()
            }
    }

    override fun disconnect() {
        activeEndpointId?.let {
            connectionsClient.disconnectFromEndpoint(it)
            activeEndpointId = null
        }
        connectionsClient.stopAllEndpoints()
    }
}
