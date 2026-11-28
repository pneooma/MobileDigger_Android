package com.example.mobiledigger.ipod

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.example.mobiledigger.MainActivity
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app running while the iPod outbox is converting or sending, or an iPod job (delete,
 * playlist, edit) is in progress — screen off, app in the background, phone locked. Shows the
 * progress in a notification, holds a partial wake lock so conversion runs at full speed, and
 * stops itself once there's nothing left it can do.
 */
class IpodService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "iPod transfers", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, build(IpodManager.ui.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wake == null) wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MobileDigger:ipod").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        if (!started) {
            started = true
            scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                var idleTicks = 0
                while (true) {
                    val ui = IpodManager.ui.value
                    // A short grace period: between two batches / right after a job there's a moment with nothing running.
                    if (IpodManager.hasBackgroundWork()) idleTicks = 0 else idleTicks++
                    if (idleTicks >= 3) break
                    nm.notify(NOTIF_ID, build(ui))
                    delay(1000)
                }
                CrashLogger.log("iPod", "background service: nothing left to do, stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun build(ui: IpodManager.Ui): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val q = ui.queue
        val done = q.count { it.state == IpodManager.TState.DONE }
        val failed = q.count { it.state == IpodManager.TState.FAILED }
        val converting = q.count { it.state == IpodManager.TState.CONVERTING || it.state == IpodManager.TState.PREPARING }
        val waiting = q.count { it.state == IpodManager.TState.WAITING }
        val ready = q.count { it.state == IpodManager.TState.READY }
        val sending = q.count { it.state == IpodManager.TState.SENDING }
        val title = when {
            q.isEmpty() -> ui.message.ifBlank { "Working on the iPod…" }
            sending > 0 -> "Sending to the iPod · $done of ${q.size} done"
            else -> "iPod transfer · $done of ${q.size} done"
        }
        val text = if (q.isEmpty()) "" else listOfNotNull(
            converting.takeIf { it > 0 }?.let { "converting $it" },
            waiting.takeIf { it > 0 }?.let { "$it waiting" },
            ready.takeIf { it > 0 }?.let { "$it ready" },
            sending.takeIf { it > 0 }?.let { "$it sending" },
            failed.takeIf { it > 0 }?.let { "$failed failed" }
        ).joinToString(" · ")
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text.ifBlank { ui.message })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (q.isNotEmpty()) b.setProgress(q.size, done + failed, false)
        else if (ui.progress != null) b.setProgress(1000, (ui.progress * 1000).toInt(), false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wake?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ipod"
        private const val NOTIF_ID = 4712

        /** Starts the service (no-op if it's running). Android refuses this while the app is in the background; then it's retried next time the app is opened. */
        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, IpodService::class.java)) }
                .onFailure { CrashLogger.log("iPod", "background service not started: ${it.message}") }
        }
    }
}
