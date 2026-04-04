//CheckWorker.kt
package com.example.sleepalertapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("CheckWorker", "Workerが起動しました") // [変更] ログを詳細に

        val prefs = applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val lastActive = prefs.getLong("last_active", 0L)
        val now = System.currentTimeMillis()
        val diff = now - lastActive

        // [追加] テストモード・閾値をSharedPrefsから読む
        val testMode = prefs.getBoolean("test_mode", false)
        val thresholdValue = prefs.getLong("threshold_value", 24L)

        val limit = if (testMode) {
            thresholdValue * 60 * 1000L          // テストモード：分単位
        } else {
            thresholdValue * 60 * 60 * 1000L     // 通常：時間単位
        }
        // [追加] ここまで

        // [追加] 動作確認用ログ
        val diffMin = diff / 1000 / 60
        val limitMin = limit / 1000 / 60
        Log.d("CheckWorker", "最終操作からの経過: ${diffMin}分 / 閾値: ${limitMin}分 / テストモード: $testMode")
        // [追加] ここまで

        if (diff > limit) {
            Log.d("CheckWorker", "閾値超過！メール送信します") // [追加]

            // [変更] "toList"キーから正しく読む（緊急③の修正）
            val toList = prefs.getStringSet("toList", emptySet())?.toList() ?: emptyList()
            val subject = prefs.getString("subject", "緊急連絡") ?: "緊急連絡"
            val body = prefs.getString("body", "自動送信メッセージ") ?: "自動送信メッセージ"

            Log.d("CheckWorker", "送信先: $toList")

            EmailSender.sendMultiple(
                applicationContext,
                toList,
                subject,
                body
            )
        } else {
            Log.d("CheckWorker", "閾値未満。送信なし") // [追加]
        }

        return Result.success()
    }
}