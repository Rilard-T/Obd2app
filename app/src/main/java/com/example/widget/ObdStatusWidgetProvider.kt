package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

data class ObdWidgetSnapshot(
    val vehicleName: String = "OBD2 Авто",
    val statusLabel: String = "ОФЛАЙН",
    val statusColorHex: Int = Color.parseColor("#94A3B8"),
    val primaryTelemetry: String = "ОЖ -- °C  •  -- В",
    val lastTripLine: String = "Поездка: --",
    val maintenanceLine: String = "ТО: --",
    val dtcCount: Int = 0
)

class ObdStatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = loadSnapshot(context)
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId, snapshot)
        }
    }

    companion object {
        private const val PREFS_NAME = "obd_widget_prefs"
        private const val KEY_VEHICLE = "vehicle"
        private const val KEY_STATUS = "status"
        private const val KEY_STATUS_COLOR = "status_color"
        private const val KEY_TELEMETRY = "telemetry"
        private const val KEY_TRIP = "trip"
        private const val KEY_MAINT = "maint"
        private const val KEY_DTC = "dtc"

        fun pushSnapshot(context: Context, snapshot: ObdWidgetSnapshot) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_VEHICLE, snapshot.vehicleName)
                .putString(KEY_STATUS, snapshot.statusLabel)
                .putInt(KEY_STATUS_COLOR, snapshot.statusColorHex)
                .putString(KEY_TELEMETRY, snapshot.primaryTelemetry)
                .putString(KEY_TRIP, snapshot.lastTripLine)
                .putString(KEY_MAINT, snapshot.maintenanceLine)
                .putInt(KEY_DTC, snapshot.dtcCount)
                .apply()

            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, ObdStatusWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                for (id in ids) {
                    updateSingleWidget(context, manager, id, snapshot)
                }
            }
        }

        private fun loadSnapshot(context: Context): ObdWidgetSnapshot {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ObdWidgetSnapshot(
                vehicleName = prefs.getString(KEY_VEHICLE, "OBD2 Авто") ?: "OBD2 Авто",
                statusLabel = prefs.getString(KEY_STATUS, "ОФЛАЙН") ?: "ОФЛАЙН",
                statusColorHex = prefs.getInt(KEY_STATUS_COLOR, Color.parseColor("#94A3B8")),
                primaryTelemetry = prefs.getString(KEY_TELEMETRY, "ОЖ -- °C  •  -- В") ?: "ОЖ -- °C  •  -- В",
                lastTripLine = prefs.getString(KEY_TRIP, "Поездка: --") ?: "Поездка: --",
                maintenanceLine = prefs.getString(KEY_MAINT, "ТО: --") ?: "ТО: --",
                dtcCount = prefs.getInt(KEY_DTC, 0)
            )
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            snapshot: ObdWidgetSnapshot
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_obd_status)
            views.setTextViewText(R.id.widget_vehicle_name, snapshot.vehicleName)
            views.setTextViewText(R.id.widget_status_pill, snapshot.statusLabel)
            views.setTextColor(R.id.widget_status_pill, snapshot.statusColorHex)
            views.setTextViewText(R.id.widget_telemetry_primary, snapshot.primaryTelemetry)
            views.setTextViewText(R.id.widget_last_trip, snapshot.lastTripLine)
            views.setTextViewText(R.id.widget_next_maintenance, snapshot.maintenanceLine)
            views.setTextViewText(R.id.widget_dtc_badge, "DTC: ${snapshot.dtcCount}")
            views.setTextColor(
                R.id.widget_dtc_badge,
                if (snapshot.dtcCount > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
