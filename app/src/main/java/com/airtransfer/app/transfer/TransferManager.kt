package com.airtransfer.app.transfer

import com.airtransfer.app.files.SelectedFile
import kotlinx.coroutines.flow.StateFlow

interface TransferManager {
    val progressFlow: StateFlow<List<FileTransferProgress>>
    fun sendFiles(endpointId: String, files: List<SelectedFile>, onComplete: () -> Unit)
    fun setOnBatchCompletedListener(listener: (List<FileTransferProgress>) -> Unit)
}
