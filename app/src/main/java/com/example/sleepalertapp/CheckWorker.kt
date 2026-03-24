package com.example.sleepalertapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("Worker", "24時間チェック実行")

        val prefs = applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val lastActive = prefs.getLong("last_active", 0L)

        val now = System.currentTimeMillis()
        val diff = now - lastActive

        val limit = 24 * 60 * 60 * 1000L

        if (diff > limit) {
            val to = prefs.getString("to", "") ?: ""

            EmailSender.send(
                applicationContext,
                to,
                "異常検知",
                "24時間操作がありません"
            )
        }

        return Result.success()
    }
}