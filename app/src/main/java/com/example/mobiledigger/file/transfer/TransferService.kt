package com.example.mobiledigger.file.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.example.mobiledigger.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a folder is copied/moved from a network drive, and shows the
 * progress in a notification. Stops itself when the transfer pauses or finishes.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val channelId = "transfers"
    private val notifId = 4711

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channelId, "Copying from drives", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifId, build(TransferManager.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            TransferManager.state.collectLatest { st ->
                if (st.status != TransferStatus.RUNNING) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(notifId, build(st))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(st: TransferUi): Notification {
        val job = st.job
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (job == null) "Copying…" else "${if (job.move) "Moving" else "Copying"} ${job.folderName} to the phone"
        val text = if (job == null) "" else
            "${job.doneFiles}/${job.files.size} files · ${TransferManager.fmt(st.doneBytes)} of ${TransferManager.fmt(job.totalBytes)}" +
                (if (st.speedBps > 0) " · ${TransferManager.fmtSpeed(st.speedBps)}" + (st.etaSeconds?.let { " · " + TransferManager.fmtEta(it) } ?: "") else "")
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(1000, (st.fraction * 1000).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
