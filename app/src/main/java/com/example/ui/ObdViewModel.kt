package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ObdApplication
import com.example.obd.data.DiagnosticScanEntity
import com.example.obd.data.ObdUserSettings
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.DtcState
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorCategory
import com.example.obd.model.SensorReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ObdUiState(
    val connectionState: ObdConnectionState = ObdConnectionState.Disconnected,
    val readings: Map<ObdPid, SensorReading> = emptyMap(),
    val dtcState: DtcState = DtcState(),
    val settings: ObdUserSettings = ObdUserSettings(),
    val scanHistory: List<DiagnosticScanEntity> = emptyList(),
    val pairedDevices: List<BluetoothDeviceItem> = emptyList(),
    val bleDiscoveredDevices: List<BluetoothDeviceItem> = emptyList(),
    val isScanningBle: Boolean = false,
    val hasBluetoothPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val selectedCategory: SensorCategory? = null,
    val selectedChartPid: ObdPid = ObdPid.ENGINE_RPM,
    val showAllBtDevices: Boolean = false
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
    private val _selectedChartPid = MutableStateFlow(ObdPid.ENGINE_RPM)
    private val _showAllBtDevices = MutableStateFlow(false)

    private var bleScanJob: Job? = null

    val uiState: StateFlow<ObdUiState> = combine(
        repository.connectionState,
        repository.readings,
        repository.dtcState,
        repository.settings,
        repository.recentScans,
        _pairedDevices,
        _bleDevices,
        _isScanningBle,
        _hasPermissions,
        _btEnabled,
        _selectedCategory,
        _selectedChartPid,
        _showAllBtDevices
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ObdUiState(
            connectionState = values[0] as ObdConnectionState,
            readings = values[1] as Map<ObdPid, SensorReading>,
            dtcState = values[2] as DtcState,
            settings = values[3] as ObdUserSettings,
            scanHistory = values[4] as List<DiagnosticScanEntity>,
            pairedDevices = values[5] as List<BluetoothDeviceItem>,
            bleDiscoveredDevices = values[6] as List<BluetoothDeviceItem>,
            isScanningBle = values[7] as Boolean,
            hasBluetoothPermissions = values[8] as Boolean,
            isBluetoothEnabled = values[9] as Boolean,
            selectedCategory = values[10] as SensorCategory?,
            selectedChartPid = values[11] as ObdPid,
            showAllBtDevices = values[12] as Boolean
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

        bleScanJob = viewModelScope.launch {
            val collectorJob = launch {
                scanner.scanBleDevices().collect { found ->
                    val existing = _bleDevices.value
                    if (existing.none { it.address == found.address }) {
                        _bleDevices.value = (existing + found).sortedByDescending { it.isLikelyObd }
                    }
                }
            }
            delay(10_000L)
            collectorJob.cancel()
            _isScanningBle.value = false
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

    fun scanDtcs() {
        repository.scanDtcs()
    }

    fun clearDtcs() {
        repository.clearDtcs()
    }

    fun clearScanHistory() {
        repository.clearScanHistory()
    }

    fun toggleAndroidAutoPid(pid: ObdPid) {
        repository.toggleAndroidAutoPid(pid)
    }

    fun selectCategory(category: SensorCategory?) {
        _selectedCategory.value = category
    }

    fun selectChartPid(pid: ObdPid) {
        _selectedChartPid.value = pid
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

    fun requiredBluetoothPermissions(): Array<String> = scanner.requiredPermissions()

    override fun onCleared() {
        stopBleScan()
        super.onCleared()
    }
}
