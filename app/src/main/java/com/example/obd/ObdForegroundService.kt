package com.example.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ObdApplication
import com.example.R
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ObdForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "obd_telemetry_channel"
        private const val NOTIFICATION_ID = 4102
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к OBD2..."))
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val repo = (application as ObdApplication).telemetryRepository
            while (isActive) {
                val conn = repo.connectionState.value
                val readings = repo.readings.value
                val rpm = readings[ObdPid.ENGINE_RPM]?.let { "${it.value.toInt()} об/мин" } ?: "--"
                val speed = readings[ObdPid.VEHICLE_SPEED]?.let { "${it.value.toInt()} км/ч" } ?: "--"
                val temp = readings[ObdPid.COOLANT_TEMP]?.let { "${it.value.toInt()}°C" } ?: "--"

                val statusText = when (conn) {
                    is ObdConnectionState.Connected ->
                        "${conn.deviceName}: $rpm • $speed • ОЖ $temp"
                    is ObdConnectionState.Simulation ->
                        "Симуляция: $rpm • $speed • ОЖ $temp"
                    is ObdConnectionState.Connecting ->
                        "${conn.deviceName}: ${conn.step}"
                    else -> "Ожидание подключения OBD2"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
                delay(1500L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Телеметрия OBD2",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поддержание связи с OBD2 адаптером для Android Auto"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OBD2 Авто • Телеметрия активна")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()
    }
}
