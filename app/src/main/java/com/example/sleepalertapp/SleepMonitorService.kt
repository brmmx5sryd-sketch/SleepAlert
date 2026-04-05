//SleepMonitorService.kt
package com.example.sleepalertapp

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import android.provider.Settings

class SleepMonitorService : Service() {

    private var toList: List<String> = emptyList()
    private lateinit var subject: String
    private lateinit var body: String
    private var isReceiverRegistered = false

    // [追加] 送信間隔（秒）SharedPrefsから読む
    private var sendIntervalSec: Long = 60L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("SleepAlertService", "画面OFF、アラーム設定")
                    setSleepAlarm()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("SleepAlertService", "画面ON、アラームキャンセル")
                    updateLastActiveTime()
                    cancelSleepAlarm() // [変更] 再送信アラームも含めてキャンセル
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
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Service", "監視中")

        if (intent?.action == "STOP_MONITORING") {
            stopMonitoring()
            return START_NOT_STICKY
        }

        if (intent?.action == "SET_RESEND_ALARM") {
            setResendAlarm()
            return START_STICKY
        }
        loadSettings()

        val to1 = intent?.getStringExtra("to1")
        val to2 = intent?.getStringExtra("to2")
        val to3 = intent?.getStringExtra("to3")

        if (to1 != null || to2 != null || to3 != null) {
            toList = listOf(to1 ?: "", to2 ?: "", to3 ?: "").filter { it.isNotEmpty() }
            subject = intent?.getStringExtra("subject") ?: subject
            body = intent?.getStringExtra("body") ?: body
        }

        Log.d("Service", "toList=$toList subject=$subject body=$body")

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
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("SleepAlertService", "exactAlarm権限なし。通知で誘導します")
                notifyExactAlarmPermission()
                return
            }
        }

        Log.d("Alarm", "送信予定セット toList=$toList 間隔=${sendIntervalSec}秒")

        val warningInterval = sendIntervalSec - 10  // [変更] 送信10秒前に警告

        // ① 警告用
        val warningIntent = Intent(this, WarningReceiver::class.java).apply {
            putStringArrayListExtra("toList", ArrayList(toList))
            putExtra("subject", subject)
            putExtra("body", body)
        }
        val warningPendingIntent = PendingIntent.getBroadcast(
            this, 1, warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ② 送信用
        val sendIntent = Intent(this, SleepAlarmReceiver::class.java).apply {
            putStringArrayListExtra("toList", ArrayList(toList))
            putExtra("subject", subject)
            putExtra("body", body)
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + warningInterval * 1000L,
            warningPendingIntent
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + sendIntervalSec * 1000L,
            sendPendingIntent
        )
    }

    // [追加] 再送信アラームをセット（送信後に呼ぶ）
    @SuppressLint("ScheduleExactAlarm")
    fun setResendAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        Log.d("Alarm", "再送信アラームセット 間隔=${sendIntervalSec}秒")

        val warningInterval = sendIntervalSec - 10

        val warningIntent = Intent(this, WarningReceiver::class.java).apply {
            putStringArrayListExtra("toList", ArrayList(toList))
            putExtra("subject", subject)
            putExtra("body", body)
        }
        val warningPendingIntent = PendingIntent.getBroadcast(
            this, 1, warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sendIntent = Intent(this, SleepAlarmReceiver::class.java).apply {
            putStringArrayListExtra("toList", ArrayList(toList))
            putExtra("subject", subject)
            putExtra("body", body)
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + warningInterval * 1000L,
            warningPendingIntent
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + sendIntervalSec * 1000L,
            sendPendingIntent
        )
    }
    // [追加] ここまで

    // [追加] exactAlarm権限がない場合に設定画面へ誘導する通知を出す
    private fun notifyExactAlarmPermission() {
        val channelId = "alarm_permission_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "権限通知", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("権限が必要です")
            .setContentText("タップして「正確なアラームの設定」を許可してください")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(200, notification)
    }

    // [追加] ここまで
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

    // [追加] 送信時刻を記録
    fun updateLastSentTime() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit().putLong("last_sent", System.currentTimeMillis()).apply()
        Log.d("SleepAlertService", "last_sent更新")
    }
    // [追加] ここまで

    private fun saveSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit()
            .putString("to1", toList.getOrNull(0))
            .putString("to2", toList.getOrNull(1))
            .putString("to3", toList.getOrNull(2))
            .putStringSet("toList", toList.toSet())
            .putString("subject", subject)
            .putString("body", body)
            .apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)

        toList = listOf(
            prefs.getString("to1", "") ?: "",
            prefs.getString("to2", "") ?: "",
            prefs.getString("to3", "") ?: ""
        ).filter { it.isNotEmpty() }

        subject = prefs.getString("subject", "緊急連絡") ?: "緊急連絡"
        body = prefs.getString("body", "自動送信メッセージ") ?: "自動送信メッセージ"

        // [追加] 送信間隔を読み込む
        sendIntervalSec = prefs.getLong("send_interval_sec", 60L)
        Log.d("Service", "送信間隔: ${sendIntervalSec}秒")
        // [追加] ここまで
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
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