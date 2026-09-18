package com.airtransfer.app.transfer

import kotlinx.serialization.Serializable

@Serializable
data class ManifestFileInfo(
    val fileId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val payloadId: Long
)

@Serializable
data class TransferManifest(
    val transferId: String,
    val fileCount: Int,
    val totalBytes: Long,
    val files: List<ManifestFileInfo>
)

data class FileTransferProgress(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long,
    val percentage: Int,
    val speedBytesPerSec: Long,
    val isCompleted: Boolean,
    val localUri: String? = null
)

@Serializable
data class EndpointMetadata(
    val appProtocol: String = "AIR_TRANSFER",
    val version: Int = 1,
    val role: String, // "SENDER" or "RECEIVER"
    val state: String = "READY",
    val deviceName: String
)
