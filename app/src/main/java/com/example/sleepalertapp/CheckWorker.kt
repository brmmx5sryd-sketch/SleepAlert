//CheckWorker.kt
package com.example.sleepalertapp

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("CheckWorker", "Workerが起動しました")

        val prefs = applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // [追加] Serviceが生きているか確認
        val serviceAlive = isServiceRunning()
        Log.d("CheckWorker", "Service生存: $serviceAlive")

        if (serviceAlive) {
            // [追加] Serviceが生きていれば何もしない
            Log.d("CheckWorker", "Service生存中のため送信しない")
            return Result.success()
        }
        // [追加] ここまで

        // Serviceが死んでいる場合のみ以下を実行
        val lastActive = prefs.getLong("last_active", 0L)
        val workerThresholdSec = prefs.getLong("worker_threshold_sec", 25 * 60 * 60L) // デフォルト25時間
        val diff = now - lastActive

        Log.d("CheckWorker", "経過: ${diff/1000/60}分 / 閾値: ${workerThresholdSec/60}分")


        //val testMode = prefs.getBoolean("test_mode", false)
        //val thresholdValue = prefs.getLong("threshold_value", 24L)

     //   val limit = if (testMode) {
     //       thresholdValue * 60 * 1000L
     //   } else {
     //       thresholdValue * 60 * 60 * 1000L
     //   }

     //   val diffMin = diff / 1000 / 60
     //   val limitMin = limit / 1000 / 60
     //   Log.d("CheckWorker", "最終操作からの経過: ${diffMin}分 / 閾値: ${limitMin}分 / テストモード: $testMode")

    //    if (diff > limit) {

        // [変更] worker_threshold_sec から読み込む


        if (diff > workerThresholdSec * 1000L) {

            Log.d("CheckWorker", "閾値超過！メール送信します")

            val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
            val subject = prefs.getString("subject", "緊急連絡") ?: "緊急連絡"
            val body = prefs.getString("body", "自動送信メッセージ") ?: "自動送信メッセージ"

            Log.d("CheckWorker", "送信先: $toList")
            EmailSender.sendMultiple(applicationContext, toList, subject, body)
        } else {
            Log.d("CheckWorker", "閾値未満。送信なし")
        }

        return Result.success()
    }

    // [追加] Serviceが動いているか確認
    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == SleepMonitorService::class.java.name }
    }
    // [追加] ここまで
}