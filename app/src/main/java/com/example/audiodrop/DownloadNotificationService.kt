package com.example.audiodrop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

/** Keeps downloads alive in the background and exposes their progress in Android's notification shade. */
class DownloadNotificationService : Service() {
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private var receiverRegistered = false

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PROGRESS -> manager.notify(NOTIFICATION_ID, buildNotification(intent))
                ACTION_STOP -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while AudioDrop saves media"
                setShowBadge(false)
            }
        )
        val filter = IntentFilter().apply {
            addAction(ACTION_PROGRESS)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(progressReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(progressReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, buildNotification(intent))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(progressReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(intent: Intent): Notification {
        val playlist = intent.getBooleanExtra(EXTRA_PLAYLIST, false)
        val downloaded = intent.getIntExtra(EXTRA_DOWNLOADED, 0).coerceAtLeast(0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1).coerceAtLeast(1)
        val percent = intent.getIntExtra(EXTRA_PERCENT, 0).coerceIn(0, 100)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Preparing download…" }
        val status = intent.getStringExtra(EXTRA_STATUS).orEmpty()
        val paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
        val content = if (playlist) {
            val count = "$downloaded of $total songs · $title"
            if (paused) "$count · Paused" else if (status.isNotBlank()) "$count · $status" else count
        } else if (paused) "Paused · ${percent}%" else if (status.isNotBlank()) "$status · ${percent}%" else "Downloading · ${percent}%"
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePause = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_TOGGLE_PAUSE
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelDownload = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_CANCEL
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notification_pause, if (paused) "Resume" else "Pause", togglePause)
            .addAction(R.drawable.ic_notification_cancel, "Cancel", cancelDownload)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setProgress(if (playlist) total else 100, if (playlist) downloaded else percent, false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "audiodrop_downloads"
        private const val NOTIFICATION_ID = 8142
        private const val ACTION_START = "com.example.audiodrop.action.START_DOWNLOAD_NOTIFICATION"
        private const val ACTION_PROGRESS = "com.example.audiodrop.action.DOWNLOAD_PROGRESS"
        private const val ACTION_STOP = "com.example.audiodrop.action.STOP_DOWNLOAD_NOTIFICATION"
        const val ACTION_TOGGLE_PAUSE = "com.example.audiodrop.action.TOGGLE_DOWNLOAD_PAUSE"
        const val ACTION_CANCEL = "com.example.audiodrop.action.CANCEL_DOWNLOAD"
        private const val EXTRA_PLAYLIST = "playlist"
        private const val EXTRA_DOWNLOADED = "downloaded"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_PAUSED = "paused"

        fun start(context: Context, title: String) {
            val intent = progressIntent(context, ACTION_START, false, 0, 1, 0, title, "Preparing", false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun update(
            context: Context,
            playlist: Boolean,
            downloaded: Int,
            total: Int,
            percent: Int,
            title: String,
            status: String,
            paused: Boolean
        ) {
            val progress = progressIntent(context, ACTION_PROGRESS, playlist, downloaded, total, percent, title, status, paused)
            progress.component = null
            progress.setPackage(context.packageName)
            context.sendBroadcast(progress)
        }

        fun stop(context: Context) {
            context.sendBroadcast(Intent(ACTION_STOP).setPackage(context.packageName))
        }

        private fun progressIntent(
            context: Context,
            action: String,
            playlist: Boolean,
            downloaded: Int,
            total: Int,
            percent: Int,
            title: String,
            status: String,
            paused: Boolean
        ) = Intent(context, DownloadNotificationService::class.java).apply {
            this.action = action
            putExtra(EXTRA_PLAYLIST, playlist)
            putExtra(EXTRA_DOWNLOADED, downloaded)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_PERCENT, percent)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_PAUSED, paused)
        }
    }
}
