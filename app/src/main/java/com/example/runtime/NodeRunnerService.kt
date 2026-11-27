package com.example.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class NodeRunnerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isServiceActive = false
                NodeEngineHolder.engine.stop()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE, ACTION_START, null -> {
                val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: "NodeStudio"
                val scriptName = intent?.getStringExtra(EXTRA_SCRIPT_NAME) ?: "index.js"
                val serverPort = intent?.getIntExtra(EXTRA_SERVER_PORT, -1)?.takeIf { it > 0 }
                val lanHosting = intent?.getBooleanExtra(EXTRA_LAN_HOSTING, true) ?: true
                val wakeLockWanted = intent?.getBooleanExtra(EXTRA_WAKE_LOCK, true) ?: true

                if (wakeLockWanted) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }

                val notification = buildNotification(
                    projectName = projectName,
                    scriptName = scriptName,
                    serverPort = serverPort,
                    lanHosting = lanHosting,
                    wakeLockActive = wakeLock?.isHeld == true
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isServiceActive = true
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceActive = false
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NodeStudio::RunnerWakeLock").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // up to 6 hours while actively running
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Node.js Runner & Local Host",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Node.js scripts, local hosting endpoints, and WakeLock status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        projectName: String,
        scriptName: String,
        serverPort: Int?,
        lanHosting: Boolean,
        wakeLockActive: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NodeRunnerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hostSummary = if (serverPort != null) {
            val url = NetworkUtils.primaryHostUrl(serverPort, lanHosting)
            "Hosting $url"
        } else {
            "Running $scriptName"
        }
        val wakeTag = if (wakeLockActive) " • WakeLock ON" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentTitle("$projectName — Node.js Active")
            .setContentText("$hostSummary$wakeTag")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nodestudio_runner_channel"
        private const val NOTIFICATION_ID = 2214

        const val ACTION_START = "com.example.runtime.ACTION_START"
        const val ACTION_UPDATE = "com.example.runtime.ACTION_UPDATE"
        const val ACTION_STOP = "com.example.runtime.ACTION_STOP"

        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_SCRIPT_NAME = "extra_script_name"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_LAN_HOSTING = "extra_lan_hosting"
        const val EXTRA_WAKE_LOCK = "extra_wake_lock"

        @Volatile
        private var isServiceActive = false

        fun startOrUpdate(
            context: Context,
            projectName: String,
            scriptName: String,
            serverPort: Int?,
            lanHosting: Boolean,
            wakeLock: Boolean
        ) {
            val intent = Intent(context, NodeRunnerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_SCRIPT_NAME, scriptName)
                putExtra(EXTRA_SERVER_PORT, serverPort ?: -1)
                putExtra(EXTRA_LAN_HOSTING, lanHosting)
                putExtra(EXTRA_WAKE_LOCK, wakeLock)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isServiceActive = true
            }
        }

        fun stop(context: Context) {
            if (!isServiceActive) return
            isServiceActive = false
            val intent = Intent(context, NodeRunnerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.stopService(intent) }
        }
    }
}
