package com.airtransfer.app.discovery

import android.content.Context
import android.os.Build
import android.util.Log
import com.airtransfer.app.transfer.EndpointMetadata
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NearbyDiscoveryEngine(private val context: Context) : DiscoveryEngine {

    companion object {
        const val SERVICE_ID = "com.airtransfer.app"
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    override fun startAdvertising(
        metadata: EndpointMetadata,
        connectionLifecycleCallback: ConnectionLifecycleCallback
    ) {
        val endpointInfo = json.encodeToString(metadata)
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            endpointInfo,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("NearbyDiscovery", "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e("NearbyDiscovery", "Advertising failed", e)
        }
    }

    override fun startDiscovery(onEndpointFound: (String, EndpointMetadata) -> Unit) {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    try {
                        val meta = json.decodeFromString<EndpointMetadata>(info.endpointName)
                        if (meta.appProtocol == "AIR_TRANSFER") {
                            onEndpointFound(endpointId, meta)
                        }
                    } catch (e: Exception) {
                        Log.w("NearbyDiscovery", "Non-AirTransfer endpoint found: ${info.endpointName}")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("NearbyDiscovery", "Endpoint lost: $endpointId")
                }
            },
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("NearbyDiscovery", "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e("NearbyDiscovery", "Discovery failed", e)
        }
    }

    override fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }
}
