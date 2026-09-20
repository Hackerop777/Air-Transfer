package com.airtransfer.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.airtransfer.app.MainActivity
import com.airtransfer.app.R

class AirTransferNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "air_transfer_gestures_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.airtransfer.app.ACTION_STOP_SERVICE"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Air Transfer Gestures",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when system-wide air gestures are enabled"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Disable Action Intent
        val stopServiceIntent = Intent(ACTION_STOP_SERVICE).apply {
            setPackage(context.packageName)
        }
        val stopServicePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Air Transfer")
            .setContentText("Air Gestures are active • Detecting hand gestures across your device")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disable",
                stopServicePendingIntent
            )
            .build()
    }
}