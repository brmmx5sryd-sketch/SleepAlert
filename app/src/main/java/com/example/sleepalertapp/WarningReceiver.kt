//WarningReceiver.kt
package com.example.sleepalertapp

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class WarningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WarningReceiver", "警告アラーム発火")

        val message = intent.getStringExtra("message")
            ?: "まもなく緊急メールを送信します。\nスマートフォンを操作してください。"

        val notificationId = intent.getIntExtra("notification_id", 100)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // notificationId に対応する音声ファイルを選択
        // 10: 6時間前 / 11: 3時間前 / 12: 30分前
        val soundResId = when (notificationId) {
            10   -> R.raw.warningvoice6h
            11   -> R.raw.warningvoice3h
            12   -> R.raw.warningvoice30min
            else -> R.raw.warningvoice30min
        }

        // 通知チャンネルは無音で作成（音声はMediaPlayerで3回再生するため）
        // 一度作成したチャンネルの音声は変更できないため、notificationId でチャンネルを分ける
        val channelId = "warning_channel_$notificationId"
        val channel = NotificationChannel(
            channelId,
            "警告",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)  // 通知音は鳴らさない（MediaPlayerで制御）
        }
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("緊急メール送信予告")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)

        // 音声を3回再生（出力 → 3秒待機 → 出力 → 3秒待機 → 出力）
        playVoiceRepeat(context, soundResId, repeatCount = 1, intervalMs = 0L)

        // [デバッグ用] 警告通知と同時にメールも送信する
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

    private fun playVoiceRepeat(
        context: Context,
        soundResId: Int,
        repeatCount: Int,
        intervalMs: Long
    ) {
        val handler = Handler(Looper.getMainLooper())
        var playedCount = 0

        fun playOnce() {
            if (playedCount >= repeatCount) return

            try {
                val uri = Uri.parse("android.resource://${context.packageName}/$soundResId")
                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(context, uri)
                    prepare()
                }

                mediaPlayer.setOnCompletionListener { mp ->
                    mp.release()
                    playedCount++
                    Log.d("WarningReceiver", "音声再生 $playedCount/$repeatCount 回目完了")

                    if (playedCount < repeatCount) {
                        // 再生完了後 3秒待機してから次の再生
                        handler.postDelayed({ playOnce() }, intervalMs)
                    }
                }

                mediaPlayer.start()
                Log.d("WarningReceiver", "音声再生開始 ${playedCount + 1}/$repeatCount 回目")

            } catch (e: Exception) {
                Log.e("WarningReceiver", "音声再生失敗: ${e.message}")
            }
        }

        playOnce()
    }
}