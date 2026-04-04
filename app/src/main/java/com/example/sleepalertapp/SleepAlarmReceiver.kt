//SleepAlarmReceiver.kt
package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // [変更] ExtrasがあればそちらをExtrasから、なければSharedPrefsから読む
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
        // [変更] ここまで

        Log.d("SleepAlertReceiver", "メール送信トリガー(Alarm) toList=$toList") // [変更] ログ追加
        EmailSender.sendMultiple(context, toList, subject, body)
    }
}