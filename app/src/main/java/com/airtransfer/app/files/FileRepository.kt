package com.airtransfer.app.files

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class SelectedFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri
)

class FileRepository(private val context: Context) {

    fun getFileInfo(uri: Uri): SelectedFile? {
        val contentResolver = context.contentResolver
        var name = "unknown_file"
        var size = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(uri) ?: guessMimeType(name)
        val id = "file_${System.currentTimeMillis()}_${name.hashCode()}"

        return SelectedFile(
            id = id,
            name = name,
            size = size,
            mimeType = mimeType,
            uri = uri
        )
    }

    /**
     * Primary public directory for AirTransfer downloads.
     * Stored in /storage/emulated/0/Download/AirTransfer so it is visible
     * in any File Manager and Downloads app.
     */
    fun getReceivedFilesDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val airTransferDir = File(downloads, "AirTransfer")
        if (!airTransferDir.exists()) {
            airTransferDir.mkdirs()
        }
        return if (airTransferDir.exists() && airTransferDir.canWrite()) {
            airTransferDir
        } else {
            // Fallback to app external files directory if public directory isn't directly writable
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AirTransfer")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Saves an incoming stream to public storage and registers it with MediaStore
     * so that photos appear in Gallery/Photos, videos in Video players, and files in File Manager.
     */
    fun saveIncomingFile(fileName: String, mimeType: String, inputStream: InputStream): File {
        val resolvedMime = if (mimeType.isBlank() || mimeType == "application/octet-stream") {
            guessMimeType(fileName)
        } else mimeType

        val targetDir = getReceivedFilesDir()
        val targetFile = File(targetDir, fileName)

        var directWriteSuccess = false
        try {
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
            directWriteSuccess = true
            Log.d("FileRepository", "Direct write succeeded to public storage: ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.w("FileRepository", "Direct public file write failed, will rely on MediaStore or app directory", e)
        }

        // 1. If direct write succeeded, trigger MediaScanner immediately so Android indexes it
        if (directWriteSuccess) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(resolvedMime)
            ) { path, uri ->
                Log.d("FileRepository", "MediaScanner indexed: $path -> $uri")
            }
        }

        // 2. On Android 10+ (API 29+), also register media in MediaStore for instant Gallery visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (directWriteSuccess) {
                    targetFile.inputStream().use { input ->
                        saveToMediaStore(fileName, resolvedMime, input)
                    }
                } else {
                    saveToMediaStore(fileName, resolvedMime, inputStream)
                }
            } catch (e: Exception) {
                Log.e("FileRepository", "Error registering into MediaStore", e)
            }
        }

        // 3. If direct write failed (e.g. strict scoped storage without MediaStore direct file access), write to app storage
        if (!directWriteSuccess) {
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AirTransfer")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val fallbackFile = File(fallbackDir, fileName)
            try {
                FileOutputStream(fallbackFile).use { output ->
                    inputStream.copyTo(output)
                }
                Log.d("FileRepository", "Saved to fallback app directory: ${fallbackFile.absolutePath}")
                return fallbackFile
            } catch (e: Exception) {
                Log.e("FileRepository", "Failed fallback file write", e)
            }
        }

        return targetFile
    }

    private fun saveToMediaStore(fileName: String, mimeType: String, inputStream: InputStream): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val (collectionUri, relativePath) = when {
                mimeType.startsWith("image/") ->
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Pictures/AirTransfer"
                mimeType.startsWith("video/") ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Movies/AirTransfer"
                mimeType.startsWith("audio/") ->
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Music/AirTransfer"
                else ->
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Download/AirTransfer"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collectionUri, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    inputStream.copyTo(output)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Log.d("FileRepository", "MediaStore registered file at $uri ($relativePath)")
            }
            uri
        } catch (e: Exception) {
            Log.e("FileRepository", "saveToMediaStore exception for $fileName", e)
            null
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
