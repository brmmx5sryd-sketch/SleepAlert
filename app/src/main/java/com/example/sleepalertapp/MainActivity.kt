//MainActivity.kt
package com.example.sleepalertapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.provider.ContactsContract
import android.Manifest
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var editTextTo1: EditText
    private lateinit var editTextTo2: EditText
    private lateinit var editTextTo3: EditText
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var buttonTestSend: Button
    private lateinit var buttonSelect1: Button
    private lateinit var buttonSelect2: Button
    private lateinit var buttonSelect3: Button
    private lateinit var buttonSendInterval: Button
    private lateinit var textViewSendInterval: TextView
    private lateinit var buttonSaveSettings: Button

    // [追加] 権限状態表示
    private lateinit var textViewBattery: TextView
    private lateinit var textViewBackground: TextView
    private lateinit var textViewAlarm: TextView

    private val REQUEST_CONTACT_1 = 101
    private val REQUEST_CONTACT_2 = 102
    private val REQUEST_CONTACT_3 = 103

    private val sendIntervalOptions = listOf(
        Pair("12時間", 12 * 60 * 60L),
        Pair("24時間", 24 * 60 * 60L),
        Pair("36時間", 36 * 60 * 60L),
        Pair("48時間", 48 * 60 * 60L),
        Pair("30分（テスト）", 30 * 60L)
    )

    private var selectedSendIntervalSec: Long = 24 * 60 * 60L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        editTextTo1 = findViewById(R.id.editTextTo1)
        editTextTo2 = findViewById(R.id.editTextTo2)
        editTextTo3 = findViewById(R.id.editTextTo3)
        editTextSubject = findViewById(R.id.editTextSubject)
        editTextBody = findViewById(R.id.editTextBody)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        buttonTestSend = findViewById(R.id.buttonTestSend)
        buttonSelect1 = findViewById(R.id.buttonSelect1)
        buttonSelect2 = findViewById(R.id.buttonSelect2)
        buttonSelect3 = findViewById(R.id.buttonSelect3)
        buttonSendInterval = findViewById(R.id.buttonSendInterval)
        textViewSendInterval = findViewById(R.id.textViewSendInterval)
        buttonSaveSettings = findViewById(R.id.buttonSaveSettings)

        // [追加] 権限状態表示のバインド
        textViewBattery = findViewById(R.id.textViewBattery)
        textViewBackground = findViewById(R.id.textViewBackground)
        textViewAlarm = findViewById(R.id.textViewAlarm)

        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        editTextTo1.setText(prefs.getString("to1", ""))
        editTextTo2.setText(prefs.getString("to2", ""))
        editTextTo3.setText(prefs.getString("to3", ""))
        editTextSubject.setText(prefs.getString("subject", ""))
        editTextBody.setText(prefs.getString("body", ""))

        selectedSendIntervalSec = prefs.getLong("send_interval_sec", 24 * 60 * 60L)
        updateSendIntervalText()

        // [追加] 起動時に権限状態を表示（アプリの進行には影響しない）
        updatePermissionStatus()

        buttonSendInterval.setOnClickListener {
            val options = sendIntervalOptions.map { it.first }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("メール送信時間を選択")
                .setItems(options) { _, which ->
                    selectedSendIntervalSec = sendIntervalOptions[which].second
                    updateSendIntervalText()
                }
                .show()
        }

        buttonSaveSettings.setOnClickListener {
            val workerThresholdSec = selectedSendIntervalSec + 60 * 60L

            prefs.edit()
                .putString("to1", editTextTo1.text.toString())
                .putString("to2", editTextTo2.text.toString())
                .putString("to3", editTextTo3.text.toString())
                .putStringSet("toList", listOf(
                    editTextTo1.text.toString(),
                    editTextTo2.text.toString(),
                    editTextTo3.text.toString()
                ).filter { it.isNotBlank() }.toSet())
                .putString("subject", editTextSubject.text.toString())
                .putString("body", editTextBody.text.toString())
                .putLong("send_interval_sec", selectedSendIntervalSec)
                .putLong("worker_threshold_sec", workerThresholdSec)
                .apply()

            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }

        buttonStart.setOnClickListener {

            // 権限不足でも進行は止めない・ダイアログも出さない
            startMonitoring()
        }

        buttonStop.setOnClickListener {
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                action = "STOP_MONITORING"
            }
            startService(intent)
            WorkManager.getInstance(this).cancelUniqueWork("check_worker")
            Toast.makeText(this, "監視を完全停止しました", Toast.LENGTH_SHORT).show()
        }

        buttonTestSend.setOnClickListener {
            val toList = listOf(
                editTextTo1.text.toString(),
                editTextTo2.text.toString(),
                editTextTo3.text.toString()
            ).filter { it.isNotBlank() }
            val subject = editTextSubject.text.toString()
            val body = editTextBody.text.toString()
            EmailSender.sendMultiple(applicationContext, toList, subject, body)
            Toast.makeText(this, "テストメール送信中（複数）", Toast.LENGTH_SHORT).show()
        }

        buttonSelect1.setOnClickListener { openContactPicker(REQUEST_CONTACT_1) }
        buttonSelect2.setOnClickListener { openContactPicker(REQUEST_CONTACT_2) }
        buttonSelect3.setOnClickListener { openContactPicker(REQUEST_CONTACT_3) }
    }

    // [追加] 権限状態を表示する（アプリの進行には影響しない）
    private fun updatePermissionStatus() {
        val batteryOk = PermissionHelper.isBatteryOptimizationIgnored(this)
        val alarmOk = PermissionHelper.isExactAlarmAllowed(this)

        textViewBattery.text = if (batteryOk) {
            "✅ [バッテリー] 設定済"
        } else {
            "❌ [バッテリー] 未設定"
        }

        // バックグラウンドはAPIで確認できないため常に案内
        textViewBackground.text = "⚠️ [バックグラウンド] アプリ設定で「制限なし」を確認してください"

        textViewAlarm.text = if (alarmOk) {
            "✅ [アラーム権限] 設定済"
        } else {
            "❌ [アラーム権限] 未設定"
        }
    }
    // [追加] ここまで

    private fun startMonitoring() {
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        val to1 = editTextTo1.text.toString()
        val to2 = editTextTo2.text.toString()
        val to3 = editTextTo3.text.toString()
        val sendIntervalSec = prefs.getLong("send_interval_sec", 24 * 60 * 60L)
        val workerThresholdSec = prefs.getLong("worker_threshold_sec", 25 * 60 * 60L)
        val toList = listOf(to1, to2, to3).filter { it.isNotBlank() }

        Log.d("StartConfig", "=== 監視開始 設定確認 ===")
        Log.d("StartConfig", "【SleepManager】")
        Log.d("StartConfig", "  メール送信待機時間  : ${sendIntervalSec}秒 (${sendIntervalSec / 3600}時間${(sendIntervalSec % 3600) / 60}分)")
        Log.d("StartConfig", "  警告アラーム発火    : 送信10秒前 → ${sendIntervalSec - 10}秒後")
        Log.d("StartConfig", "【Worker】")
        Log.d("StartConfig", "  送信閾値            : ${workerThresholdSec}秒 (${workerThresholdSec / 3600}時間${(workerThresholdSec % 3600) / 60}分)")
        Log.d("StartConfig", "  監視サイクル        : 15分ごと")
        Log.d("StartConfig", "【メール送信先】")
        toList.forEachIndexed { index, email ->
            Log.d("StartConfig", "  宛先${index + 1}: $email")
        }
        Log.d("StartConfig", "========================")

        val intent = Intent(this, SleepMonitorService::class.java).apply {
            putExtra("to1", to1)
            putExtra("to2", to2)
            putExtra("to3", to3)
            putExtra("subject", editTextSubject.text.toString())
            putExtra("body", editTextBody.text.toString())
        }
        startForegroundService(intent)

        val workRequest = PeriodicWorkRequestBuilder<CheckWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "check_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Toast.makeText(this, "常駐監視を開始しました", Toast.LENGTH_SHORT).show()
    }

    private fun updateSendIntervalText() {
        val label = sendIntervalOptions.find { it.second == selectedSendIntervalSec }?.first ?: "未設定"
        textViewSendInterval.text = label
    }

    private fun openContactPicker(requestCode: Int) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CONTACTS), 100
            )
            return
        }

        val contacts = mutableListOf<Pair<String, String>>()
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                if (!name.isNullOrBlank()) contacts.add(Pair(name, id))
            }
        }

        if (contacts.isEmpty()) {
            Toast.makeText(this, "連絡先がありません", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("連絡先を選択")
            .setItems(contacts.map { it.first }.toTypedArray()) { _, which ->
                showEmailDialog(requestCode, contacts[which].second)
            }
            .show()
    }

    private fun showEmailDialog(requestCode: Int, contactId: String) {
        val emails = mutableListOf<String>()
        val emailCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.DATA),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )
        emailCursor?.use {
            while (it.moveToNext()) {
                val email = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DATA)
                )
                if (!email.isNullOrBlank()) emails.add(email)
            }
        }

        if (emails.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("メールアドレスなし")
                .setMessage("この連絡先にはメールアドレスが登録されていません")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("メールアドレスを選択")
                .setItems(emails.toTypedArray()) { _, which ->
                    setEmail(requestCode, emails[which])
                }
                .show()
        }
    }

    private fun setEmail(requestCode: Int, email: String) {
        when (requestCode) {
            REQUEST_CONTACT_1 -> editTextTo1.setText(email)
            REQUEST_CONTACT_2 -> editTextTo2.setText(email)
            REQUEST_CONTACT_3 -> editTextTo3.setText(email)
        }
    }
}