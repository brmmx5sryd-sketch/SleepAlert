//EmailSender.kt
package com.example.sleepalertapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import javax.mail.*
import javax.mail.internet.*

object EmailSender {

    // [修正 #3] onSuccess / onFailure コールバックを追加
    // 送信の成否を呼び出し元で把握できるようにした
    fun send(
        context: Context,
        to: String,
        subject: String,
        body: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        val username = BuildConfig.GMAIL_USER
        val password = BuildConfig.GMAIL_PASS

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
                // [修正 #3] 成功コールバックを呼び出す
                onSuccess?.invoke()
            } catch (e: Exception) {
                Log.e("EmailSender", "メール送信失敗: ${e.message}")
                // [修正 #3] 失敗コールバックを呼び出す
                onFailure?.invoke(e)
            }
        }
    }

    // [修正 #3] sendMultiple にも全件成功時コールバックを追加
    // allSuccess: 全件送信成功時に呼ばれる
    // onAnyFailure: 1件でも失敗した場合に呼ばれる
    fun sendMultiple(
        context: Context,
        toList: List<String>,
        subject: String,
        body: String,
        onAllSuccess: (() -> Unit)? = null,
        onAnyFailure: ((String, Exception) -> Unit)? = null
    ) {
        if (toList.isEmpty()) return

        // [修正 #3] 成功・失敗カウントで全件結果を追跡
        var successCount = 0
        var failureCount = 0
        val total = toList.size

        toList.forEach { to ->
            send(
                context, to, subject, body,
                onSuccess = {
                    successCount++
                    if (successCount + failureCount == total && failureCount == 0) {
                        onAllSuccess?.invoke()
                    }
                },
                onFailure = { e ->
                    failureCount++
                    onAnyFailure?.invoke(to, e)
                }
            )
        }
    }
}
