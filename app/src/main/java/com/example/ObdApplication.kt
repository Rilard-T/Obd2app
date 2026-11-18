package com.example

import android.app.Application
import com.example.obd.data.DiagnosticDatabase
import com.example.obd.data.ObdSessionLogger
import com.example.obd.data.ObdTelemetryRepository
import com.example.obd.data.PlayBillingManager
import com.example.obd.data.SettingsDataStore
import com.example.obd.protocol.BluetoothScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObdApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var diagnosticDatabase: DiagnosticDatabase
        private set

    lateinit var sessionLogger: ObdSessionLogger
        private set

    lateinit var telemetryRepository: ObdTelemetryRepository
        private set

    lateinit var playBillingManager: PlayBillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        diagnosticDatabase = DiagnosticDatabase.getInstance(this)
        sessionLogger = ObdSessionLogger(this)
        val scanner = BluetoothScanner(this)
        telemetryRepository = ObdTelemetryRepository(
            context = this,
            settingsDataStore = settingsDataStore,
            diagnosticScanDao = diagnosticDatabase.diagnosticScanDao(),
            customPidDao = diagnosticDatabase.customPidDao(),
            vehicleProfileDao = diagnosticDatabase.vehicleProfileDao(),
            maintenanceDao = diagnosticDatabase.maintenanceDao(),
            tripRecordDao = diagnosticDatabase.tripRecordDao(),
            accelerationRunDao = diagnosticDatabase.accelerationRunDao(),
            bluetoothScanner = scanner,
            sessionLogger = sessionLogger
        )
        playBillingManager = PlayBillingManager(
            context = this,
            scope = appScope,
            onProEntitlementChanged = { entitled ->
                if (entitled) {
                    appScope.launch {
                        settingsDataStore.setProPreviewEnabled(true)
                    }
                }
            }
        )
        if (!BuildConfig.IS_GITHUB_BUILD) {
            playBillingManager.startConnection()
        }
    }
}
