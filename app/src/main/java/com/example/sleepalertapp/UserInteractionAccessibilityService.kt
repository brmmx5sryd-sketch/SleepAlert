package com.example.sleepalertapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class UserInteractionAccessibilityService : AccessibilityService() {

    companion object {
        var isScreenOn: Boolean = false
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        AppLog.d("AccessibilityService", "接続完了")  // 起動時のみ → 常に出力でも問題ない頻度
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScreenOn) return

        val prefs = getSharedPreferences("monitor", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_alive", false)) return

        // スクロール中などに連続発火する可能性があるため AppLog（抑制可能）に変更
        AppLog.d("AccessibilityService", "ユーザー操作検知 → last_active更新")
        prefs.edit().putLong("last_active", System.currentTimeMillis()).apply()

        val intent = Intent(this, SleepMonitorService::class.java).apply {
            action = "USER_INTERACTION_DETECTED"
        }
        startService(intent)
    }

    override fun onInterrupt() {}
}