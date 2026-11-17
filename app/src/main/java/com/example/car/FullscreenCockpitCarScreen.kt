package com.example.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Alert
import androidx.car.app.model.CarText
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.ObdApplication
import com.example.R
import com.example.obd.data.AndroidAutoDisplayMode
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdAlertEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FullscreenCockpitCarScreen(
    carContext: CarContext,
    private val surfaceController: CockpitSurfaceController,
    private val isPushedOnTopOfMenu: Boolean = false
) : Screen(carContext) {

    private val repository = (carContext.applicationContext as ObdApplication).telemetryRepository
    @Volatile
    private var isScreenResumed = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    repository.settings
                        .map { Triple(it.activePreset.id, it.activePreset.name, it.appLanguage) }
                        .distinctUntilChanged()
                        .collect {
                            invalidate()
                        }
                }
                lifecycleScope.launch {
                    repository.alertEvents.collect { event ->
                        if (isScreenResumed && repository.settings.value.aaPopupAlertsEnabled) {
                            showCarAlert(event)
                        }
                    }
                }
            }

            override fun onResume(owner: LifecycleOwner) {
                isScreenResumed = true
                repository.sessionLogger.info("AUTO", "Активирован полноэкранный режим приборов (NavigationTemplate)")
                surfaceController.setRenderActive(true)
            }

            override fun onPause(owner: LifecycleOwner) {
                isScreenResumed = false
                surfaceController.setRenderActive(false)
            }
        })
    }

    private fun showCarAlert(event: ObdAlertEvent) {
        val l10nCtx = AppLocalization.localizedContext(carContext, repository.settings.value.appLanguage)
        val subtitle = if (event.level == AlertLevel.CRITICAL) {
            l10nCtx.getString(R.string.car_alert_crit_sub)
        } else {
            l10nCtx.getString(R.string.car_alert_warn_sub)
        }
        runCatching {
            val alert = Alert.Builder(
                event.id,
                CarText.create(event.message),
                8000L
            )
                .setSubtitle(CarText.create(subtitle))
                .build()
            carContext.getCarService(AppManager::class.java).showAlert(alert)
        }
    }

    override fun onGetTemplate(): Template {
        val settings = repository.settings.value
        val l10nCtx = AppLocalization.localizedContext(carContext, settings.appLanguage)
        val presetName = settings.activePreset.localizedName(settings.isEnglish)
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(presetName)
                    .setOnClickListener {
                        repository.cycleNextCockpitPreset()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.aa_btn_menu))
                    .setOnClickListener {
                        surfaceController.setRenderActive(false)
                        if (isPushedOnTopOfMenu) {
                            screenManager.pop()
                        } else {
                            screenManager.push(ObdMainCarScreen(carContext, surfaceController))
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(l10nCtx.getString(R.string.aa_btn_exit))
                    .setOnClickListener {
                        surfaceController.setRenderActive(false)
                        if (isPushedOnTopOfMenu) {
                            repository.setAndroidAutoDisplayMode(AndroidAutoDisplayMode.STANDARD_GRID)
                            screenManager.pop()
                        } else {
                            carContext.finishCarApp()
                        }
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }
}
