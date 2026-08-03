package com.nhn.gps.location.phone.tracker.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val channelId = "zone_alerts"

    fun buildMonitoringNotification(): android.app.Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_zone_bottom)
            .setContentTitle("Zone monitoring active")
            .setContentText("GPS Location is watching your saved zones")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notify(alert: ZoneAlert) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val action = if (alert.isEnter) "Entered" else "Left"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_zone_bottom)
            .setContentTitle("$action ${alert.zoneName}")
            .setContentText(if (alert.isEnter) "You entered this zone" else "You left this zone")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Zone alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
