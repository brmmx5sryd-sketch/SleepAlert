package com.example.sleepalertapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import javax.mail.*
import javax.mail.internet.*

object EmailSender {
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
            } catch (e: Exception) {
                Log.e("EmailSender", "メール送信失敗: ${e.message}")
            }
        }
    }
}