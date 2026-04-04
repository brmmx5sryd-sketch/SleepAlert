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

    // [追加] Worker設定UI
    private lateinit var editTextWorkerInterval: EditText
    private lateinit var editTextThreshold: EditText
    private lateinit var switchTestMode: Switch
    private lateinit var buttonSaveWorkerSettings: Button
    // [追加] ここまで

    private val REQUEST_CONTACT_1 = 101
    private val REQUEST_CONTACT_2 = 102
    private val REQUEST_CONTACT_3 = 103

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

        // [追加] Worker設定UIのバインド
        editTextWorkerInterval = findViewById(R.id.editTextWorkerInterval)
        editTextThreshold = findViewById(R.id.editTextThreshold)
        switchTestMode = findViewById(R.id.switchTestMode)
        buttonSaveWorkerSettings = findViewById(R.id.buttonSaveWorkerSettings)
        // [追加] ここまで

        editTextTo1.setText("ana05224@gmail.com")
        editTextTo2.setText("ana05224@nifty.com")
        editTextTo3.setText("sugar_lay_lenard@yahoo.co.jp")
        editTextSubject.setText("test")
        editTextBody.setText("This is test mail")

        // [追加] 保存済みWorker設定を復元
        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        editTextWorkerInterval.setText(prefs.getLong("worker_interval", 15L).toString())
        editTextThreshold.setText(prefs.getLong("threshold_value", 24L).toString())
        switchTestMode.isChecked = prefs.getBoolean("test_mode", false)
        // [追加] ここまで

        buttonStart.setOnClickListener {
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                putExtra("to1", editTextTo1.text.toString())
                putExtra("to2", editTextTo2.text.toString())
                putExtra("to3", editTextTo3.text.toString())
                putExtra("subject", editTextSubject.text.toString())
                putExtra("body", editTextBody.text.toString())
            }
            startForegroundService(intent)

            // [変更] Worker間隔をSharedPrefsから読んで反映
            val interval = prefs.getLong("worker_interval", 15L)
            val workRequest = PeriodicWorkRequestBuilder<CheckWorker>(
                interval, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "check_worker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Toast.makeText(this, "常駐監視を開始しました（Worker間隔: ${interval}分）", Toast.LENGTH_SHORT).show()
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

        // [追加] Worker設定の保存ボタン
        buttonSaveWorkerSettings.setOnClickListener {
            val interval = editTextWorkerInterval.text.toString().toLongOrNull()
            val threshold = editTextThreshold.text.toString().toLongOrNull()
            val testMode = switchTestMode.isChecked

            if (interval == null || interval < 15) {
                Toast.makeText(this, "Worker間隔は15分以上で入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (threshold == null || threshold < 1) {
                Toast.makeText(this, "閾値は1以上で入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putLong("worker_interval", interval)
                .putLong("threshold_value", threshold)
                .putBoolean("test_mode", testMode)
                .apply()

            val unit = if (testMode) "分" else "時間"
            Toast.makeText(
                this,
                "保存しました（間隔: ${interval}分 / 閾値: ${threshold}${unit}）",
                Toast.LENGTH_SHORT
            ).show()
        }
        // [追加] ここまで
    }

    // openContactPicker・showEmailDialog・setEmail は変更なし
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