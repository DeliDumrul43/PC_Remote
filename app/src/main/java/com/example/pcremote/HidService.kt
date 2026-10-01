package com.example.pcremote

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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the HID connection alive while the app is in the background.
 * Android drops a Bluetooth HID app that is neither on screen nor running a foreground service.
 * The Bluetooth work itself is started from the screen too, so a failure here can't block the app.
 */
class HidService : Service() {

    companion object {
        private const val CHANNEL_ID = "remote_connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.pcremote.STOP"

        /** Returns false if the service couldn't start (e.g. not declared in the manifest). */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, HidService::class.java)) != null
        } catch (e: Exception) {
            Log.w("PCRemote", "Could not start HidService", e)
            false
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var hid: HidController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        hid = HidController.get(this)
        createChannel()
        val notification = buildNotification(getString(R.string.msg_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scope.launch {
            hid.state.collect { s ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(getString(s.message, s.arg ?: "")))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        hid.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hid.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.notif_channel_desc)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val open = PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HidService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.turn_off), stop)
            .build()
    }
}
