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


        val prefs      = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val name       = prefs.getString("sender_name", "") ?: ""
        val phone      = prefs.getString("sender_phone", "") ?: ""
        val lastActive = prefs.getLong("last_active", System.currentTimeMillis())
        val sleepSec   = (System.currentTimeMillis() - lastActive) / 1000
        val sleepHours   = sleepSec / 3600
        val sleepMinutes = (sleepSec % 3600) / 60
        val roundedMinutes = (sleepMinutes / 10) * 10  // 10分単位で切り捨て
        val sleepLabel   = "${sleepHours}時間${roundedMinutes.toString().padStart(2, '0')}分"

        val subject = MailTemplate.buildSubject(name)
        val body    = MailTemplate.buildBody(name, phone, sleepLabel)

        Log.d("SleepAlertReceiver", "メール送信トリガー(Alarm) toList=$toList")
        EmailSender.sendMultiple(context, toList, subject, body)

        // [追加] 送信時刻を記録してServiceに再送信アラームをセットさせる
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