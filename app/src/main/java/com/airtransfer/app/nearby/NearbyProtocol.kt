package com.airtransfer.app.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NearbyProtocol {
    const val SERVICE_ID = "com.airtransfer.app.spatial.v2"

    const val TYPE_DEVICE_HELLO = "DEVICE_HELLO"
    const val TYPE_DEVICE_READY = "DEVICE_READY"
    const val TYPE_SESSION_REQUEST = "SESSION_REQUEST"
    const val TYPE_SESSION_ACCEPT = "SESSION_ACCEPT"
    const val TYPE_AIROBJECT_META = "AIROBJECT_META"
    const val TYPE_AIR_GRABBED = "AIR_GRABBED"
    const val TYPE_AIR_ABORT = "AIR_ABORT"
    const val TYPE_REQUEST_TRANSFER = "REQUEST_TRANSFER"
    const val TYPE_TRANSFER_CONFIRMED = "TRANSFER_CONFIRMED"
    const val TYPE_READY_TO_RELEASE = "READY_TO_RELEASE"
    const val TYPE_RELEASE = "RELEASE"
    const val TYPE_TRANSFER_COMPLETE = "TRANSFER_COMPLETE"
    const val TYPE_CANCEL = "CANCEL"
    const val TYPE_ERROR = "ERROR"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ControlMessage(
        val type: String,
        val senderId: String,
        val senderName: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val payload: String? = null
    )

    fun encode(message: ControlMessage): ByteArray {
        return json.encodeToString(message).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): ControlMessage? {
        return try {
            json.decodeFromString<ControlMessage>(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}