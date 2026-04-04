//EmailSender.kt
package com.example.sleepalertapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import javax.mail.*
import javax.mail.internet.*

object EmailSender {

    // [追加] 通知を出す共通関数
    private fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
        val channelId = "email_status_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "メール送信状態", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
    // [追加] ここまで

    fun send(context: Context, to: String, subject: String, body: String) {
        val username = "ana05224@gmail.com"
        val password = "cuznecjtebqrnhie"  // アプリパスワード
        //    val username = "gmstasou@gmail.com"
        //    val password = "cldjqarfycoawptc"  // 麻生　アプリパスワード

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(username))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setText(body)
                }

                Transport.send(message)
                Log.d("EmailSender", "メール送信成功: $to")

                // [追加] 送信成功通知（宛先ごとに異なるIDで通知）
                val notificationId = (to.hashCode() and 0x7FFFFFFF) % 1000 + 300
                showNotification(context, "メール送信成功", "$to へ送信しました", notificationId)
                // [追加] ここまで

            } catch (e: Exception) {
                Log.e("EmailSender", "メール送信失敗: ${e.message}")

                // [追加] 送信失敗通知（宛先ごとに異なるIDで通知）
                val notificationId = (to.hashCode() and 0x7FFFFFFF) % 1000 + 400
                showNotification(context, "メール送信失敗", "$to への送信に失敗しました", notificationId)
                // [追加] ここまで
            }
        }
    }

    fun sendMultiple(context: Context, toList: List<String>, subject: String, body: String) {
        toList.forEach { to ->
            send(context, to, subject, body)
        }
    }
}