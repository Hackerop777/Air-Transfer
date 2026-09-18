package com.airtransfer.app.session

import com.airtransfer.app.files.SelectedFile
import com.airtransfer.app.transfer.FileTransferProgress

sealed class SessionState {
    object Idle : SessionState()
    data class Armed(val fileCount: Int, val totalBytes: Long) : SessionState()
    object Advertising : SessionState()
    object Discovering : SessionState()
    data class DeviceFound(val endpointId: String, val deviceName: String) : SessionState()
    data class IncomingRequest(val endpointId: String, val deviceName: String) : SessionState()
    object GestureArmed : SessionState()
    data class Connecting(val deviceName: String) : SessionState()
    data class Transferring(val progressList: List<FileTransferProgress>) : SessionState()
    data class Completed(val files: List<FileTransferProgress>) : SessionState()
    data class Error(val message: String) : SessionState()
}
