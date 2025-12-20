package dev.ve5li.lanpam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TcpListenerService : Service() {
    private lateinit var tcpListener: TcpListener
    private lateinit var rsaCrypto: RsaCrypto
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tcp_listener_service"
        private const val CHANNEL_NAME = "PAM TCP Listener"

        fun start(context: Context) {
            val intent = Intent(context, TcpListenerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TcpListenerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TcpListenerService", "Service created")

        // Initialize crypto and listener
        rsaCrypto = RsaCrypto(this)
        tcpListener = TcpListener(this, rsaCrypto, 4200)

        // Create notification channel
        createNotificationChannel()

        // Acquire wake lock to keep CPU running for network operations
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LanPam::TcpListenerWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TcpListenerService", "Service started")

        // Start foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
            Log.d("TcpListenerService", "Wake lock acquired")
        }

        // Start TCP listener in service scope
        serviceScope.launch {
            tcpListener.start()
        }

        // If the service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TcpListenerService", "Service destroyed")

        // Stop TCP listener
        tcpListener.stop()

        // Release wake lock
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d("TcpListenerService", "Wake lock released")
        }

        // Cancel all coroutines
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that the PAM TCP listener is running"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LAN-PAM Active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
    }
}
