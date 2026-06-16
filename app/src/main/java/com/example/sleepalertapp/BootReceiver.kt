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

            // last_activeは触らない、再起動時刻を別キーで保存
            // CheckWorkerがlast_activeとlast_boot_timeの新しい方を基準にするため
            // 再起動による空白時間が無操作時間として加算されるのを防ぐ
            context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_boot_time", System.currentTimeMillis())
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