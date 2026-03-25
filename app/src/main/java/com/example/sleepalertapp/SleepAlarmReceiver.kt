package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val toList = intent.getStringArrayListExtra("toList") ?: return
        val subject = intent.getStringExtra("subject") ?: "緊急連絡"
        val body = intent.getStringExtra("body") ?: "自動送信メッセージ"

        Log.d("SleepAlertReceiver", "メール送信トリガー(Alarm)")
        EmailSender.sendMultiple(context, toList, subject, body)

    }
}