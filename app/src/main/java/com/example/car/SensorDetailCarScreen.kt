package com.example.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.ObdApplication
import com.example.R
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SensorDetailCarScreen(
    carContext: CarContext,
    private val sensorId: String
) : Screen(carContext) {

    private val repository = (carContext.applicationContext as ObdApplication).telemetryRepository
    private var lastInvalidateMs: Long = 0L

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    combine(
                        repository.readings,
                        repository.settings,
                        repository.sensors
                    ) { _, _, _ -> Unit }.collectLatest {
                        val now = System.currentTimeMillis()
                        if (now - lastInvalidateMs >= 500L) {
                            lastInvalidateMs = now
                            invalidate()
                        }
                    }
                }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val settings = repository.settings.value
        val isEng = settings.isEnglish
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val sensor = repository.sensors.value.find { it.id == sensorId }
            ?: ObdPid.ENGINE_RPM.toTelemetrySensor(isEng)
        val reading = repository.readings.value[sensor.id]
        val unit = sensor.displayUnit(settings.useFahrenheit, settings.pressureUnit, isEng)

        val currentText = reading?.let {
            "${sensor.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit, isEng)} $unit"
        } ?: l10nCtx.getString(R.string.label_waiting_data)

        val minMaxText = reading?.let {
            val minStr = sensor.formatValue(it.minValue, settings.useFahrenheit, settings.pressureUnit, isEng)
            val maxStr = sensor.formatValue(it.maxValue, settings.useFahrenheit, settings.pressureUnit, isEng)
            l10nCtx.getString(R.string.car_detail_minmax, minStr, unit, maxStr)
        } ?: l10nCtx.getString(
            R.string.car_detail_range,
            sensor.minRange.toInt(),
            sensor.maxRange.toInt(),
            unit
        )

        val statusText = when (reading?.alertLevel) {
            AlertLevel.CRITICAL -> l10nCtx.getString(R.string.car_status_crit)
            AlertLevel.WARNING -> l10nCtx.getString(R.string.car_status_warn)
            else -> l10nCtx.getString(R.string.car_status_ok)
        }

        val isPinned = sensor.id in settings.androidAutoSensorIds
        val gaugeIcon = CarGaugeBitmapFactory.createGaugeIcon(
            sensor = sensor,
            reading = reading,
            useFahrenheit = settings.useFahrenheit,
            pressureUnit = settings.pressureUnit,
            sizePx = 300,
            isEnglish = isEng
        )

        val headerNote = if (sensor.header.isNotBlank()) " • HDR ${sensor.header}" else ""
        val pane = Pane.Builder()
            .setImage(gaugeIcon)
            .addRow(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_detail_current, currentText))
                    .addText(minMaxText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(l10nCtx.getString(R.string.car_detail_status, statusText))
                    .addText("PID ${sensor.modeAndPid}$headerNote • ${sensor.category.localizedName(isEng)}")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(
                        if (isPinned) l10nCtx.getString(R.string.aa_pin_remove)
                        else l10nCtx.getString(R.string.aa_pin_add)
                    )
                    .setBackgroundColor(if (isPinned) CarColor.DEFAULT else CarColor.PRIMARY)
                    .setOnClickListener {
                        repository.toggleAndroidAutoSensor(sensor.id)
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.action_back))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle(sensor.localizedDisplayName(isEng))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
