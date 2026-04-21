//WarningReceiver.kt
package com.example.sleepalertapp

import android.app.*
import android.content.*
import android.util.Log

class WarningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WarningReceiver", "警告アラーム発火")

        val message = intent.getStringExtra("message")
            ?: "まもなく緊急メールを送信します。\nスマートフォンを操作してください。"

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "warning_channel"
        val channel = NotificationChannel(channelId, "警告", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("緊急メール送信予告")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))  // 2行表示のため
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(100, notification)
    }
}