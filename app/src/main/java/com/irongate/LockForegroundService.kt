package com.irongate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LockForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var db: IronGateDatabase
    private lateinit var usageStatsManager: UsageStatsManager
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        db = IronGateDatabase.getInstance(this)
        usageStatsManager = getSystemService(UsageStatsManager::class.java)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOCK -> startLockLoop()
            ACTION_END_LOCK -> scope.launch {
                endLock("Lock ended by user")
            }
            else -> if (LockStateStore.read(this).active) startLockLoop()
        }
        return START_STICKY
    }

    private fun startLockLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            startForeground(NOTIF_ID, buildNotification())
            var lastQueried = System.currentTimeMillis() - 1_000L
            while (isActive) {
                val lockState = LockStateStore.read(this@LockForegroundService)
                if (!lockState.active) {
                    stopSelf()
                    break
                }
                if (System.currentTimeMillis() >= lockState.endsAtMillis) {
                    endLock("Lock finished")
                    break
                }
                logBlockedUsageAttempts(lastQueried)
                lastQueried = System.currentTimeMillis()
                startForeground(NOTIF_ID, buildNotification())
                delay(1_000L)
            }
        }
    }

    private suspend fun logBlockedUsageAttempts(sinceMillis: Long) {
        val blocked = db.dao().getBlockedPackageNames().toSet()
        if (blocked.isEmpty()) return

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(sinceMillis, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                blocked.contains(event.packageName)
            ) {
                db.dao().insertAttemptLog(
                    AttemptLogEntity(
                        timestampMillis = now,
                        type = "OPEN_BLOCKED_APP",
                        packageName = event.packageName,
                        details = "Blocked app launch attempt detected by usage stats."
                    )
                )
            }
        }
    }

    private suspend fun endLock(reason: String) {
        db.dao().closeActiveSession(reason)
        LockStateStore.stop(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val blockedCount = runBlocking { db.dao().getBlockedPackageNames().size }
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Iron Gate Active")
            .setContentText("Iron Gate Active - $blockedCount apps blocked")
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Iron Gate Lock",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_LOCK = "com.irongate.action.START_LOCK"
        const val ACTION_END_LOCK = "com.irongate.action.END_LOCK"
        private const val CHANNEL_ID = "iron_gate_lock_channel"
        private const val NOTIF_ID = 1101
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val state = LockStateStore.read(context)
        if (state.active && state.endsAtMillis > System.currentTimeMillis()) {
            context.startForegroundService(Intent(context, LockForegroundService::class.java).apply {
                action = LockForegroundService.ACTION_START_LOCK
            })
        } else if (state.active) {
            LockStateStore.stop(context)
        }
    }
}
