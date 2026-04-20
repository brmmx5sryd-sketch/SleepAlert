//MailTemplate.kt
package com.example.sleepalertapp

object MailTemplate {

    fun buildSubject(name: String): String {
        return "【緊急】【自動送信】${name}さんの安否を確認してください"
    }

    fun buildBody(name: String, phone: String, sleepLabel: String): String {
        return """
このメールは${name}さんのスマートフォンから自動送信しています。
${name}さんは${sleepLabel}、スマートフォンを起動していません。
すみやかに電話などで状況を確認してください。${phone}
        """.trimIndent()
    }
}