package com.example.sleepalertapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var editTextTo: EditText
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var buttonTestSend: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        editTextTo = findViewById(R.id.editTextTo)
        editTextSubject = findViewById(R.id.editTextSubject)
        editTextBody = findViewById(R.id.editTextBody)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        buttonTestSend = findViewById(R.id.buttonTestSend)

        editTextTo.setText("ana05224@gmail.com")
        editTextSubject.setText("test")
        editTextBody.setText("This is test mail")

        buttonStart.setOnClickListener {
            val intent = Intent(this, SleepMonitorService::class.java).apply {
                putExtra("to", editTextTo.text.toString())
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
            val to = editTextTo.text.toString()
            val subject = editTextSubject.text.toString()
            val body = editTextBody.text.toString()

            EmailSender.send(applicationContext, to, subject, body)
            Toast.makeText(this, "テストメール送信中...", Toast.LENGTH_SHORT).show()
        }
    }
}