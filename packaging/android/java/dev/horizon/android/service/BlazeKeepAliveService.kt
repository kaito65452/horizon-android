package dev.horizon.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.horizon.android.bridge.BlazeNativeBridge
import dev.horizon.android.diagnostics.ProcessTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * Background persistence service holding PARTIAL_WAKE_LOCK and WIFI_MODE_FULL_HIGH_PERF
 * so background 8-thread downloads, server keep-alive packets, and Complex Voice Chat (ported by Asano Saryu)
 * stay active when switching applications.
 */
class BlazeKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        private const val CHANNEL_ID = "blaze_keepalive_channel"
        private const val NOTIFICATION_ID = 2603

        private val serviceJob = SupervisorJob()
        private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

        fun start(context: Context) {
            val intent = Intent(context, BlazeKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            cancelHeartbeat()
            val intent = Intent(context, BlazeKeepAliveService::class.java)
            context.stopService(intent)
        }

        /**
         * Cancels all background coroutines and heartbeat / watchdog jobs.
         */
        fun cancelHeartbeat() {
            try {
                serviceJob.cancelChildren()
            } catch (t: Throwable) {
                System.err.println("BlazeKeepAliveService cancelHeartbeat: ${t.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessTracker.isServiceRunning = true
        acquireLocks()
        startForegroundNotification()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BlazeLauncher::KeepAliveWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BlazeLauncher::KeepAliveWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            System.err.println("BlazeKeepAliveService locks: ${e.message}")
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Blaze Keep-Alive & Voice Chat",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps downloads and Complex Voice Chat active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blaze Engine 26.3 • Keep-Alive Active")
            .setContentText("Segmented downloads & Complex Voice Chat (ported by Asano Saryu) running")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        ProcessTracker.isServiceRunning = false
        cancelHeartbeat()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            System.err.println("BlazeKeepAliveService lock release error: ${e.message}")
        }
        wakeLock = null
        wifiLock = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
