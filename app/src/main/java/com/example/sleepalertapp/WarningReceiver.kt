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

        // [前回修正 #3] 通知IDを固定値100から、SleepMonitorService が
        // putExtra("notification_id", requestCode) で渡した値に変更
        // これにより 6時間前(10)・3時間前(11)・30分前(12) の警告が
        // 互いに上書きされなくなる
        val notificationId = intent.getIntExtra("notification_id", 100)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "warning_channel"
        val channel = NotificationChannel(channelId, "警告", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("緊急メール送信予告")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        // [前回修正 #3] 固定の 100 ではなく notificationId を使用
        notificationManager.notify(notificationId, notification)

        // [デバッグ用] 警告通知と同時にメールも送信する
        // リリース時は MailTemplate.kt の DEBUG_SEND_ON_WARNING をコメントアウトする
        if (MailTemplate.DEBUG_SEND_ON_WARNING) {
             val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
             val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
             val name  = prefs.getString("sender_name", "") ?: ""
             val phone = prefs.getString("sender_phone", "") ?: ""
             val lastActive = prefs.getLong("last_active", System.currentTimeMillis())
             val sleepSec = (System.currentTimeMillis() - lastActive) / 1000
             val sleepHours = sleepSec / 3600
             val sleepMinutes = (sleepSec % 3600) / 60
             val roundedMinutes = (sleepMinutes / 10) * 10
             val sleepLabel = "${sleepHours}時間${roundedMinutes.toString().padStart(2, '0')}分"

             val subject = MailTemplate.buildSubject(name)
             val body    = MailTemplate.buildBody(name, phone, sleepLabel)

             Log.d("WarningReceiver", "[DEBUG] 警告と同時にメール送信 toList=$toList")
             EmailSender.sendMultiple(context, toList, subject, body)
        }
    }
}
