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
    private var isReceiverRegistered = false
    private var sendIntervalSec: Long = 60L

    private var isBatteryReceiverRegistered = false
    private val batteryReceiver = BatteryReceiver()

    private var screenOnTimeoutSec: Long = 180L
    private var pendingActiveUpdate = false
    private val handler = Handler(Looper.getMainLooper())
    private val confirmUserActive = Runnable {
        Log.d("SleepAlertService", "タイムアウト経過、last_active更新")
        pendingActiveUpdate = false
        updateLastActiveTime()
        cancelSleepAlarm()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    UserInteractionAccessibilityService.isScreenOn = true
                    // [修正] 画面ONでタップフラグをリセット
                    pendingActiveUpdate = false
                    Log.d("SleepAlertService", "画面ON、タップフラグリセット（false）")
                    // [screenOnTimeout: 復活時はコメント解除し、上2行を削除]
                    // handler.removeCallbacks(confirmUserActive)
                    // handler.postDelayed(confirmUserActive, screenOnTimeoutSec * 1000L)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    UserInteractionAccessibilityService.isScreenOn = false
                    handler.removeCallbacks(confirmUserActive)
                    // [修正] タップフラグがtrueの場合のみlast_active更新・アラームリセット
                    if (pendingActiveUpdate) {
                        pendingActiveUpdate = false
                        updateLastActiveTime()
                        setSleepAlarm()
                        Log.d("SleepAlertService", "画面OFF（タップあり）→ last_active更新・アラームリセット")
                    } else {
                        Log.d("SleepAlertService", "画面OFF（タップなし）→ last_active更新なし・アラーム継続")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        getSharedPreferences("monitor", MODE_PRIVATE)
            .edit()
            .putBoolean("service_alive", true)
            .apply()

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

        if (!isBatteryReceiverRegistered) {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, batteryFilter)
            isBatteryReceiverRegistered = true
            Log.d("SleepMonitorService", "BatteryReceiver登録完了")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.d("Service", "監視中")

        if (intent?.action == "STOP_MONITORING") {
            stopMonitoring()
            return START_NOT_STICKY
        }

        if (intent?.action == "SET_RESEND_ALARM") {
            loadSettings()
            setResendAlarm()
            return START_STICKY
        }

        // [修正] タッチ操作検知：タップフラグをtrueにセットするのみ
        if (intent?.action == "USER_INTERACTION_DETECTED") {
            if (!pendingActiveUpdate) {
                pendingActiveUpdate = true
                Log.d("SleepMonitorService", "タッチ検知 → タップフラグON（true）")
            }
            return START_STICKY
        }

        loadSettings()

        val to1 = intent?.getStringExtra("to1")
        val to2 = intent?.getStringExtra("to2")
        val to3 = intent?.getStringExtra("to3")

        val isUserStart = to1 != null || to2 != null || to3 != null

        if (isUserStart) {
            toList = listOf(to1 ?: "", to2 ?: "", to3 ?: "").filter { it.isNotEmpty() }
            val senderName  = intent?.getStringExtra("sender_name")
            val senderPhone = intent?.getStringExtra("sender_phone")
            if (senderName != null) {
                getSharedPreferences("monitor", MODE_PRIVATE).edit()
                    .putString("sender_name", senderName)
                    .putString("sender_phone", senderPhone ?: "")
                    .apply()
            }
            updateLastActiveTime()
            Log.d("SleepMonitorService", "ユーザー操作による起動、last_active更新")
        } else {
            Log.d("SleepMonitorService", "再起動等による起動、last_active更新しない")
        }

        AppLog.d("Service", "toList=$toList")

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
            .setSmallIcon(R.drawable.ic_monitoring_handshake_on)
            .build()
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarms() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("SleepAlertService", "exactAlarm権限なし")
                notifyExactAlarmPermission()
                return
            }
        }

        val now = System.currentTimeMillis()
        val isTestMode = sendIntervalSec == 30 * 60L

        cancelWarningAlarms(alarmManager)

        if (isTestMode) {
            setWarningAlarm(alarmManager, now, sendIntervalSec - 10 * 60L, 12, "10分後に緊急メールを送信します。\nスマートフォンを操作してください。")
        } else {
            setWarningAlarm(alarmManager, now, sendIntervalSec - 6 * 60 * 60L,  10, "6時間後に緊急メールを送信します。\nスマートフォンを操作してください。")
            setWarningAlarm(alarmManager, now, sendIntervalSec - 3 * 60 * 60L,  11, "3時間後に緊急メールを送信します。\nスマートフォンを操作してください。")
            setWarningAlarm(alarmManager, now, sendIntervalSec - 30 * 60L,      12, "30分後に緊急メールを送信します。\nスマートフォンを操作してください。")
        }

        val sendIntent = Intent(this, SleepAlarmReceiver::class.java).apply {
            putStringArrayListExtra("toList", ArrayList(toList))
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + sendIntervalSec * 1000L,
            sendPendingIntent
        )

        Log.d("Alarm", "アラームセット完了 間隔=${sendIntervalSec}秒 テスト=$isTestMode")
    }

    private fun setWarningAlarm(
        alarmManager: AlarmManager,
        now: Long,
        offsetSec: Long,
        requestCode: Int,
        message: String
    ) {
        if (offsetSec <= 0) return

        val warningIntent = Intent(this, WarningReceiver::class.java).apply {
            putExtra("message", message)
            putExtra("notification_id", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + offsetSec * 1000L,
            pendingIntent
        )
        Log.d("Alarm", "警告アラーム requestCode=$requestCode ${offsetSec/60}分後")
    }

    private fun cancelWarningAlarms(alarmManager: AlarmManager) {
        listOf(10, 11, 12).forEach { code ->
            val intent = PendingIntent.getBroadcast(
                this, code, Intent(this, WarningReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(intent)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setSleepAlarm() { scheduleAlarms() }

    @SuppressLint("ScheduleExactAlarm")
    fun setResendAlarm() { scheduleAlarms() }

    private fun notifyExactAlarmPermission() {
        val channelId = "alarm_permission_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "権限通知", NotificationManager.IMPORTANCE_HIGH)
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

    private fun cancelSleepAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        cancelWarningAlarms(alarmManager)
        val sendPendingIntent = PendingIntent.getBroadcast(
            this, 2, Intent(this, SleepAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(sendPendingIntent)
        Log.d("SleepAlertService", "全アラームキャンセル")
    }

    private fun updateLastActiveTime() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit().putLong("last_active", System.currentTimeMillis()).apply()
    }

    fun updateLastSentTime() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit().putLong("last_sent", System.currentTimeMillis()).apply()
        Log.d("SleepAlertService", "last_sent更新")
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        prefs.edit()
            .putString("to1", toList.getOrNull(0))
            .putString("to2", toList.getOrNull(1))
            .putString("to3", toList.getOrNull(2))
            .putStringSet("toList", toList.toSet())
            .apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        toList = listOf(
            prefs.getString("to1", "") ?: "",
            prefs.getString("to2", "") ?: "",
            prefs.getString("to3", "") ?: ""
        ).filter { it.isNotEmpty() }
        sendIntervalSec = prefs.getLong("send_interval_sec", 60L)
        screenOnTimeoutSec = prefs.getLong("screen_on_timeout_sec", 180L)
        AppLog.d("Service", "送信間隔: ${sendIntervalSec}秒、画面ONタイムアウト: ${screenOnTimeoutSec}秒")
    }

    override fun onDestroy() {
        handler.removeCallbacks(confirmUserActive)
        pendingActiveUpdate = false

        super.onDestroy()

        getSharedPreferences("monitor", MODE_PRIVATE)
            .edit()
            .putBoolean("service_alive", false)
            .apply()

        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                Log.w("SleepMonitorService", "BatteryReceiver unregister失敗", e)
            }
            isBatteryReceiverRegistered = false
            Log.d("SleepMonitorService", "BatteryReceiver解除完了")
        }

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w("SleepMonitorService", "screenReceiver unregister失敗", e)
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