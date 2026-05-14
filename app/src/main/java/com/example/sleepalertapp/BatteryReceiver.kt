//BatteryReceiver.kt
package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val percent = (level * 100) / scale
        AppLog.d("BatteryReceiver", "バッテリー残量: $percent%")  // 頻繁に発火 → ENABLED=false で抑制

        val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

        // 20%以下の警告音フラグ処理
        if (percent > 20) {
            // 20%超えたら警告音フラグをリセット
            if (prefs.getBoolean("battery_warning_played", false)) {
                prefs.edit().putBoolean("battery_warning_played", false).apply()
                Log.d("BatteryReceiver", "バッテリー警告音フラグをリセット")
            }
        } else {
            // 20%以下：未再生の場合のみ警告音を鳴らす
            if (!prefs.getBoolean("battery_warning_played", false)) {
                Log.d("BatteryReceiver", "バッテリー20%以下 警告音再生開始")
                playVoiceRepeat(context, R.raw.batterywarning, repeatCount = 1, intervalMs = 0L)
                prefs.edit().putBoolean("battery_warning_played", true).apply()
            }
        }

        if (percent > 5) {
            // 5%超えたらバッテリー低下送信フラグをリセット
            if (prefs.getBoolean("battery_low_sent", false)) {
                prefs.edit().putBoolean("battery_low_sent", false).apply()
                Log.d("BatteryReceiver", "バッテリー低下送信フラグをリセット")  // イベント発生時のみ → 常に出力
            }
        } else {
            // 5%以下になった時点で送信

            // 送信済みフラグが立っていればスキップ
            if (prefs.getBoolean("battery_low_sent", false)) {
                AppLog.d("BatteryReceiver", "送信済みのためスキップ")  // 5%以下の間ずっと発火 → ENABLED=false で抑制
                return
            }

            val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
            val name = prefs.getString("sender_name", "") ?: ""
            val phone = prefs.getString("sender_phone", "") ?: ""

            if (toList.isEmpty() || name.isBlank()) {
                Log.w("BatteryReceiver", "送信先または名前が未設定のためスキップ")  // 異常系 → 常に出力
                return
            }

            // SleepAlarmReceiver と同じロジックで sleepLabel を生成
            val lastActive = prefs.getLong("last_active", System.currentTimeMillis())
            val sleepSec = (System.currentTimeMillis() - lastActive) / 1000
            val sleepHours = sleepSec / 3600
            val sleepMinutes = (sleepSec % 3600) / 60
            val roundedMinutes = (sleepMinutes / 10) * 10  // 10分単位で切り捨て
            val sleepLabel = "${sleepHours}時間${roundedMinutes.toString().padStart(2, '0')}分"

            // 件名・本文は既存の MailTemplate をそのまま流用
            // sleepLabel にはスリープ開始からの時間を渡し、本文末尾にバッテリー注記を追加
            val subject = MailTemplate.buildSubject(name)
            val body = MailTemplate.buildBody(name, phone, sleepLabel) + "\nバッテリー残量５％以下のため送信しています"

            Log.d("BatteryReceiver", "バッテリー低下メール送信開始: $toList")  // 重要イベント → 常に出力
            EmailSender.sendMultiple(context, toList, subject, body)

            // バッテリー低下送信フラグをON（5%超過で復帰するまで再送しない）
            prefs.edit().putBoolean("battery_low_sent", true).apply()
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
                    Log.d("BatteryReceiver", "警告音再生 $playedCount/$repeatCount 回目完了")

                    if (playedCount < repeatCount) {
                        handler.postDelayed({ playOnce() }, intervalMs)
                    }
                }

                mediaPlayer.start()
                Log.d("BatteryReceiver", "警告音再生開始 ${playedCount + 1}/$repeatCount 回目")

            } catch (e: Exception) {
                Log.e("BatteryReceiver", "警告音再生失敗: ${e.message}")
            }
        }

        playOnce()
    }
}