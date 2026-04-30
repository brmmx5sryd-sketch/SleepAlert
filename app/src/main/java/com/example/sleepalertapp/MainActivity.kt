//MainActivity.kt
package com.example.sleepalertapp

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var editTextSenderName: EditText
    private lateinit var editTextSenderPhone: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var buttonTestSend: Button
    private lateinit var buttonSelect1: Button
    private lateinit var buttonSelect2: Button
    private lateinit var buttonSelect3: Button
    private lateinit var buttonSendInterval: Button
    private lateinit var textViewSendInterval: TextView
    private lateinit var buttonSaveSettings: Button
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

    companion object {
        const val KEY_SETTINGS_SAVED = "settings_saved"
        const val KEY_TEST_SEND_SUCCESS = "test_send_success"
    }

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
        editTextSenderName  = findViewById(R.id.editTextSenderName)
        editTextSenderPhone = findViewById(R.id.editTextSenderPhone)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        buttonTestSend = findViewById(R.id.buttonTestSend)
        buttonSelect1 = findViewById(R.id.buttonSelect1)
        buttonSelect2 = findViewById(R.id.buttonSelect2)
        buttonSelect3 = findViewById(R.id.buttonSelect3)
        buttonSendInterval = findViewById(R.id.buttonSendInterval)
        textViewSendInterval = findViewById(R.id.textViewSendInterval)
        buttonSaveSettings = findViewById(R.id.buttonSaveSettings)
        textViewBattery = findViewById(R.id.textViewBattery)
        textViewBackground = findViewById(R.id.textViewBackground)
        textViewAlarm = findViewById(R.id.textViewAlarm)

        val prefs = getSharedPreferences("monitor", MODE_PRIVATE)
        editTextTo1.setText(prefs.getString("to1", ""))
        editTextTo2.setText(prefs.getString("to2", ""))
        editTextTo3.setText(prefs.getString("to3", ""))
        editTextSenderName.setText(prefs.getString("sender_name", ""))
        editTextSenderPhone.setText(prefs.getString("sender_phone", ""))

        selectedSendIntervalSec = prefs.getLong("send_interval_sec", 24 * 60 * 60L)
        updateSendIntervalText()
        updatePermissionStatus()

        val addressWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit()
                    .putBoolean(KEY_TEST_SEND_SUCCESS, false)
                    .putBoolean(KEY_SETTINGS_SAVED, false)
                    .apply()
            }
        }
        editTextTo1.addTextChangedListener(addressWatcher)
        editTextTo2.addTextChangedListener(addressWatcher)
        editTextTo3.addTextChangedListener(addressWatcher)

        val senderInfoWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putBoolean(KEY_SETTINGS_SAVED, false).apply()
            }
        }
        editTextSenderName.addTextChangedListener(senderInfoWatcher)
        editTextSenderPhone.addTextChangedListener(senderInfoWatcher)

        buttonSendInterval.setOnClickListener {
            val options = sendIntervalOptions.map { it.first }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("メール送信時間を選択")
                .setItems(options) { _, which ->
                    selectedSendIntervalSec = sendIntervalOptions[which].second
                    updateSendIntervalText()
                    prefs.edit().putBoolean(KEY_SETTINGS_SAVED, false).apply()
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
                .putString("sender_name",  editTextSenderName.text.toString())
                .putString("sender_phone", editTextSenderPhone.text.toString())
                .putLong("send_interval_sec", selectedSendIntervalSec)
                .putLong("worker_threshold_sec", workerThresholdSec)
                .putBoolean(KEY_SETTINGS_SAVED, true)
                .apply()
            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }

        buttonStart.setOnClickListener {
            if (!prefs.getBoolean(KEY_SETTINGS_SAVED, false)) {
                AlertDialog.Builder(this)
                    .setTitle("設定が保存されていません")
                    .setMessage("「設定を保存」ボタンを押してから監視を開始してください。")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            if (!isMailInputValid()) return@setOnClickListener

            if (!prefs.getBoolean(KEY_TEST_SEND_SUCCESS, false)) {
                AlertDialog.Builder(this)
                    .setTitle("テスト送信が完了していません")
                    .setMessage("「テスト送信」ボタンを押して送信を確認してから監視を開始してください。")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            startMonitoring()
            setLauncherIcon(true)  // 監視中アイコンに切り替え
        }

        buttonStop.setOnClickListener {
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                action = "STOP_MONITORING"
            }
            startService(intent)
            WorkManager.getInstance(this).cancelUniqueWork("check_worker")
            Toast.makeText(this, "監視を完全停止しました", Toast.LENGTH_SHORT).show()
            setLauncherIcon(false)  // 停止中アイコンに切り替え
        }

        buttonTestSend.setOnClickListener {
            if (!isMailInputValid()) return@setOnClickListener

            val toList = listOf(
                editTextTo1.text.toString(),
                editTextTo2.text.toString(),
                editTextTo3.text.toString()
            ).filter { it.isNotBlank() }

            val name  = editTextSenderName.text.toString()
            val phone = editTextSenderPhone.text.toString()
            val subject = MailTemplate.buildSubject(name)
            val body    = MailTemplate.buildBody(name, phone, "テスト")

            EmailSender.sendMultiple(
                applicationContext,
                toList,
                subject,
                body,
                onAllSuccess = {
                    runOnUiThread {
                        prefs.edit().putBoolean(KEY_TEST_SEND_SUCCESS, true).apply()
                        Toast.makeText(
                            this,
                            "テストメール送信成功。監視を開始できます。",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onAnyFailure = { failedTo, _ ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "送信失敗: $failedTo\nメールアドレスや接続を確認してください。",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            Toast.makeText(this, "テストメール送信中...", Toast.LENGTH_SHORT).show()
        }

        buttonSelect1.setOnClickListener { openContactPicker(REQUEST_CONTACT_1) }
        buttonSelect2.setOnClickListener { openContactPicker(REQUEST_CONTACT_2) }
        buttonSelect3.setOnClickListener { openContactPicker(REQUEST_CONTACT_3) }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    // ランチャーアイコンを切り替える
    private fun setLauncherIcon(isMonitoring: Boolean) {
        val pm = packageManager
        val onAlias  = ComponentName(this, "com.example.sleepalertapp.MainActivityMonitoring")
        val offAlias = ComponentName(this, "com.example.sleepalertapp.MainActivityDefault")

        if (isMonitoring) {
            pm.setComponentEnabledSetting(onAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(offAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        } else {
            pm.setComponentEnabledSetting(onAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(offAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
        }
    }

    private fun isMailInputValid(): Boolean {
        val to1 = editTextTo1.text.toString()
        val to2 = editTextTo2.text.toString()
        val to3 = editTextTo3.text.toString()

        if (listOf(to1, to2, to3).all { it.isBlank() }) {
            AlertDialog.Builder(this)
                .setTitle("送信先が入力されていません")
                .setMessage("送信先メールアドレスを1件以上入力してください。")
                .setPositiveButton("OK", null)
                .show()
            return false
        }
        if (editTextSenderName.text.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("名前が入力されていません")
                .setMessage("あなたの名前を入力してください。")
                .setPositiveButton("OK", null).show()
            return false
        }
        if (editTextSenderPhone.text.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("電話番号が入力されていません")
                .setMessage("電話番号を入力してください。")
                .setPositiveButton("OK", null).show()
            return false
        }
        return true
    }

    private fun updatePermissionStatus() {
        val batteryOk = PermissionHelper.isBatteryOptimizationIgnored(this)
        val alarmOk = PermissionHelper.isExactAlarmAllowed(this)

        textViewBattery.text = if (batteryOk) "✅ [バッテリー] 設定済" else "❌ [バッテリー] 未設定"
        textViewAlarm.text   = if (alarmOk)   "✅ [アラーム権限] 設定済" else "❌ [アラーム権限] 未設定"
        textViewBackground.text = "⚠️ [バックグラウンド] アプリ設定で「制限なし」を確認してください"
    }

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
        Log.d("StartConfig", "========================")

        val intent = Intent(this, SleepMonitorService::class.java).apply {
            putExtra("to1", to1)
            putExtra("to2", to2)
            putExtra("to3", to3)
            putExtra("sender_name",  editTextSenderName.text.toString())
            putExtra("sender_phone", editTextSenderPhone.text.toString())
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