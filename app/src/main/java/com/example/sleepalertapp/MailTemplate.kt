//MailTemplate.kt
package com.example.sleepalertapp

object MailTemplate {

    // [デバッグ用] 警告通知と同時にメールも送信する
    // リリース時はこの1行をコメントアウトする
    const val DEBUG_SEND_ON_WARNING = true

    fun buildSubject(name: String): String {
        return "【緊急】【自動送信】${name}さんの安否を確認してください"
    }

    fun buildBody(name: String, phone: String, sleepLabel: String): String {
        // [修正 #7] 電話番号を独立した行に移動し、読みやすく整形
        return """
このメールは${name}さんのスマートフォンから自動送信しています。
${name}さんは${sleepLabel}、スマートフォンを起動していません。
すみやかに電話などで状況を確認してください。
連絡先電話番号: ${phone}
        """.trimIndent()
    }
}
