package com.airtransfer.app.transfer

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.airtransfer.app.files.FileRepository
import com.airtransfer.app.files.SelectedFile
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class NearbyTransferManager(
    private val context: Context,
    private val fileRepository: FileRepository
) : TransferManager {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _progressFlow = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    override val progressFlow: StateFlow<List<FileTransferProgress>> = _progressFlow.asStateFlow()

    private var onBatchCompleted: ((List<FileTransferProgress>) -> Unit)? = null

    // Unified tracking for BOTH sender and receiver: payloadId -> ManifestFileInfo
    private val activeTransferMap = ConcurrentHashMap<Long, ManifestFileInfo>()
    // Set of payload IDs sent by this device (used to differentiate sender vs receiver updates)
    private val outgoingPayloadIds = Collections.synchronizedSet(mutableSetOf<Long>())
    // Payloads received on the receiver side: payloadId -> Payload
    private val incomingPayloadMap = ConcurrentHashMap<Long, Payload>()

    private var currentManifest: TransferManifest? = null
    private var transferStartTime = 0L

    val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    // Control message (e.g. Manifest received on Receiver)
                    try {
                        val str = String(payload.asBytes() ?: byteArrayOf(), Charsets.UTF_8)
                        val manifest = json.decodeFromString<TransferManifest>(str)
                        currentManifest = manifest
                        transferStartTime = System.currentTimeMillis()

                        manifest.files.forEach { fileInfo ->
                            activeTransferMap[fileInfo.payloadId] = fileInfo
                        }

                        _progressFlow.value = manifest.files.map { fileInfo ->
                            FileTransferProgress(
                                fileId = fileInfo.fileId,
                                fileName = fileInfo.name,
                                fileSize = fileInfo.size,
                                bytesTransferred = 0L,
                                percentage = 0,
                                speedBytesPerSec = 0L,
                                isCompleted = false
                            )
                        }
                        Log.d("NearbyTransfer", "Manifest received with ${manifest.fileCount} files, registered payload IDs")
                    } catch (e: Exception) {
                        Log.e("NearbyTransfer", "Failed to parse manifest", e)
                    }
                }

                Payload.Type.FILE -> {
                    incomingPayloadMap[payload.id] = payload
                    Log.d("NearbyTransfer", "Incoming file payload registered: ${payload.id}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val bytesTransferred = update.bytesTransferred
            val totalBytes = update.totalBytes

            // Look up file in activeTransferMap (works for both Sender and Receiver!)
            val fileInfo = activeTransferMap[payloadId]
            if (fileInfo == null) {
                Log.v("NearbyTransfer", "Update for unmapped payload: $payloadId (bytes: $bytesTransferred)")
                return
            }

            val elapsedSec = ((System.currentTimeMillis() - transferStartTime) / 1000f).coerceAtLeast(0.1f)
            val speed = (bytesTransferred / elapsedSec).toLong()

            val actualTotalBytes = if (totalBytes > 0) totalBytes else fileInfo.size
            val percentage = if (actualTotalBytes > 0) {
                ((bytesTransferred * 100) / actualTotalBytes).toInt().coerceIn(0, 100)
            } else 0

            val isDone = update.status == PayloadTransferUpdate.Status.SUCCESS
            val isSender = outgoingPayloadIds.contains(payloadId)

            val currentList = _progressFlow.value.toMutableList()
            val idx = currentList.indexOfFirst { it.fileId == fileInfo.fileId }
            if (idx != -1) {
                currentList[idx] = currentList[idx].copy(
                    bytesTransferred = if (isDone) fileInfo.size else bytesTransferred,
                    percentage = if (isDone) 100 else percentage,
                    speedBytesPerSec = speed,
                    isCompleted = isDone
                )
                _progressFlow.value = currentList
            }

            if (isDone) {
                Log.d("NearbyTransfer", "Payload $payloadId completed. Role: ${if (isSender) "SENDER" else "RECEIVER"}")

                if (isSender) {
                    // SENDER: Check if all outgoing files completed
                    if (currentList.isNotEmpty() && currentList.all { it.isCompleted }) {
                        Log.d("NearbyTransfer", "Sender: All outgoing files completed successfully!")
                        onBatchCompleted?.invoke(currentList)
                    }
                } else {
                    // RECEIVER: Save the received file to public storage (Downloads / Pictures) & MediaStore
                    val payload = incomingPayloadMap[payloadId]
                    var savedFile: File? = null

                    // Method 1: ParcelFileDescriptor
                    val pfd = payload?.asFile()?.asParcelFileDescriptor()
                    if (pfd != null) {
                        try {
                            FileInputStream(pfd.fileDescriptor).use { input ->
                                savedFile = fileRepository.saveIncomingFile(fileInfo.name, fileInfo.mimeType, input)
                            }
                            pfd.close()
                        } catch (e: Exception) {
                            Log.e("NearbyTransfer", "Failed to stream copy via PFD for ${fileInfo.name}", e)
                        }
                    }

                    // Method 2: Fallback to asJavaFile
                    if (savedFile == null) {
                        payload?.asFile()?.asJavaFile()?.let { tempFile ->
                            try {
                                tempFile.inputStream().use { input ->
                                    savedFile = fileRepository.saveIncomingFile(fileInfo.name, fileInfo.mimeType, input)
                                }
                            } catch (e: Exception) {
                                Log.e("NearbyTransfer", "Failed to copy javaFile for ${fileInfo.name}", e)
                            }
                        }
                    }

                    if (idx != -1 && savedFile != null) {
                        currentList[idx] = currentList[idx].copy(localUri = savedFile!!.absolutePath)
                        _progressFlow.value = currentList
                    }

                    // Check if all files completed on receiver
                    if (currentList.isNotEmpty() && currentList.all { it.isCompleted }) {
                        Log.d("NearbyTransfer", "Receiver: All incoming files saved and completed!")
                        onBatchCompleted?.invoke(currentList)
                    }
                }
            }
        }
    }

    override fun sendFiles(
        endpointId: String,
        files: List<SelectedFile>,
        onComplete: () -> Unit
    ) {
        transferStartTime = System.currentTimeMillis()
        val manifestFiles = mutableListOf<ManifestFileInfo>()
        val filePayloads = mutableListOf<Pair<SelectedFile, Payload>>()

        outgoingPayloadIds.clear()

        files.forEach { selectedFile ->
            try {
                val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(selectedFile.uri, "r")
                if (pfd != null) {
                    val payload = Payload.fromFile(pfd)
                    filePayloads.add(selectedFile to payload)

                    val manifestItem = ManifestFileInfo(
                        fileId = selectedFile.id,
                        name = selectedFile.name,
                        size = selectedFile.size,
                        mimeType = selectedFile.mimeType,
                        payloadId = payload.id
                    )
                    manifestFiles.add(manifestItem)

                    // Register payload for progress tracking on SENDER!
                    activeTransferMap[payload.id] = manifestItem
                    outgoingPayloadIds.add(payload.id)
                }
            } catch (e: Exception) {
                Log.e("NearbyTransfer", "Failed to open file descriptor for ${selectedFile.name}", e)
            }
        }

        val totalBytes = files.sumOf { it.size }
        val manifest = TransferManifest(
            transferId = "transfer_${System.currentTimeMillis()}",
            fileCount = manifestFiles.size,
            totalBytes = totalBytes,
            files = manifestFiles
        )

        _progressFlow.value = manifestFiles.map { mf ->
            FileTransferProgress(
                fileId = mf.fileId,
                fileName = mf.name,
                fileSize = mf.size,
                bytesTransferred = 0L,
                percentage = 0,
                speedBytesPerSec = 0L,
                isCompleted = false
            )
        }

        // 1. Send manifest bytes first
        val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(manifestBytes))

        // 2. Stream file payloads
        filePayloads.forEach { (_, payload) ->
            connectionsClient.sendPayload(endpointId, payload)
        }

        Log.d("NearbyTransfer", "Sender: Dispatched manifest and ${filePayloads.size} file payloads")
        onComplete()
    }

    override fun setOnBatchCompletedListener(listener: (List<FileTransferProgress>) -> Unit) {
        onBatchCompleted = listener
    }
}
