package com.example

import android.app.Application
import com.example.obd.data.DiagnosticDatabase
import com.example.obd.data.ObdTelemetryRepository
import com.example.obd.data.SettingsDataStore
import com.example.obd.protocol.BluetoothScanner

class ObdApplication : Application() {

    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var diagnosticDatabase: DiagnosticDatabase
        private set

    lateinit var telemetryRepository: ObdTelemetryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        diagnosticDatabase = DiagnosticDatabase.getInstance(this)
        val scanner = BluetoothScanner(this)
        telemetryRepository = ObdTelemetryRepository(
            context = this,
            settingsDataStore = settingsDataStore,
            diagnosticScanDao = diagnosticDatabase.diagnosticScanDao(),
            bluetoothScanner = scanner
        )
    }
}
