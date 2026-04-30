//AppLog.kt
package com.example.sleepalertapp

import android.util.Log

object AppLog {
    // ★ここを切り替えるだけ★
    // デバッグ時: true  リリース時: false
    private const val ENABLED = false

    fun d(tag: String, msg: String) { if (ENABLED) Log.d(tag, msg) }
    fun w(tag: String, msg: String) { if (ENABLED) Log.w(tag, msg) }
    fun e(tag: String, msg: String) { if (ENABLED) Log.e(tag, msg) }
}