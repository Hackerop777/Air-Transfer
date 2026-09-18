package com.airtransfer.app.connection

import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback

interface ConnectionManager {
    fun requestConnection(endpointId: String, localName: String, onConnected: () -> Unit, onDisconnected: () -> Unit)
    fun createAdvertiserCallback(
        onIncomingConnection: (endpointId: String, deviceName: String) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ): ConnectionLifecycleCallback
    fun acceptConnection(endpointId: String, onConnected: () -> Unit, onDisconnected: () -> Unit)
    fun disconnect()
}
