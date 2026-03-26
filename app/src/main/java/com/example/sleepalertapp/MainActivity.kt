package com.example.sleepalertapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

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

        editTextTo1.setText("ana05224@gmail.com")
        editTextTo2.setText("ana05224@nifty.com")
        editTextTo3.setText("sugar_lay_lenard@yahoo.co.jp")

        editTextSubject.setText("test")
        editTextBody.setText("This is test mail")

        buttonStart.setOnClickListener {
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                putExtra("to1", editTextTo1.text.toString())
                putExtra("to2", editTextTo2.text.toString())
                putExtra("to3", editTextTo3.text.toString())
                putExtra("subject", editTextSubject.text.toString())
                putExtra("body", editTextBody.text.toString())
            }
            startForegroundService(intent)

            // 👇追加（最重要）
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


        buttonStop.setOnClickListener {

            val intent = Intent(this, SleepMonitorService::class.java).apply {
            action = "STOP_MONITORING"
            }

            startService(intent)

            // Workerも止める
            WorkManager.getInstance(this)
            .cancelUniqueWork("check_worker")
            Toast.makeText(this, "監視を完全停止しました", Toast.LENGTH_SHORT).show()
         }

        buttonTestSend.setOnClickListener {

            val toList = listOf(
                editTextTo1.text.toString(),
                editTextTo2.text.toString(),
                editTextTo3.text.toString()
            ).filter { it.isNotBlank() }   // 空は除外

            val subject = editTextSubject.text.toString()
            val body = editTextBody.text.toString()

            EmailSender.sendMultiple(applicationContext, toList, subject, body)

            Toast.makeText(this, "テストメール送信中（複数）", Toast.LENGTH_SHORT).show()
        }

        buttonSelect1.setOnClickListener {
            openContactPicker(REQUEST_CONTACT_1)
        }

        buttonSelect2.setOnClickListener {
            openContactPicker(REQUEST_CONTACT_2)
        }

        buttonSelect3.setOnClickListener {
            openContactPicker(REQUEST_CONTACT_3)
        }
    }

    private fun openContactPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return

            val cursor = contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val email = it.getString(0)

                    when (requestCode) {
                        REQUEST_CONTACT_1 -> editTextTo1.setText(email)
                        REQUEST_CONTACT_2 -> editTextTo2.setText(email)
                        REQUEST_CONTACT_3 -> editTextTo3.setText(email)
                    }
                }
            }
        }
    }
}