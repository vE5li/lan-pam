package com.example.phonepam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

class PamNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationIdCounter = AtomicInteger(1000)

    companion object {
        private const val CHANNEL_ID = "pam_requests"
        private const val CHANNEL_NAME = "PAM Authentication Requests"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for PAM authentication requests"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showPamRequest(requestId: String, requestBody: LanPamRequestBody): Int {
        val notificationId = notificationIdCounter.getAndIncrement()

        val acceptIntent = Intent(context, PamActionReceiver::class.java).apply {
            action = PamActionReceiver.ACTION_ACCEPT
            putExtra(PamActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(PamActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2,
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = Intent(context, PamActionReceiver::class.java).apply {
            action = PamActionReceiver.ACTION_REJECT
            putExtra(PamActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(PamActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2 + 1,
            rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Authentication Request"
        val message = buildString {
            append("Source: ${requestBody.source}\n")
            requestBody.user?.let { append("User: $it\n") }
            requestBody.service?.let { append("Service: $it\n") }
            requestBody.type?.let { append("Type: $it") }
        }

        // Build actions with authentication required
        val acceptAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Accept",
            acceptPendingIntent
        ).setAuthenticationRequired(true).build()

        val rejectAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Reject",
            rejectPendingIntent
        ).setAuthenticationRequired(true).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("${requestBody.source} - ${requestBody.user ?: "unknown user"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .addAction(acceptAction)
            .addAction(rejectAction)
            .build()

        notificationManager.notify(notificationId, notification)
        return notificationId
    }
}
