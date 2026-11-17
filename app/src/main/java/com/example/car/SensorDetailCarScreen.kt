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
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SensorDetailCarScreen(
    carContext: CarContext,
    private val pid: ObdPid
) : Screen(carContext) {

    private val repository = (carContext.applicationContext as ObdApplication).telemetryRepository
    private var lastInvalidateMs: Long = 0L

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    combine(repository.readings, repository.settings) { _, _ -> Unit }.collectLatest {
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
        val reading = repository.readings.value[pid]
        val unit = pid.displayUnit(settings.useFahrenheit, settings.pressureUnit)

        val currentText = reading?.let {
            "${pid.formatValue(it.value, settings.useFahrenheit, settings.pressureUnit)} $unit"
        } ?: "Ожидание данных..."

        val minMaxText = reading?.let {
            val minStr = pid.formatValue(it.minValue, settings.useFahrenheit, settings.pressureUnit)
            val maxStr = pid.formatValue(it.maxValue, settings.useFahrenheit, settings.pressureUnit)
            "Мин: $minStr $unit • Макс: $maxStr $unit"
        } ?: "Диапазон: ${pid.minRange.toInt()}..${pid.maxRange.toInt()} $unit"

        val statusText = when (reading?.alertLevel) {
            AlertLevel.CRITICAL -> "КРИТИЧЕСКОЕ ЗНАЧЕНИЕ"
            AlertLevel.WARNING -> "Повышенное значение"
            else -> "В пределах нормы"
        }

        val isPinned = pid in settings.androidAutoPids
        val gaugeIcon = CarGaugeBitmapFactory.createGaugeIcon(
            pid = pid,
            reading = reading,
            useFahrenheit = settings.useFahrenheit,
            pressureUnit = settings.pressureUnit,
            sizePx = 300
        )

        val pane = Pane.Builder()
            .setImage(gaugeIcon)
            .addRow(
                Row.Builder()
                    .setTitle("Текущее значение: $currentText")
                    .addText(minMaxText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Состояние: $statusText")
                    .addText("OBD2 PID ${pid.modeAndPid} • ${pid.category.displayName}")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (isPinned) "Убрать с приборов" else "На приборы")
                    .setBackgroundColor(if (isPinned) CarColor.DEFAULT else CarColor.PRIMARY)
                    .setOnClickListener {
                        repository.toggleAndroidAutoPid(pid)
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Назад")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle(pid.displayName)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
