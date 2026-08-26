package com.nhn.gps.location.phone.tracker.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlertType
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.ui.zone.iconRes
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
            .setContentTitle(context.getString(R.string.zone_monitoring_active_title))
            .setContentText(context.getString(R.string.zone_monitoring_active_text))
            .setContentIntent(contentIntent(destination = DESTINATION_ZONE_ALERTS))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notify(alert: ZoneAlert) {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_zone_bottom)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, alert.iconRes()))
            .setContentTitle(
                context.getString(
                    when (alert.type) {
                        ZoneAlertType.ENTER -> R.string.zone_alert_enter_title
                        ZoneAlertType.LEAVE -> R.string.zone_alert_leave_title
                        ZoneAlertType.NEAR_DANGEROUS -> R.string.zone_alert_near_dangerous_title
                        ZoneAlertType.RETURNED_SAFE -> R.string.zone_alert_returned_safe_title
                    },
                    alert.zoneName,
                )
            )
            .setContentText(
                context.getString(
                    when (alert.type) {
                        ZoneAlertType.ENTER -> R.string.zone_alert_enter_text
                        ZoneAlertType.LEAVE -> R.string.zone_alert_leave_text
                        ZoneAlertType.NEAR_DANGEROUS -> R.string.zone_alert_near_dangerous_text
                        ZoneAlertType.RETURNED_SAFE -> R.string.zone_alert_returned_safe_text
                    },
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                contentIntent(
                    destination = DESTINATION_ALERT_DETAIL,
                    alertId = alert.id,
                )
            )
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.toInt(), notification)
    }

    fun cancel(alertId: Long) {
        NotificationManagerCompat.from(context).cancel(alertId.toInt())
    }

    private fun contentIntent(destination: String, alertId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TARGET_DESTINATION, destination)
            alertId?.let { putExtra(EXTRA_ZONE_ALERT_ID, it) }
        }
        val requestCode = alertId?.hashCode() ?: MONITORING_REQUEST_CODE
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.zone_alerts),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    companion object {
        const val EXTRA_TARGET_DESTINATION = "TARGET_DESTINATION"
        const val EXTRA_ZONE_ALERT_ID = "ZONE_ALERT_ID"
        const val DESTINATION_ALERT_DETAIL = "alert_detail"
        const val DESTINATION_ZONE_ALERTS = "zone_alerts"
        private const val MONITORING_REQUEST_CODE = 4101
    }
}
