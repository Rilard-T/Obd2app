package com.example.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ObdApplication
import com.example.obd.data.AndroidAutoDisplayMode

class ObdCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return ObdCarSession()
    }
}

class ObdCarSession : Session() {

    private var surfaceController: CockpitSurfaceController? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val repository = (carContext.applicationContext as ObdApplication).telemetryRepository
        repository.tryAutoConnect("Android Auto")

        if (!com.example.BuildConfig.ALLOW_AA_NAVIGATION_SURFACE) {
            surfaceController = null
            return ObdMainCarScreen(carContext, surfaceController = null)
        }

        val controller = surfaceController ?: CockpitSurfaceController(repository).also { created ->
            surfaceController = created
            created.startObserving()
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(created)
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    runCatching {
                        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                    }
                    created.release()
                    surfaceController = null
                }
            })
        }

        val mode = repository.settings.value.effectiveAndroidAutoDisplayMode
        return if (mode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) {
            FullscreenCockpitCarScreen(carContext, controller, isPushedOnTopOfMenu = false)
        } else {
            ObdMainCarScreen(carContext, controller)
        }
    }
}
