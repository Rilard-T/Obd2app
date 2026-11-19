package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ObdApplication
import com.example.obd.ai.AiAnalysisState
import com.example.obd.data.AccelerationRunEntity
import com.example.obd.data.AndroidAutoDisplayMode
import com.example.obd.data.CustomPidEntity
import com.example.obd.data.DiagnosticScanEntity
import com.example.obd.data.FuelTrimHealthSummary
import com.example.obd.data.MaintenanceItemEntity
import com.example.obd.data.ObdLogEntry
import com.example.obd.data.ObdUserSettings
import com.example.obd.data.TelemetrySnapshotEntity
import com.example.obd.data.TripRecordEntity
import com.example.obd.data.VehicleProfileEntity
import com.example.obd.model.AccelDiscipline
import com.example.obd.model.AccelTimerState
import com.example.obd.model.AiVehicleSuggestion
import com.example.obd.model.AiVehicleSuggestionState
import com.example.obd.model.AppLanguage
import com.example.obd.model.BatteryTestState
import com.example.obd.model.BillingUiState
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.CatalogVehicleTemplate
import com.example.obd.model.CloudSyncState
import com.example.obd.model.CockpitDialTheme
import com.example.obd.model.DataloggerState
import com.example.obd.model.DtcState
import com.example.obd.model.Elm327BenchmarkResult
import com.example.obd.model.FreezeFrameData
import com.example.obd.model.FuelType
import com.example.obd.model.GpsSpeedState
import com.example.obd.model.ImReadinessResult
import com.example.obd.model.LivePidProbeResult
import com.example.obd.model.ObdAlertEvent
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PhotoAnalysisMode
import com.example.obd.model.PhotoAnalysisState
import com.example.obd.model.PrePurchaseInspectionResult
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.SensorCategory
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import com.example.obd.model.TerminalEntry
import com.example.obd.model.TripStats
import com.example.obd.model.TtsVoiceOption
import com.example.obd.model.UsbSerialDeviceItem
import com.example.obd.model.VehicleInfoState
import com.example.obd.model.VoiceAssistantState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ObdUiState(
    val connectionState: ObdConnectionState = ObdConnectionState.Disconnected,
    val sensors: List<TelemetrySensor> = ObdPid.entries.map { it.toTelemetrySensor() },
    val customPids: List<CustomPidEntity> = emptyList(),
    val readings: Map<String, SensorReading> = emptyMap(),
    val dtcState: DtcState = DtcState(),
    val imReadiness: ImReadinessResult? = null,
    val freezeFrame: FreezeFrameData? = null,
    val vehicleInfo: VehicleInfoState = VehicleInfoState(),
    val aiAnalysisState: AiAnalysisState = AiAnalysisState.Idle,
    val voiceAssistantState: VoiceAssistantState = VoiceAssistantState.Idle,
    val tripStats: TripStats = TripStats(),
    val accelTimer: AccelTimerState = AccelTimerState(),
    val vehicleProfiles: List<VehicleProfileEntity> = emptyList(),
    val maintenanceItems: List<MaintenanceItemEntity> = emptyList(),
    val tripHistory: List<TripRecordEntity> = emptyList(),
    val accelerationRuns: List<AccelerationRunEntity> = emptyList(),
    val settings: ObdUserSettings = ObdUserSettings(),
    val settingsLoaded: Boolean = false,
    val scanHistory: List<DiagnosticScanEntity> = emptyList(),
    val logEntries: List<ObdLogEntry> = emptyList(),
    val pairedDevices: List<BluetoothDeviceItem> = emptyList(),
    val bleDiscoveredDevices: List<BluetoothDeviceItem> = emptyList(),
    val usbDevices: List<UsbSerialDeviceItem> = emptyList(),
    val isScanningBle: Boolean = false,
    val hasBluetoothPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val selectedCategory: SensorCategory? = null,
    val selectedChartSensorId: String = ObdPid.ENGINE_RPM.modeAndPid,
    val showAllBtDevices: Boolean = false,
    val supportedMode01Pids: Set<String>? = null,
    val benchmarkResult: Elm327BenchmarkResult? = null,
    val isRunningBenchmark: Boolean = false,
    val activeAlert: ObdAlertEvent? = null,
    val availableTtsVoices: List<TtsVoiceOption> = emptyList(),
    val dataloggerState: DataloggerState = DataloggerState(),
    val gpsSpeedState: GpsSpeedState = GpsSpeedState(),
    val cloudSyncState: CloudSyncState = CloudSyncState(),
    val billingUiState: BillingUiState = BillingUiState(),
    val prePurchaseResult: PrePurchaseInspectionResult? = null,
    val batteryTestState: BatteryTestState = BatteryTestState(),
    val photoAnalysisState: PhotoAnalysisState = PhotoAnalysisState.Idle,
    val aiVehicleSuggestionState: AiVehicleSuggestionState = AiVehicleSuggestionState.Idle,
    val terminalEntries: List<TerminalEntry> = emptyList(),
    val fuelTrimHealth: FuelTrimHealthSummary = FuelTrimHealthSummary(),
    val telemetrySnapshots: List<TelemetrySnapshotEntity> = emptyList(),
    val isProbingCustomPids: Boolean = false
) {
    val activeVehicleProfile: VehicleProfileEntity?
        get() = vehicleProfiles.find { it.id == settings.activeVehicleProfileId } ?: vehicleProfiles.firstOrNull()

    val hasProAccess: Boolean
        get() = com.example.BuildConfig.IS_GITHUB_BUILD ||
            billingUiState.hasProEntitlement ||
            (com.example.BuildConfig.DEBUG && settings.isProPreviewEnabled)
}

private data class TelemetryBundle(
    val connectionState: ObdConnectionState,
    val sensors: List<TelemetrySensor>,
    val customPids: List<CustomPidEntity>,
    val readings: Map<String, SensorReading>,
    val dtcState: DtcState
)

private data class DeepDiagBundle(
    val imReadiness: ImReadinessResult?,
    val freezeFrame: FreezeFrameData?,
    val vehicleInfo: VehicleInfoState,
    val aiAnalysisState: AiAnalysisState,
    val voiceAssistantState: VoiceAssistantState
)

private data class TripGarageBundle(
    val tripStats: TripStats,
    val accelTimer: AccelTimerState,
    val vehicleProfiles: List<VehicleProfileEntity>,
    val tripHistory: List<TripRecordEntity>,
    val accelerationRuns: List<AccelerationRunEntity>,
    val maintenanceItems: List<MaintenanceItemEntity>
)

private data class BtBundle(
    val pairedDevices: List<BluetoothDeviceItem>,
    val bleDevices: List<BluetoothDeviceItem>,
    val isScanningBle: Boolean,
    val hasPermissions: Boolean,
    val btEnabled: Boolean,
    val usbDevices: List<UsbSerialDeviceItem>
)

private data class FeatureBundle(
    val supportedMode01Pids: Set<String>?,
    val benchmarkResult: Elm327BenchmarkResult?,
    val isRunningBenchmark: Boolean,
    val activeAlert: ObdAlertEvent?,
    val availableTtsVoices: List<TtsVoiceOption>
)

private data class AdvancedBundle(
    val prePurchaseResult: PrePurchaseInspectionResult?,
    val batteryTestState: BatteryTestState,
    val photoAnalysisState: PhotoAnalysisState,
    val aiVehicleSuggestionState: AiVehicleSuggestionState,
    val terminalEntries: List<TerminalEntry>
)

private data class InsightsBundle(
    val fuelTrimHealth: FuelTrimHealthSummary,
    val telemetrySnapshots: List<TelemetrySnapshotEntity>,
    val isProbingCustomPids: Boolean
)

private data class ExtrasBundle(
    val dataloggerState: DataloggerState,
    val gpsSpeedState: GpsSpeedState,
    val cloudSyncState: CloudSyncState,
    val billingUiState: BillingUiState,
    val advanced: AdvancedBundle,
    val insights: InsightsBundle
)

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObdApplication
    private val repository = app.telemetryRepository
    private val scanner = repository.bluetoothScanner
    private val billingManager = app.playBillingManager

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    private val _bleDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    private val _isScanningBle = MutableStateFlow(false)
    private val _hasPermissions = MutableStateFlow(scanner.hasBluetoothPermissions())
    private val _btEnabled = MutableStateFlow(scanner.isBluetoothEnabled())
    private val _selectedCategory = MutableStateFlow<SensorCategory?>(null)
    private val _selectedChartSensorId = MutableStateFlow(ObdPid.ENGINE_RPM.modeAndPid)
    private val _showAllBtDevices = MutableStateFlow(false)
    private val _settingsLoaded = MutableStateFlow(false)

    private var bleScanJob: Job? = null

    private val telemetryFlow = combine(
        repository.connectionState,
        repository.sensors,
        repository.customPids,
        repository.readings,
        repository.dtcState
    ) { conn, sensors, customPids, readings, dtc ->
        TelemetryBundle(conn, sensors, customPids, readings, dtc)
    }

    private val deepDiagFlow = combine(
        repository.imReadiness,
        repository.freezeFrame,
        repository.vehicleInfo,
        repository.aiAnalysisState,
        repository.voiceAssistantState
    ) { im, ff, vInfo, ai, voice ->
        DeepDiagBundle(im, ff, vInfo, ai, voice)
    }

    private val tripGarageFlow = combine(
        repository.tripStats,
        repository.accelTimer,
        repository.vehicleProfiles,
        repository.tripHistory,
        repository.accelerationRuns,
        repository.maintenanceItems
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TripGarageBundle(
            tripStats = values[0] as TripStats,
            accelTimer = values[1] as AccelTimerState,
            vehicleProfiles = values[2] as List<VehicleProfileEntity>,
            tripHistory = values[3] as List<TripRecordEntity>,
            accelerationRuns = values[4] as List<AccelerationRunEntity>,
            maintenanceItems = values[5] as List<MaintenanceItemEntity>
        )
    }

    private val btFlow = combine(
        _pairedDevices,
        _bleDevices,
        _isScanningBle,
        _hasPermissions,
        _btEnabled,
        repository.usbDevices
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        BtBundle(
            pairedDevices = values[0] as List<BluetoothDeviceItem>,
            bleDevices = values[1] as List<BluetoothDeviceItem>,
            isScanningBle = values[2] as Boolean,
            hasPermissions = values[3] as Boolean,
            btEnabled = values[4] as Boolean,
            usbDevices = values[5] as List<UsbSerialDeviceItem>
        )
    }

    private val featureFlow = combine(
        repository.supportedMode01Pids,
        repository.benchmarkResult,
        repository.isRunningBenchmark,
        repository.activeAlert,
        repository.availableTtsVoices
    ) { supported, bench, runningBench, alert, voices ->
        FeatureBundle(supported, bench, runningBench, alert, voices)
    }

    private val advancedFlow = combine(
        repository.prePurchaseResult,
        repository.batteryTestState,
        repository.photoAnalysisState,
        repository.aiVehicleSuggestionState,
        repository.terminalEntries
    ) { prePurchase, battery, photo, suggestion, terminal ->
        AdvancedBundle(prePurchase, battery, photo, suggestion, terminal)
    }

    private val insightsFlow = combine(
        repository.fuelTrimHealth,
        repository.telemetrySnapshots,
        repository.isProbingCustomPids
    ) { trim, snaps, probing ->
        InsightsBundle(trim, snaps, probing)
    }

    private val extrasFlow = combine(
        repository.dataloggerState,
        repository.gpsSpeedState,
        repository.cloudSyncState,
        billingManager.state,
        advancedFlow,
        insightsFlow
    ) { values ->
        ExtrasBundle(
            dataloggerState = values[0] as DataloggerState,
            gpsSpeedState = values[1] as GpsSpeedState,
            cloudSyncState = values[2] as CloudSyncState,
            billingUiState = values[3] as BillingUiState,
            advanced = values[4] as AdvancedBundle,
            insights = values[5] as InsightsBundle
        )
    }

    private val uiFiltersFlow = combine(
        _selectedCategory,
        _selectedChartSensorId,
        _showAllBtDevices,
        _settingsLoaded,
        extrasFlow
    ) { cat, chartId, showAll, loaded, extras ->
        listOf(cat, chartId, showAll, loaded, extras)
    }

    val uiState: StateFlow<ObdUiState> = combine(
        telemetryFlow,
        deepDiagFlow,
        tripGarageFlow,
        btFlow,
        featureFlow,
        app.settingsDataStore.settingsFlow.onEach { _settingsLoaded.value = true },
        repository.recentScans,
        repository.logEntries,
        uiFiltersFlow
    ) { values ->
        val telemetry = values[0] as TelemetryBundle
        val deepDiag = values[1] as DeepDiagBundle
        val tripGarage = values[2] as TripGarageBundle
        val bt = values[3] as BtBundle
        val feature = values[4] as FeatureBundle
        val settings = values[5] as ObdUserSettings
        @Suppress("UNCHECKED_CAST")
        val scans = values[6] as List<DiagnosticScanEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[7] as List<ObdLogEntry>
        @Suppress("UNCHECKED_CAST")
        val filters = values[8] as List<Any?>
        val extras = filters[4] as ExtrasBundle

        ObdUiState(
            connectionState = telemetry.connectionState,
            sensors = telemetry.sensors,
            customPids = telemetry.customPids,
            readings = telemetry.readings,
            dtcState = telemetry.dtcState,
            imReadiness = deepDiag.imReadiness,
            freezeFrame = deepDiag.freezeFrame,
            vehicleInfo = deepDiag.vehicleInfo,
            aiAnalysisState = deepDiag.aiAnalysisState,
            voiceAssistantState = deepDiag.voiceAssistantState,
            tripStats = tripGarage.tripStats,
            accelTimer = tripGarage.accelTimer,
            vehicleProfiles = tripGarage.vehicleProfiles,
            maintenanceItems = tripGarage.maintenanceItems,
            tripHistory = tripGarage.tripHistory,
            accelerationRuns = tripGarage.accelerationRuns,
            settings = settings,
            settingsLoaded = filters[3] as Boolean,
            scanHistory = scans,
            logEntries = logs,
            pairedDevices = bt.pairedDevices,
            bleDiscoveredDevices = bt.bleDevices,
            usbDevices = bt.usbDevices,
            isScanningBle = bt.isScanningBle,
            hasBluetoothPermissions = bt.hasPermissions,
            isBluetoothEnabled = bt.btEnabled,
            selectedCategory = filters[0] as SensorCategory?,
            selectedChartSensorId = filters[1] as String,
            showAllBtDevices = filters[2] as Boolean,
            supportedMode01Pids = feature.supportedMode01Pids,
            benchmarkResult = feature.benchmarkResult,
            isRunningBenchmark = feature.isRunningBenchmark,
            activeAlert = feature.activeAlert,
            availableTtsVoices = feature.availableTtsVoices,
            dataloggerState = extras.dataloggerState,
            gpsSpeedState = extras.gpsSpeedState,
            cloudSyncState = extras.cloudSyncState,
            billingUiState = extras.billingUiState,
            prePurchaseResult = extras.advanced.prePurchaseResult,
            batteryTestState = extras.advanced.batteryTestState,
            photoAnalysisState = extras.advanced.photoAnalysisState,
            aiVehicleSuggestionState = extras.advanced.aiVehicleSuggestionState,
            terminalEntries = extras.advanced.terminalEntries,
            fuelTrimHealth = extras.insights.fuelTrimHealth,
            telemetrySnapshots = extras.insights.telemetrySnapshots,
            isProbingCustomPids = extras.insights.isProbingCustomPids
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ObdUiState(
            hasBluetoothPermissions = scanner.hasBluetoothPermissions(),
            isBluetoothEnabled = scanner.isBluetoothEnabled()
        )
    )

    init {
        refreshBluetoothDevices()
        refreshUsbDevices()
    }

    fun tryAutoConnect(reason: String) {
        refreshBluetoothDevices()
        repository.tryAutoConnect(reason)
    }

    fun refreshBluetoothDevices() {
        _hasPermissions.value = scanner.hasBluetoothPermissions()
        _btEnabled.value = scanner.isBluetoothEnabled()
        if (_hasPermissions.value && _btEnabled.value) {
            _pairedDevices.value = scanner.getPairedDevices()
        } else {
            _pairedDevices.value = emptyList()
        }
    }

    fun startBleScan() {
        refreshBluetoothDevices()
        if (!_hasPermissions.value || !_btEnabled.value) return
        bleScanJob?.cancel()
        _bleDevices.value = emptyList()
        _isScanningBle.value = true
        repository.sessionLogger.info("SCAN", "Запуск поиска устройств Bluetooth Classic + BLE")

        bleScanJob = viewModelScope.launch {
            val bleCollector = launch {
                scanner.scanBleDevices().collect { found ->
                    mergeDiscoveredDevice(found)
                }
            }
            val classicCollector = launch {
                scanner.scanClassicDevices().collect { found ->
                    mergeDiscoveredDevice(found)
                }
            }
            delay(10_000L)
            bleCollector.cancel()
            classicCollector.cancel()
            _isScanningBle.value = false
            repository.sessionLogger.info("SCAN", "Поиск завершён, найдено устройств: ${_bleDevices.value.size}")
        }
    }

    private fun mergeDiscoveredDevice(found: BluetoothDeviceItem) {
        val existing = _bleDevices.value
        if (existing.none { it.address == found.address }) {
            _bleDevices.value = (existing + found).sortedByDescending { it.isLikelyObd }
        }
    }

    fun stopBleScan() {
        bleScanJob?.cancel()
        bleScanJob = null
        _isScanningBle.value = false
    }

    fun connectToDevice(device: BluetoothDeviceItem) {
        stopBleScan()
        repository.connectToDevice(device)
    }

    fun connectWifi(host: String, port: Int) {
        stopBleScan()
        repository.connectWifi(host, port)
    }

    fun startSimulationMode() {
        stopBleScan()
        repository.startSimulationMode()
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun runAdapterBenchmark() {
        repository.runAdapterBenchmark()
    }

    suspend fun probeCustomPid(
        modeAndPid: String,
        header: String,
        equation: String
    ): LivePidProbeResult {
        return repository.probeCustomPid(modeAndPid, header, equation)
    }

    fun scanDtcs() {
        repository.scanDtcs()
    }

    fun scanFreezeFrame() {
        repository.scanFreezeFrame()
    }

    fun clearDtcs() {
        repository.clearDtcs()
    }

    fun clearScanHistory() {
        repository.clearScanHistory()
    }

    fun runAiFaultAnalysis() {
        repository.runAiFaultAnalysis()
    }

    fun clearAiAnalysis() {
        repository.clearAiAnalysis()
    }

    fun resetTrip() {
        repository.resetTrip()
    }

    fun saveCurrentTripToHistory() {
        repository.saveCurrentTripToHistory()
    }

    fun deleteTripRecord(id: Long) {
        repository.deleteTripRecord(id)
    }

    fun clearTripHistory() {
        repository.clearTripHistory()
    }

    fun buildTripsCsv(trips: List<TripRecordEntity>): String = repository.buildTripsCsv(trips)

    fun selectAccelDiscipline(discipline: AccelDiscipline) {
        repository.selectAccelDiscipline(discipline)
    }

    fun armAccelTimer() {
        repository.armAccelTimer()
    }

    fun resetAccelTimer() {
        repository.resetAccelTimer()
    }

    fun clearAccelRuns() {
        repository.clearAccelRuns()
    }

    fun selectVehicleProfile(profileId: Long) {
        repository.selectVehicleProfile(profileId)
    }

    fun saveVehicleProfile(
        existingId: Long? = null,
        name: String,
        vin: String?,
        displacementLiters: Float,
        fuelType: FuelType,
        redlineRpm: Int,
        shiftLightRpm: Int,
        fuelPricePerLiter: Float,
        preferredPresetId: String,
        odometerKm: Float? = null,
        curbWeightKg: Int = 1450
    ) {
        repository.saveVehicleProfile(
            existingId = existingId,
            name = name,
            vin = vin,
            displacementLiters = displacementLiters,
            fuelType = fuelType,
            redlineRpm = redlineRpm,
            shiftLightRpm = shiftLightRpm,
            fuelPricePerLiter = fuelPricePerLiter,
            preferredPresetId = preferredPresetId,
            odometerKm = odometerKm,
            curbWeightKg = curbWeightKg
        )
    }

    fun deleteVehicleProfile(profileId: Long) {
        repository.deleteVehicleProfile(profileId)
    }

    fun exportConfigBackupJson(): String = repository.exportConfigBackupJson()

    fun importConfigBackupJson(rawJson: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.importConfigBackupJson(rawJson))
        }
    }

    fun toggleAndroidAutoSensor(sensorId: String) {
        repository.toggleAndroidAutoSensor(sensorId)
    }

    fun setAndroidAutoDisplayMode(mode: AndroidAutoDisplayMode) {
        repository.setAndroidAutoDisplayMode(mode)
    }

    fun setDialTheme(theme: CockpitDialTheme) {
        repository.setDialTheme(theme)
    }

    fun setShiftLightEnabled(enabled: Boolean) {
        repository.setShiftLightEnabled(enabled)
    }

    fun setShiftLightRpm(rpm: Int) {
        repository.setShiftLightRpm(rpm)
    }

    fun selectCockpitPreset(presetId: String) {
        repository.selectCockpitPreset(presetId)
    }

    fun cycleNextCockpitPreset() {
        repository.cycleNextCockpitPreset()
    }

    fun renameCockpitPreset(presetId: String, newName: String) {
        repository.renameCockpitPreset(presetId, newName)
    }

    fun setFullscreenDialSlot(slotIndex: Int, sensorId: String) {
        repository.setFullscreenDialSlot(slotIndex, sensorId)
    }

    fun setFullscreenStripSlot(slotIndex: Int, sensorId: String) {
        repository.setFullscreenStripSlot(slotIndex, sensorId)
    }

    fun resetFullscreenLayoutDefaults() {
        repository.resetFullscreenLayoutDefaults()
    }

    fun setAutoConnectOnLaunch(enabled: Boolean) {
        repository.setAutoConnectOnLaunch(enabled)
    }

    fun setAutoReconnectOnDrop(enabled: Boolean) {
        repository.setAutoReconnectOnDrop(enabled)
    }

    fun setFastSlowPollingEnabled(enabled: Boolean) {
        repository.setFastSlowPollingEnabled(enabled)
    }

    fun setAutoConnectOnBtCar(enabled: Boolean) {
        repository.setAutoConnectOnBtCar(enabled)
    }

    fun setVoiceAlertsEnabled(enabled: Boolean) {
        repository.setVoiceAlertsEnabled(enabled)
    }

    fun setAaPopupAlertsEnabled(enabled: Boolean) {
        repository.setAaPopupAlertsEnabled(enabled)
    }

    fun preloadTtsVoices() {
        repository.preloadTtsVoices()
    }

    fun setTtsVolume(volume: Float) {
        repository.setTtsVolume(volume)
    }

    fun setTtsSpeechRate(rate: Float) {
        repository.setTtsSpeechRate(rate)
    }

    fun setTtsVoiceName(voiceName: String?) {
        repository.setTtsVoiceName(voiceName)
    }

    fun setSensorAlertOverride(override: SensorAlertOverride) {
        repository.setSensorAlertOverride(override)
    }

    fun dismissActiveAlert() {
        repository.dismissActiveAlert()
    }

    fun triggerTestAlert() {
        repository.triggerTestAlert()
    }

    fun selectCategory(category: SensorCategory?) {
        _selectedCategory.value = category
    }

    fun selectChartSensor(sensorId: String) {
        _selectedChartSensorId.value = sensorId
    }

    fun toggleShowAllBtDevices() {
        _showAllBtDevices.value = !_showAllBtDevices.value
    }

    fun setPollingIntervalMs(intervalMs: Long) {
        repository.setPollingIntervalMs(intervalMs)
    }

    fun setUseFahrenheit(useFahrenheit: Boolean) {
        repository.setUseFahrenheit(useFahrenheit)
    }

    fun setPressureUnit(unit: PressureUnit) {
        repository.setPressureUnit(unit)
    }

    fun setAppLanguage(language: AppLanguage) {
        repository.setAppLanguage(language)
    }

    fun requiredBluetoothPermissions(): Array<String> = scanner.requiredPermissions()

    fun startupPermissions(): Array<String> = scanner.allStartupPermissions()

    fun markStartupPermissionsRequested() {
        repository.markStartupPermissionsRequested()
    }

    fun acceptSafetyDisclaimer() {
        repository.acceptSafetyDisclaimer()
    }

    fun setProPreviewEnabled(enabled: Boolean) {
        repository.setProPreviewEnabled(enabled)
    }

    // --- USB OTG Serial ---

    fun refreshUsbDevices() {
        repository.refreshUsbDevices()
    }

    fun requestUsbPermission(deviceId: Int) {
        repository.requestUsbPermission(deviceId)
    }

    fun connectUsbDevice(deviceId: Int) {
        stopBleScan()
        repository.connectUsbDevice(deviceId)
    }

    // --- Datalogger ---

    fun startDatalogger() {
        repository.startDatalogger()
    }

    fun stopDatalogger() {
        repository.stopDatalogger()
    }

    fun clearDatalogger() {
        repository.clearDatalogger()
    }

    fun buildDataloggerCsv(): String = repository.buildDataloggerCsv()

    // --- OEM Brand PID Catalog ---

    fun importOemPack(packId: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importOemPack(packId)
            onComplete(count)
        }
    }

    // --- Trip Automation & Calibration + GPS ---

    fun setAutoSaveTripOnDisconnect(enabled: Boolean) {
        repository.setAutoSaveTripOnDisconnect(enabled)
    }

    fun setFuelCalibrationFactor(factor: Float) {
        repository.setFuelCalibrationFactor(factor)
    }

    fun setSpeedCalibrationFactor(factor: Float) {
        repository.setSpeedCalibrationFactor(factor)
    }

    fun applyFuelFillupCalibration(actualLiters: Float, computerLiters: Float, onResult: (Float?) -> Unit) {
        onResult(repository.applyFuelFillupCalibration(actualLiters, computerLiters))
    }

    fun startGpsSpeedCheck() {
        repository.startGpsSpeedCheck()
    }

    fun stopGpsSpeedCheck() {
        repository.stopGpsSpeedCheck()
    }

    fun applySuggestedGpsSpeedFactor() {
        repository.applySuggestedGpsSpeedFactor()
    }

    // --- Maintenance Tracker ---

    fun addMaintenanceItem(
        title: String,
        category: String,
        intervalKm: Int,
        intervalHours: Int,
        notes: String?
    ) {
        repository.addMaintenanceItem(title, category, intervalKm, intervalHours, notes)
    }

    fun markMaintenanceDone(itemId: Long) {
        repository.markMaintenanceDone(itemId)
    }

    fun deleteMaintenanceItem(itemId: Long) {
        repository.deleteMaintenanceItem(itemId)
    }

    // --- Voice AI Driver Assistant ---

    fun askVoiceDriverQuestion(question: String, speakReply: Boolean = true) {
        repository.askVoiceDriverQuestion(question, speakReply)
    }

    fun clearVoiceAssistantState() {
        repository.clearVoiceAssistantState()
    }

    fun speakCarStatusSummary() {
        repository.speakCarStatusSummary()
    }

    // --- Pre-Purchase Inspection, Battery Test, Vision AI, VIN Fill, ELM Terminal ---

    fun runPrePurchaseInspection() {
        repository.runPrePurchaseInspection()
    }

    fun createPrePurchasePdfFile(): File? = repository.createPrePurchasePdfFile()

    fun startBatteryTest() {
        repository.startBatteryAlternatorTest()
    }

    fun cancelBatteryTest() {
        repository.cancelBatteryTest()
    }

    fun analyzePhoto(uri: Uri, mode: PhotoAnalysisMode) {
        repository.analyzeSelectedPhoto(uri, mode)
    }

    fun clearPhotoAnalysis() {
        repository.clearPhotoAnalysis()
    }

    fun requestAiVehicleSuggestion(vin: String?, query: String) {
        repository.requestAiVehicleSuggestion(vin, query)
    }

    fun clearAiVehicleSuggestion() {
        repository.clearAiVehicleSuggestion()
    }

    fun applyAiSuggestionExtras(suggestion: AiVehicleSuggestion) {
        repository.applyAiSuggestionExtras(suggestion)
    }

    fun sendRawElmCommand(command: String) {
        repository.sendRawElmCommand(command)
    }

    fun clearTerminal() {
        repository.clearTerminal()
    }

    // --- Cloud Sync ---

    fun attemptSilentCloudSignIn(activityContext: Context) {
        repository.attemptSilentCloudSignIn(activityContext)
    }

    fun signInWithGoogle(activityContext: Context) {
        repository.signInWithGoogle(activityContext)
    }

    fun syncCloudNow() {
        repository.syncCloudNow()
    }

    fun signOutCloud() {
        repository.signOutCloud()
    }

    // --- Google Play Billing ---

    fun refreshBilling() {
        billingManager.refreshPurchases()
    }

    fun launchProPurchase(activity: Activity, productId: String) {
        billingManager.launchPurchaseFlow(activity, productId, uiState.value.settings.isEnglish)
    }

    fun addCustomPid(
        name: String,
        shortName: String,
        modeAndPid: String,
        header: String,
        equation: String,
        unit: String,
        minValue: Float,
        maxValue: Float,
        decimals: Int,
        warningHigh: Float? = null,
        criticalHigh: Float? = null,
        warningLow: Float? = null,
        criticalLow: Float? = null,
        alertsEnabled: Boolean = true,
        bindToActiveVehicle: Boolean = true
    ) {
        repository.addCustomPid(
            name = name,
            shortName = shortName,
            modeAndPid = modeAndPid,
            header = header,
            equation = equation,
            unit = unit,
            minValue = minValue,
            maxValue = maxValue,
            decimals = decimals,
            warningHigh = warningHigh,
            criticalHigh = criticalHigh,
            warningLow = warningLow,
            criticalLow = criticalLow,
            alertsEnabled = alertsEnabled,
            bindToActiveVehicle = bindToActiveVehicle
        )
    }

    fun updateCustomPid(entity: CustomPidEntity) {
        repository.updateCustomPid(entity)
    }

    fun deleteCustomPid(id: Long) {
        if (_selectedChartSensorId.value == "CUSTOM_$id") {
            _selectedChartSensorId.value = ObdPid.ENGINE_RPM.modeAndPid
        }
        repository.deleteCustomPid(id)
    }

    fun importTorqueCsv(csvText: String, bindToActiveVehicle: Boolean = true, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importTorqueCsv(csvText, bindToActiveVehicle)
            onComplete(count)
        }
    }

    fun probeVehicleCustomPids() {
        repository.probeVehicleCustomPids()
    }

    fun clearTelemetrySnapshots() {
        repository.clearTelemetrySnapshots()
    }

    fun applyOnboardingSetup(
        language: AppLanguage,
        customVehicleName: String,
        vin: String?,
        template: CatalogVehicleTemplate?,
        displacementLiters: Float,
        fuelType: FuelType,
        redlineRpm: Int,
        curbWeightKg: Int,
        importRecommendedOemPids: Boolean
    ) {
        repository.applyOnboardingSetup(
            language = language,
            customVehicleName = customVehicleName,
            vin = vin,
            template = template,
            displacementLiters = displacementLiters,
            fuelType = fuelType,
            redlineRpm = redlineRpm,
            curbWeightKg = curbWeightKg,
            importRecommendedOemPids = importRecommendedOemPids
        )
    }

    fun skipOnboardingWizard() {
        repository.skipOnboardingWizard()
    }

    fun reopenOnboardingWizard() {
        repository.reopenOnboardingWizard()
    }

    fun buildErrorReportText(): String = repository.buildErrorReportText()

    fun createShareableReportFile(): File = repository.createShareableReportFile()

    fun clearLogs() {
        repository.clearLogs()
    }

    fun logInfo(tag: String, message: String) {
        repository.sessionLogger.info(tag, message)
    }

    override fun onCleared() {
        stopBleScan()
        super.onCleared()
    }
}
