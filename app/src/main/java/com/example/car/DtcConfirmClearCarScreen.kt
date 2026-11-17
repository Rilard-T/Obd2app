package com.example.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.example.ObdApplication
import com.example.R

class DtcConfirmClearCarScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = (carContext.applicationContext as ObdApplication).telemetryRepository

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
            "Команда Mode 04 удалит коды неисправностей из памяти ЭБУ и погасит индикатор Check Engine. Выполнять только на остановленном автомобиле."
        )
            .setHeader(
                Header.Builder()
                    .setTitle("Сбросить ошибки ЭБУ?")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_car_dtc)
                ).setTint(CarColor.YELLOW).build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Сбросить (Mode 04)")
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        repository.clearDtcs()
                        screenManager.pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Отмена")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
