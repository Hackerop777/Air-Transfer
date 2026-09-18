package com.airtransfer.app.discovery

import com.airtransfer.app.transfer.EndpointMetadata
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback

interface DiscoveryEngine {
    fun startAdvertising(metadata: EndpointMetadata, connectionLifecycleCallback: ConnectionLifecycleCallback)
    fun startDiscovery(onEndpointFound: (String, EndpointMetadata) -> Unit)
    fun stop()
}
