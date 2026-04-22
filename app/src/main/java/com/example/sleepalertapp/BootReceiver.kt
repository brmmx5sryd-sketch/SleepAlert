//BootReceiver.kt
package com.example.sleepalertapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // [修正 #1] 再起動直後に last_active を現在時刻でリセット
            // これがないと CheckWorker が「長時間無操作」と誤判定し
            // 起動直後にメールを誤送信してしまう
            context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_active", System.currentTimeMillis())
                .apply()

            // SharedPrefs から to1〜to3 / sender_name / sender_phone を
            // SleepMonitorService.loadSettings() が読み取るため、
            // Extra なしで起動しても設定は正しく復元される（意図的）
            val serviceIntent = Intent(context, SleepMonitorService::class.java)
            context.startForegroundService(serviceIntent)

            val workRequest = PeriodicWorkRequestBuilder<CheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "check_worker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
