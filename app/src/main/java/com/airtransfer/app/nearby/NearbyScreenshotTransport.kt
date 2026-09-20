package com.airtransfer.app.nearby

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.airtransfer.app.airobject.AirObject
import com.airtransfer.app.airobject.AirObjectMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles sending and receiving screenshot payloads via Nearby Connections.
 * Uses BYTES payloads exclusively (no files, no arbitrary payloads).
 * Automatically saves received screenshots into public Pictures/Screenshots or Download/AirTransfer.
 */
class NearbyScreenshotTransport(
    private val context: Context,
    private val presenceManager: NearbyPresenceManager
) {
    private var pendingMeta: NearbyProtocol.ControlMessage? = null
    private var bufferedScreenshotBytes: ByteArray? = null
    private var bufferedBitmap: Bitmap? = null
    private var bufferedSenderEndpointId: String? = null
    private var isCatchPending = false

    var activeAirObject: AirObject? = null
        private set

    var onPeerGrabbed: ((senderId: String) -> Unit)? = null
    var onPeerAborted: ((senderId: String) -> Unit)? = null
    var onTransferConfirmed: ((receiverId: String) -> Unit)? = null
    var onScreenshotReadyToPresent: ((savedUri: Uri?, bitmap: Bitmap?) -> Unit)? = null

    init {
        // Late-join peer sync: if a peer connects while an air object is held, immediately send to them
        presenceManager.onPeerConnected = { endpointId, peerName ->
            val airObj = activeAirObject
            if (airObj != null) {
                Log.d("ScreenshotTransport", "New peer $endpointId ($peerName) connected while holding air object. Syncing grab...")
                sendGrabToPeer(endpointId, airObj)
            }
        }
    }

    private fun sendGrabToPeer(endpointId: String, airObject: AirObject) {
        val metaJson = Json.encodeToString(AirObjectMetadata.serializer(), airObject.toMetadata())
        val metaMsg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_AIR_GRABBED,
            senderId = presenceManager.localDeviceId,
            payload = metaJson
        )
        presenceManager.sendControlMessage(endpointId, metaMsg)
        presenceManager.sendBytePayload(endpointId, airObject.encodedBytes)
    }

    /**
     * Broadcasts that a screenshot was grabbed and pre-buffers image bytes to all connected peers.
     */
    fun broadcastGrab(airObject: AirObject) {
        activeAirObject = airObject
        val metaJson = Json.encodeToString(AirObjectMetadata.serializer(), airObject.toMetadata())
        val metaMsg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_AIR_GRABBED,
            senderId = presenceManager.localDeviceId,
            payload = metaJson
        )

        // 1. Send announcement to all peers
        presenceManager.sendControlMessageToAll(metaMsg)

        // 2. Pre-buffer compressed bytes to all peers
        presenceManager.sendBytePayloadToAll(airObject.encodedBytes)

        Log.d("ScreenshotTransport", "Broadcasted grab announcement and pre-buffered ${airObject.encodedBytes.size} bytes")
    }

    /**
     * Broadcasts that grab was cancelled/aborted on sender device.
     */
    fun broadcastAbort() {
        activeAirObject = null
        val abortMsg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_AIR_ABORT,
            senderId = presenceManager.localDeviceId
        )
        presenceManager.sendControlMessageToAll(abortMsg)
        Log.d("ScreenshotTransport", "Broadcasted abort announcement")
    }

    /**
     * Confirms to sender that screenshot was successfully accepted by receiver.
     */
    fun sendTransferConfirmed(senderEndpointId: String) {
        activeAirObject = null
        val confirmMsg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_TRANSFER_CONFIRMED,
            senderId = presenceManager.localDeviceId
        )
        presenceManager.sendControlMessage(senderEndpointId, confirmMsg)
        Log.d("ScreenshotTransport", "Sent transfer confirmation to $senderEndpointId")
    }

    /**
     * Requests active screenshot transfer from sender (ad-hoc pull / catch recovery).
     */
    fun requestTransferFromPeers() {
        val reqMsg = NearbyProtocol.ControlMessage(
            type = NearbyProtocol.TYPE_REQUEST_TRANSFER,
            senderId = presenceManager.localDeviceId
        )
        presenceManager.sendControlMessageToAll(reqMsg)
        Log.d("ScreenshotTransport", "Broadcasted TYPE_REQUEST_TRANSFER to connected peers")
    }

    /**
     * Called when receiver executes the catch gesture (Fist -> Palm).
     * If bytes are already buffered, displays instantly. Otherwise requests from peers and marks catch pending.
     */
    fun executeCatch() {
        val bytes = bufferedScreenshotBytes
        val bmp = bufferedBitmap
        if (bytes != null && bmp != null) {
            val uri = saveScreenshotToPublicStorage(bytes)
            bufferedSenderEndpointId?.let { sendTransferConfirmed(it) }
            onScreenshotReadyToPresent?.invoke(uri, bmp)
            clearBuffer()
        } else {
            isCatchPending = true
            Log.d("ScreenshotTransport", "Catch triggered before bytes arrived; requesting transfer from peers...")
            requestTransferFromPeers()
        }
    }

    /**
     * Handles incoming byte payload from Nearby Connections.
     */
    fun handleIncomingBytes(
        endpointId: String,
        bytes: ByteArray,
        onScreenshotReceived: (savedUri: Uri?, bitmap: Bitmap?) -> Unit
    ) {
        val controlMsg = NearbyProtocol.decode(bytes)
        if (controlMsg != null) {
            when (controlMsg.type) {
                NearbyProtocol.TYPE_AIR_GRABBED, NearbyProtocol.TYPE_AIROBJECT_META -> {
                    pendingMeta = controlMsg
                    bufferedSenderEndpointId = endpointId
                    onPeerGrabbed?.invoke(endpointId)
                    Log.d("ScreenshotTransport", "Peer $endpointId grabbed air object")
                }
                NearbyProtocol.TYPE_REQUEST_TRANSFER -> {
                    Log.d("ScreenshotTransport", "Peer $endpointId requested transfer. Pushing active AirObject...")
                    val airObj = activeAirObject
                    if (airObj != null) {
                        sendGrabToPeer(endpointId, airObj)
                    }
                }
                NearbyProtocol.TYPE_AIR_ABORT, NearbyProtocol.TYPE_CANCEL -> {
                    clearBuffer()
                    onPeerAborted?.invoke(endpointId)
                    Log.d("ScreenshotTransport", "Peer $endpointId aborted air object")
                }
                NearbyProtocol.TYPE_TRANSFER_CONFIRMED -> {
                    activeAirObject = null
                    onTransferConfirmed?.invoke(endpointId)
                    Log.d("ScreenshotTransport", "Peer $endpointId confirmed transfer")
                }
            }
        } else {
            // Binary screenshot image payload pre-buffered!
            Log.d("ScreenshotTransport", "Received binary screenshot (${bytes.size} bytes) from $endpointId")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bufferedScreenshotBytes = bytes
            bufferedBitmap = bitmap
            bufferedSenderEndpointId = endpointId

            if (isCatchPending && bitmap != null) {
                val uri = saveScreenshotToPublicStorage(bytes)
                sendTransferConfirmed(endpointId)
                onScreenshotReadyToPresent?.invoke(uri, bitmap)
                onScreenshotReceived(uri, bitmap)
                clearBuffer()
            }
        }
    }

    fun clearBuffer() {
        bufferedScreenshotBytes = null
        bufferedBitmap = null
        bufferedSenderEndpointId = null
        isCatchPending = false
        pendingMeta = null
    }

    /**
     * Saves received screenshot directly into the system Gallery / Photos via MediaStore.
     */
    private fun saveScreenshotToPublicStorage(imageBytes: ByteArray): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "AirTransfer_$timestamp.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(imageBytes)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Log.d("ScreenshotTransport", "Screenshot saved via MediaStore: $uri")
                    uri
                } else null
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { it.write(imageBytes) }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                Log.d("ScreenshotTransport", "Screenshot saved to legacy storage: ${file.absolutePath}")
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e("ScreenshotTransport", "Failed to save received screenshot", e)
            null
        }
    }
}