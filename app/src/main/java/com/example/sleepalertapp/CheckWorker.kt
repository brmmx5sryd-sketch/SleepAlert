//CheckWorker.kt
package com.example.sleepalertapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("CheckWorker", "Workerが起動しました")

        val prefs = applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val serviceAlive = isServiceRunning()
        Log.d("CheckWorker", "Service生存: $serviceAlive")

        if (serviceAlive) {
            Log.d("CheckWorker", "Service生存中のため送信しない")
            return Result.success()
        }

        val lastActive   = prefs.getLong("last_active", 0L)
        val lastBootTime = prefs.getLong("last_boot_time", 0L)

        if (lastActive == 0L && lastBootTime == 0L) {
            Log.d("CheckWorker", "lastActive未設定のためスキップ")
            return Result.success()
        }

        // last_active・last_boot_timeのうち新しい方を基準にする
        val baseline = maxOf(lastActive, lastBootTime)
        val workerThresholdSec = prefs.getLong("worker_threshold_sec", 25 * 60 * 60L)
        val diff = now - baseline

        Log.d("CheckWorker", "経過: ${diff/1000/60}分 / 閾値: ${workerThresholdSec/60}分")

        if (diff > workerThresholdSec * 1000L) {
            Log.d("CheckWorker", "閾値超過！メール送信します")

            val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()

            val name  = prefs.getString("sender_name", "") ?: ""
            val phone = prefs.getString("sender_phone", "") ?: ""

            // メール本文のスリープ時間はlast_activeを基準にする
            val sleepBaseline = if (lastActive > 0L) lastActive else lastBootTime
            val sleepSec = (now - sleepBaseline) / 1000
            val sleepHours = sleepSec / 3600
            val sleepMinutes = (sleepSec % 3600) / 60
            val roundedMinutes = (sleepMinutes / 10) * 10
            val sleepLabel = "${sleepHours}時間${roundedMinutes.toString().padStart(2, '0')}分"

            val subject = MailTemplate.buildSubject(name)
            val body    = MailTemplate.buildBody(name, phone, sleepLabel)

            AppLog.d("CheckWorker", "送信先: $toList")
            EmailSender.sendMultiple(applicationContext, toList, subject, body)
        } else {
            Log.d("CheckWorker", "閾値未満。送信なし")
        }

        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        return applicationContext
            .getSharedPreferences("monitor", Context.MODE_PRIVATE)
            .getBoolean("service_alive", false)
    }
}