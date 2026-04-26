//BatteryReceiver.kt
package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val percent = (level * 100) / scale
        Log.d("BatteryReceiver", "バッテリー残量: $percent%")

        val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)

        if (percent > 60) {
            // 5%超えたらバッテリー低下送信フラグをリセット
            if (prefs.getBoolean("battery_low_sent", false)) {
                prefs.edit().putBoolean("battery_low_sent", false).apply()
                Log.d("BatteryReceiver", "バッテリー低下送信フラグをリセット")
            }
        } else {
            // 5%以下になった時点で送信

            // 送信済みフラグが立っていればスキップ
            if (prefs.getBoolean("battery_low_sent", false)) {
                Log.d("BatteryReceiver", "送信済みのためスキップ")
                return
            }

            val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
            val name = prefs.getString("sender_name", "") ?: ""
            val phone = prefs.getString("sender_phone", "") ?: ""

            if (toList.isEmpty() || name.isBlank()) {
                Log.w("BatteryReceiver", "送信先または名前が未設定のためスキップ")
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
            val body = MailTemplate.buildBody(name, phone, sleepLabel) + "\nバッテリー残量５％以下"

            Log.d("BatteryReceiver", "バッテリー低下メール送信開始: $toList")
            EmailSender.sendMultiple(context, toList, subject, body)

            // バッテリー低下送信フラグをON（5%超過で復帰するまで再送しない）
            prefs.edit().putBoolean("battery_low_sent", true).apply()
        }
    }
}