package com.cyberrick.phoneinspector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cyberrick.phoneinspector.R

/**
 * Keeps the process in the foreground while a scan runs so Android does not kill it when the user switches away.
 * Does no work itself; the view model runs the pipeline. Declared with the special use foreground type (API 34+).
 */
class ScanService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    // Lint's ForegroundServiceType check does not recognise foregroundServiceType="specialUse" although the
    // manifest declares it with the required PROPERTY_SPECIAL_USE_FGS_SUBTYPE; verified at runtime on API 36.
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.i(TAG, "ScanService start")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_scanning))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "CPIScan"
        private const val CHANNEL_ID = "scan"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) = context.startForegroundService(Intent(context, ScanService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, ScanService::class.java))
    }
}
