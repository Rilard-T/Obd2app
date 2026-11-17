package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ObdApplication
import com.example.obd.data.AndroidAutoDisplayMode
import com.example.obd.data.CustomPidEntity
import com.example.obd.data.DiagnosticScanEntity
import com.example.obd.data.ObdLogEntry
import com.example.obd.data.ObdUserSettings
import com.example.obd.model.AppLanguage
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.DtcState
import com.example.obd.model.Elm327BenchmarkResult
import com.example.obd.model.LivePidProbeResult
import com.example.obd.model.ObdAlertEvent
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.SensorCategory
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import com.example.obd.model.TtsVoiceOption
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
    val settings: ObdUserSettings = ObdUserSettings(),
    val settingsLoaded: Boolean = false,
    val scanHistory: List<DiagnosticScanEntity> = emptyList(),
    val logEntries: List<ObdLogEntry> = emptyList(),
    val pairedDevices: List<BluetoothDeviceItem> = emptyList(),
    val bleDiscoveredDevices: List<BluetoothDeviceItem> = emptyList(),
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
    val availableTtsVoices: List<TtsVoiceOption> = emptyList()
)

private data class TelemetryBundle(
    val connectionState: ObdConnectionState,
    val sensors: List<TelemetrySensor>,
    val customPids: List<CustomPidEntity>,
    val readings: Map<String, SensorReading>,
    val dtcState: DtcState
)

private data class BtBundle(
    val pairedDevices: List<BluetoothDeviceItem>,
    val bleDevices: List<BluetoothDeviceItem>,
    val isScanningBle: Boolean,
    val hasPermissions: Boolean,
    val btEnabled: Boolean
)

private data class FeatureBundle(
    val supportedMode01Pids: Set<String>?,
    val benchmarkResult: Elm327BenchmarkResult?,
    val isRunningBenchmark: Boolean,
    val activeAlert: ObdAlertEvent?,
    val availableTtsVoices: List<TtsVoiceOption>
)

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObdApplication
    private val repository = app.telemetryRepository
    private val scanner = repository.bluetoothScanner

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

    private val btFlow = combine(
        _pairedDevices,
        _bleDevices,
        _isScanningBle,
        _hasPermissions,
        _btEnabled
    ) { paired, ble, scanning, hasPerm, btEn ->
        BtBundle(paired, ble, scanning, hasPerm, btEn)
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

    private val uiFiltersFlow = combine(
        _selectedCategory,
        _selectedChartSensorId,
        _showAllBtDevices,
        _settingsLoaded
    ) { cat, chartId, showAll, loaded ->
        listOf(cat, chartId, showAll, loaded)
    }

    val uiState: StateFlow<ObdUiState> = combine(
        telemetryFlow,
        btFlow,
        featureFlow,
        app.settingsDataStore.settingsFlow.onEach { _settingsLoaded.value = true },
        repository.recentScans,
        repository.logEntries,
        uiFiltersFlow
    ) { values ->
        val telemetry = values[0] as TelemetryBundle
        val bt = values[1] as BtBundle
        val feature = values[2] as FeatureBundle
        val settings = values[3] as ObdUserSettings
        @Suppress("UNCHECKED_CAST")
        val scans = values[4] as List<DiagnosticScanEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[5] as List<ObdLogEntry>
        @Suppress("UNCHECKED_CAST")
        val filters = values[6] as List<Any?>

        ObdUiState(
            connectionState = telemetry.connectionState,
            sensors = telemetry.sensors,
            customPids = telemetry.customPids,
            readings = telemetry.readings,
            dtcState = telemetry.dtcState,
            settings = settings,
            settingsLoaded = filters[3] as Boolean,
            scanHistory = scans,
            logEntries = logs,
            pairedDevices = bt.pairedDevices,
            bleDiscoveredDevices = bt.bleDevices,
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
            availableTtsVoices = feature.availableTtsVoices
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

    fun clearDtcs() {
        repository.clearDtcs()
    }

    fun clearScanHistory() {
        repository.clearScanHistory()
    }

    fun toggleAndroidAutoSensor(sensorId: String) {
        repository.toggleAndroidAutoSensor(sensorId)
    }

    fun setAndroidAutoDisplayMode(mode: AndroidAutoDisplayMode) {
        repository.setAndroidAutoDisplayMode(mode)
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
        alertsEnabled: Boolean = true
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
            alertsEnabled = alertsEnabled
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

    fun importTorqueCsv(csvText: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importTorqueCsv(csvText)
            onComplete(count)
        }
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
