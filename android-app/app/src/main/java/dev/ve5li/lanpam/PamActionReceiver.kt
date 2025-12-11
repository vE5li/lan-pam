package dev.ve5li.lanpam

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PamActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ACCEPT = "dev.ve5li.lanpam.ACTION_ACCEPT"
        const val ACTION_REJECT = "dev.ve5li.lanpam.ACTION_REJECT"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_ACCEPT -> {
                Log.d("PamActionReceiver", "User accepted request $requestId")
                PamResponseHandler.respondToRequest(requestId, true)
            }
            ACTION_REJECT -> {
                Log.d("PamActionReceiver", "User rejected request $requestId")
                PamResponseHandler.respondToRequest(requestId, false)
            }
        }

        // Cancel the notification
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
