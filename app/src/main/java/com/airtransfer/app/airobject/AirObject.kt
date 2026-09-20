package com.airtransfer.app.airobject

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Domain representation of any transferable spatial entity.
 * In V2, the only implemented and active type is SCREENSHOT.
 * Designed so that IMAGE and VIDEO can be added in V3/V4 without redesigning the architecture.
 */
enum class AirObjectType {
    SCREENSHOT,
    IMAGE,
    VIDEO
}

/**
 * Metadata representation of an AirObject for lightweight protocol exchange.
 */
@Serializable
data class AirObjectMetadata(
    val id: String,
    val type: String,
    val width: Int,
    val height: Int,
    val encodedSize: Long,
    val mimeType: String,
    val timestamp: Long,
    val sourceDeviceId: String,
    val sessionId: String,
    val checksum: String
)

/**
 * Full in-memory AirObject entity holding thumbnail and compressed byte payload.
 */
data class AirObject(
    val id: String = UUID.randomUUID().toString(),
    val type: AirObjectType = AirObjectType.SCREENSHOT,
    val encodedBytes: ByteArray,
    val thumbnail: Bitmap? = null,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg",
    val timestamp: Long = System.currentTimeMillis(),
    val sourceDeviceId: String = "",
    val sessionId: String = "",
    val checksum: String = computeChecksum(encodedBytes)
) {
    fun toMetadata(): AirObjectMetadata = AirObjectMetadata(
        id = id,
        type = type.name,
        width = width,
        height = height,
        encodedSize = encodedBytes.size.toLong(),
        mimeType = mimeType,
        timestamp = timestamp,
        sourceDeviceId = sourceDeviceId,
        sessionId = sessionId,
        checksum = checksum
    )

    fun decodeBitmap(): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AirObject
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun fromScreenshotBitmap(
            bitmap: Bitmap,
            sourceDeviceId: String,
            sessionId: String,
            quality: Int = 85
        ): AirObject {
            val width = bitmap.width
            val height = bitmap.height

            // Efficient thumbnail generation for fluid overlay tracking
            val thumbScale = 0.25f
            val thumbWidth = (width * thumbScale).toInt().coerceAtLeast(1)
            val thumbHeight = (height * thumbScale).toInt().coerceAtLeast(1)
            val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)

            // Compress to JPEG for high-speed P2P transmission
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()

            return AirObject(
                type = AirObjectType.SCREENSHOT,
                encodedBytes = bytes,
                thumbnail = thumbnail,
                width = width,
                height = height,
                mimeType = "image/jpeg",
                sourceDeviceId = sourceDeviceId,
                sessionId = sessionId
            )
        }

        fun computeChecksum(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }
    }
}