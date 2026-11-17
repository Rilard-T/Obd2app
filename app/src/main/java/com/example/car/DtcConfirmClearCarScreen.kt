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
import com.example.obd.i18n.AppLocalization

class DtcConfirmClearCarScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = (carContext.applicationContext as ObdApplication).telemetryRepository

    override fun onGetTemplate(): Template {
        val l10nCtx = AppLocalization.localizedContext(carContext, repository.settings.value.appLanguage)
        return MessageTemplate.Builder(
            l10nCtx.getString(R.string.dtc_clear_car_msg)
        )
            .setHeader(
                Header.Builder()
                    .setTitle(l10nCtx.getString(R.string.dtc_clear_title))
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
                    .setTitle(l10nCtx.getString(R.string.dtc_clear_mode04_btn))
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        repository.clearDtcs()
                        screenManager.pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.action_cancel))
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}
