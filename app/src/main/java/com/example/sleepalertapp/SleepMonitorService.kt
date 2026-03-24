package com.example.sleepalertapp

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.*
import android.util.Log

class SleepMonitorService : Service() {

    private lateinit var to: String
    private lateinit var subject: String
    private lateinit var body: String
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("SleepAlertService", "画面OFF、5分後にアラーム設定")
                    setSleepAlarm()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("SleepAlertService", "画面ON、アラームキャンセル")
                    updateLastActiveTime()
                    cancelSleepAlarm()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    screenReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                // API 33 未満は RECEIVER_NOT_EXPORTED を使えないため従来のシグネチャで登録
                registerReceiver(screenReceiver, filter)
            }

            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("Service", "監視中")

        // STOP命令チェック
        if (intent?.action == "STOP_MONITORING") {
            stopMonitoring()
            return START_NOT_STICKY
        }

        loadSettings()
        to = intent?.getStringExtra("to") ?: to
        subject = intent?.getStringExtra("subject") ?: "緊急連絡"
        body = intent?.getStringExtra("body") ?: "自動送信メッセージ"

        // 👇追加
        updateLastActiveTime()
        saveSettings()

        val notification = createNotification()
        startForeground(1, notification)

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "SleepMonitorChannel"
        val channel = NotificationChannel(channelId, "監視サービス", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("スリープ監視中")
            .setContentText("端末がスリープになるとメールを送信します")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }


    @SuppressLint("ScheduleExactAlarm")
    private fun setSleepAlarm() {
        Log.d("Alarm", "送信予定セット")

        // ① 警告用
        val warningIntent = Intent(this, WarningReceiver::class.java).apply {
            putExtra("to", to)
            putExtra("subject", subject)
            putExtra("body", body)
        }

        val warningPendingIntent = PendingIntent.getBroadcast(
            this, 1, warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ② 送信用
        val sendIntent = Intent(this, SleepAlarmReceiver::class.java).apply {
            putExtra("to", to)
            putExtra("subject", subject)
            putExtra("body", body)
        }

        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val now = System.currentTimeMillis()

        // 50秒後：警告
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + 50 * 1000L,
            warningPendingIntent
        )

        // 60秒後：送信
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + 60 * 1000L,
            sendPendingIntent
        )
    }

    private fun cancelSleepAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val warningIntent = Intent(this, WarningReceiver::class.java)
        val warningPendingIntent = PendingIntent.getBroadcast(
            this, 1, warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sendIntent = Intent(this, SleepAlarmReceiver::class.java)
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(warningPendingIntent)
        alarmManager.cancel(sendPendingIntent)

        Log.d("SleepAlertService", "全アラームキャンセル")
    }

    private fun updateLastActiveTime() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit().putLong("last_active", System.currentTimeMillis()).apply()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit()
            .putString("to", to)
            .apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        to = prefs.getString("to", "") ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                // unregisterReceiver は状態によっては例外になることがあるため握りつぶしではなくログに残す
                Log.w("SleepMonitorService", "unregisterReceiver failed", e)
            }
            isReceiverRegistered = false
        }

        cancelSleepAlarm()
    }

    private fun stopMonitoring() {
        cancelSleepAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}