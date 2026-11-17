package com.example.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ObdApplication
import com.example.R
import com.example.obd.i18n.AppLocalization
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
        val repo = (application as ObdApplication).telemetryRepository
        val initCtx = AppLocalization.localizedContext(this, repo.settings.value.appLanguage)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(initCtx, initCtx.getString(R.string.fg_connecting))
        )
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                val userSettings = repo.settings.value
                val isEng = userSettings.isEnglish
                val l10nCtx = AppLocalization.localizedContext(this@ObdForegroundService, userSettings.appLanguage)
                val conn = repo.connectionState.value
                val readings = repo.readings.value
                val rpmUnit = ObdPid.ENGINE_RPM.localizedUnit(isEng)
                val speedUnit = ObdPid.VEHICLE_SPEED.localizedUnit(isEng)
                val rpm = readings[ObdPid.ENGINE_RPM.modeAndPid]?.let { "${it.value.toInt()} $rpmUnit" } ?: "--"
                val speed = readings[ObdPid.VEHICLE_SPEED.modeAndPid]?.let { "${it.value.toInt()} $speedUnit" } ?: "--"
                val temp = readings[ObdPid.COOLANT_TEMP.modeAndPid]?.let {
                    "${ObdPid.COOLANT_TEMP.formatValue(it.value, userSettings.useFahrenheit, userSettings.pressureUnit, isEng)}${ObdPid.COOLANT_TEMP.displayUnit(userSettings.useFahrenheit, userSettings.pressureUnit, isEng)}"
                } ?: "--"

                val statusText = when (conn) {
                    is ObdConnectionState.Connected ->
                        l10nCtx.getString(R.string.fg_conn_line, conn.deviceName, rpm, speed, temp)
                    is ObdConnectionState.Simulation ->
                        l10nCtx.getString(R.string.fg_sim_line, rpm, speed, temp)
                    is ObdConnectionState.Connecting ->
                        "${conn.deviceName}: ${conn.step}"
                    else -> l10nCtx.getString(R.string.fg_waiting)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(l10nCtx, statusText))
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
            val repo = (application as ObdApplication).telemetryRepository
            val l10nCtx = AppLocalization.localizedContext(this, repo.settings.value.appLanguage)
            val channel = NotificationChannel(
                CHANNEL_ID,
                l10nCtx.getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = l10nCtx.getString(R.string.fg_channel_desc)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(l10nCtx: Context, text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(l10nCtx.getString(R.string.fg_notif_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()
    }
}
