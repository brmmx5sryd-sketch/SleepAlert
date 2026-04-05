//SleepAlarmReceiver.kt
package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        val toList = intent.getStringArrayListExtra("toList")?.toList()
            ?: run {
                val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
                prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
            }

        val subject = intent.getStringExtra("subject")
            ?: context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
                .getString("subject", "緊急連絡") ?: "緊急連絡"

        val body = intent.getStringExtra("body")
            ?: context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
                .getString("body", "自動送信メッセージ") ?: "自動送信メッセージ"

        Log.d("SleepAlertReceiver", "メール送信トリガー(Alarm) toList=$toList")
        EmailSender.sendMultiple(context, toList, subject, body)

        // [追加] 送信時刻を記録してServiceに再送信アラームをセットさせる
        val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sent", System.currentTimeMillis()).apply()
        Log.d("SleepAlertReceiver", "last_sent更新")

        // [追加] Serviceに再送信アラームをセットする命令を送る
        val serviceIntent = Intent(context, SleepMonitorService::class.java).apply {
            action = "SET_RESEND_ALARM"
        }
        context.startForegroundService(serviceIntent)
        // [追加] ここまで
    }
}