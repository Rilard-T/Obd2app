package com.example.obd.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.obd.ObdForegroundService
import com.example.obd.ai.AiAnalysisState
import com.example.obd.ai.AiDiagnosticAssistant
import com.example.obd.cloud.CloudAuthManager
import com.example.obd.cloud.CloudSyncRepository
import com.example.obd.model.AccelDiscipline
import com.example.obd.model.AccelTimerState
import com.example.obd.model.AiVehicleSuggestion
import com.example.obd.model.AiVehicleSuggestionState
import com.example.obd.model.AppLanguage
import com.example.obd.model.BatteryTestPhase
import com.example.obd.model.BatteryTestState
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.BluetoothTransportType
import com.example.obd.model.CloudSyncState
import com.example.obd.model.CloudSyncStatus
import com.example.obd.model.CockpitDialTheme
import com.example.obd.model.DataloggerState
import com.example.obd.model.DtcState
import com.example.obd.model.Elm327BenchmarkResult
import com.example.obd.model.FreezeFrameData
import com.example.obd.model.FuelType
import com.example.obd.model.GpsSpeedState
import com.example.obd.model.ImReadinessResult
import com.example.obd.model.LivePidProbeResult
import com.example.obd.model.MaintenanceStatus
import com.example.obd.model.Mode06TestResult
import com.example.obd.model.ObdAlertEvent
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PhotoAnalysisMode
import com.example.obd.model.PhotoAnalysisState
import com.example.obd.model.PrePurchaseInspectionResult
import com.example.obd.model.PressureUnit
import com.example.obd.model.ResetSuspicionLevel
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import com.example.obd.model.TerminalEntry
import com.example.obd.model.TripStats
import com.example.obd.model.TtsVoiceOption
import com.example.obd.model.UsbSerialDeviceItem
import com.example.obd.model.VehicleInfoState
import com.example.obd.model.VoiceAssistantState
import com.example.obd.protocol.BleObdTransport
import com.example.obd.protocol.BluetoothClassicObdTransport
import com.example.obd.protocol.BluetoothScanner
import com.example.obd.protocol.Elm327Benchmark
import com.example.obd.protocol.Elm327Parser
import com.example.obd.protocol.ObdTransport
import com.example.obd.protocol.OemPidCatalog
import com.example.obd.protocol.TorqueEquationEvaluator
import com.example.obd.protocol.UsbObdHelper
import com.example.obd.protocol.UsbObdTransport
import com.example.obd.protocol.WifiObdTransport
import com.example.widget.ObdStatusWidgetProvider
import com.example.widget.ObdWidgetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class ObdTelemetryRepository(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val diagnosticScanDao: DiagnosticScanDao,
    private val customPidDao: CustomPidDao,
    private val vehicleProfileDao: VehicleProfileDao,
    private val maintenanceDao: MaintenanceDao,
    private val tripRecordDao: TripRecordDao,
    private val accelerationRunDao: AccelerationRunDao,
    val bluetoothScanner: BluetoothScanner,
    val sessionLogger: ObdSessionLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val tripManager = TripComputerManager()
    private val aiAssistant = AiDiagnosticAssistant()
    val datalogger = TelemetryDatalogger()
    val gpsSpeedMonitor = GpsSpeedMonitor(context)
    private val cloudAuthManager = CloudAuthManager(context)
    private val cloudSyncRepository = CloudSyncRepository(
        context = context,
        vehicleProfileDao = vehicleProfileDao,
        maintenanceDao = maintenanceDao,
        customPidDao = customPidDao,
        tripRecordDao = tripRecordDao
    )

    val alertManager = ObdAlertManager(context, sessionLogger)
    val activeAlert: StateFlow<ObdAlertEvent?> = alertManager.activeAlert
    val alertEvents: SharedFlow<ObdAlertEvent> = alertManager.alertEvents
    val availableTtsVoices: StateFlow<List<TtsVoiceOption>> = alertManager.availableVoices

    private val _connectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, SensorReading>>(emptyMap())
    val readings: StateFlow<Map<String, SensorReading>> = _readings.asStateFlow()

    private val _dtcState = MutableStateFlow(DtcState())
    val dtcState: StateFlow<DtcState> = _dtcState.asStateFlow()

    private val _imReadiness = MutableStateFlow<ImReadinessResult?>(null)
    val imReadiness: StateFlow<ImReadinessResult?> = _imReadiness.asStateFlow()

    private val _freezeFrame = MutableStateFlow<FreezeFrameData?>(null)
    val freezeFrame: StateFlow<FreezeFrameData?> = _freezeFrame.asStateFlow()

    private val _vehicleInfo = MutableStateFlow(VehicleInfoState())
    val vehicleInfo: StateFlow<VehicleInfoState> = _vehicleInfo.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _aiAnalysisState.asStateFlow()

    private val _voiceAssistantState = MutableStateFlow<VoiceAssistantState>(VoiceAssistantState.Idle)
    val voiceAssistantState: StateFlow<VoiceAssistantState> = _voiceAssistantState.asStateFlow()

    private val _supportedMode01Pids = MutableStateFlow<Set<String>?>(null)
    val supportedMode01Pids: StateFlow<Set<String>?> = _supportedMode01Pids.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<Elm327BenchmarkResult?>(null)
    val benchmarkResult: StateFlow<Elm327BenchmarkResult?> = _benchmarkResult.asStateFlow()

    private val _isRunningBenchmark = MutableStateFlow(false)
    val isRunningBenchmark: StateFlow<Boolean> = _isRunningBenchmark.asStateFlow()

    private val _usbDevices = MutableStateFlow<List<UsbSerialDeviceItem>>(emptyList())
    val usbDevices: StateFlow<List<UsbSerialDeviceItem>> = _usbDevices.asStateFlow()

    private val _cloudSyncState = MutableStateFlow(
        CloudSyncState(user = cloudAuthManager.currentUserSummary())
    )
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _prePurchaseResult = MutableStateFlow<PrePurchaseInspectionResult?>(null)
    val prePurchaseResult: StateFlow<PrePurchaseInspectionResult?> = _prePurchaseResult.asStateFlow()

    private val _batteryTestState = MutableStateFlow(BatteryTestState())
    val batteryTestState: StateFlow<BatteryTestState> = _batteryTestState.asStateFlow()

    private val _photoAnalysisState = MutableStateFlow<PhotoAnalysisState>(PhotoAnalysisState.Idle)
    val photoAnalysisState: StateFlow<PhotoAnalysisState> = _photoAnalysisState.asStateFlow()

    private val _aiVehicleSuggestionState = MutableStateFlow<AiVehicleSuggestionState>(AiVehicleSuggestionState.Idle)
    val aiVehicleSuggestionState: StateFlow<AiVehicleSuggestionState> = _aiVehicleSuggestionState.asStateFlow()

    private val _terminalEntries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

    val tripStats: StateFlow<TripStats> = tripManager.tripStats
    val accelTimer: StateFlow<AccelTimerState> = tripManager.accelTimer
    val dataloggerState: StateFlow<DataloggerState> = datalogger.state
    val gpsSpeedState: StateFlow<GpsSpeedState> = gpsSpeedMonitor.state
    val logEntries: StateFlow<List<ObdLogEntry>> = sessionLogger.entries

    val settings: StateFlow<ObdUserSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ObdUserSettings()
    )

    val customPids: StateFlow<List<CustomPidEntity>> = customPidDao.observeCustomPids().stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val vehicleProfiles: StateFlow<List<VehicleProfileEntity>> = vehicleProfileDao.observeProfiles().stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val maintenanceItems: StateFlow<List<MaintenanceItemEntity>> = combine(
        maintenanceDao.observeAll(),
        settings,
        vehicleProfiles
    ) { all, userSettings, profiles ->
        val activeId = userSettings.activeVehicleProfileId ?: profiles.firstOrNull()?.id
        if (activeId == null) emptyList() else all.filter { it.vehicleId == activeId }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val tripHistory = tripRecordDao.observeTrips()
    val accelerationRuns = accelerationRunDao.observeRuns()

    val sensors: StateFlow<List<TelemetrySensor>> = combine(
        customPidDao.observeCustomPids(),
        settingsDataStore.settingsFlow
    ) { customList, userSettings ->
        val rawSensors = ObdPid.entries.map { it.toTelemetrySensor(userSettings.isEnglish) } +
            customList.filter { it.enabled }.map { it.toTelemetrySensor() }
        rawSensors.map { sensor -> applyAlertOverride(sensor, userSettings) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ObdPid.entries.map { it.toTelemetrySensor() }
    )

    val recentScans = diagnosticScanDao.observeRecentScans()

    private var activeTransport: ObdTransport? = null
    private var pollingJob: Job? = null
    private var aiJob: Job? = null
    private var voiceJob: Job? = null
    private var batteryTestJob: Job? = null
    private var photoJob: Job? = null
    private var prePurchaseJob: Job? = null
    private var terminalEntrySeq = 0L
    private val unsupportedSensorIds = mutableSetOf<String>()
    private var simTick = 0
    private var tripStartTimestampMs: Long = System.currentTimeMillis()
    @Volatile
    private var currentTripSavedToHistory: Boolean = false
    @Volatile
    private var activeCanHeader: String = "7DF"
    @Volatile
    private var userManuallyDisconnected: Boolean = false
    @Volatile
    private var pendingUsbAutoConnectId: Int? = null
    private var widgetPushCounter: Int = 0

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != UsbObdHelper.ACTION_USB_PERMISSION) return
            refreshUsbDevices()
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val pendingId = pendingUsbAutoConnectId
            if (granted && pendingId != null) {
                pendingUsbAutoConnectId = null
                connectUsbDevice(pendingId)
            } else if (!granted) {
                pendingUsbAutoConnectId = null
                sessionLogger.warn("USB", "Доступ к USB OTG адаптеру не предоставлен")
            }
        }
    }

    init {
        runCatching {
            val filter = IntentFilter(UsbObdHelper.ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(
                context,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        refreshUsbDevices()
        scope.launch {
            seedDefaultVehicleProfileIfEmpty()
            delay(400L)
            pushWidgetSnapshot()
        }
    }

    private suspend fun seedDefaultVehicleProfileIfEmpty() {
        val existing = vehicleProfileDao.getAll()
        if (existing.isEmpty()) {
            val isEng = settings.value.isEnglish
            val defaultId = vehicleProfileDao.insert(
                VehicleProfileEntity(
                    name = if (isEng) "My Car 2.0L" else "Мой автомобиль 2.0 л",
                    vin = null,
                    displacementLiters = 2.0f,
                    fuelType = FuelType.PETROL.name,
                    redlineRpm = 6500,
                    shiftLightRpm = 6000,
                    fuelPricePerLiter = 58.0f,
                    preferredPresetId = ObdUserSettings.DEFAULT_PRESET_ID
                )
            )
            settingsDataStore.setActiveVehicleProfileId(defaultId)
            seedDefaultMaintenanceIfEmpty(defaultId, "profile_$defaultId", isEng)
        } else {
            val first = existing.first()
            seedDefaultMaintenanceIfEmpty(first.id, first.syncVehicleKey, settings.value.isEnglish)
        }
    }

    private fun activeVehicleProfile(): VehicleProfileEntity? {
        val list = vehicleProfiles.value
        val activeId = settings.value.activeVehicleProfileId
        return list.find { it.id == activeId } ?: list.firstOrNull()
    }

    fun refreshUsbDevices() {
        _usbDevices.value = UsbObdHelper.enumerateAdapters(context)
    }

    fun requestUsbPermission(deviceId: Int) {
        val dev = UsbObdHelper.findDeviceById(context, deviceId) ?: return
        UsbObdHelper.requestUsbPermission(context, dev)
    }

    fun connectUsbDevice(deviceId: Int) {
        refreshUsbDevices()
        val usbDevice = UsbObdHelper.findDeviceById(context, deviceId)
        if (usbDevice == null) {
            sessionLogger.warn("USB", "USB OTG устройство #$deviceId не найдено")
            return
        }
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (manager == null || !manager.hasPermission(usbDevice)) {
            pendingUsbAutoConnectId = deviceId
            UsbObdHelper.requestUsbPermission(context, usbDevice)
            return
        }
        val family = UsbObdHelper.identifyChipFamily(usbDevice) ?: "USB ELM327"
        val label = usbDevice.productName?.takeIf { it.isNotBlank() } ?: "USB ELM327 ($family)"
        connectToDevice(
            BluetoothDeviceItem(
                name = label,
                address = "USB:$deviceId",
                transport = BluetoothTransportType.USB_SERIAL,
                isPaired = true,
                isLikelyObd = true
            )
        )
    }

    fun handleBluetoothAclConnected(deviceAddress: String?, deviceName: String?) {
        val currentConn = _connectionState.value
        val currentSettings = settings.value
        if (!currentSettings.autoConnectOnBtCar) return
        if (currentConn.isActiveTelemetry || currentConn is ObdConnectionState.Connecting) return
        userManuallyDisconnected = false
        if (!bluetoothScanner.hasBluetoothPermissions() || !bluetoothScanner.isBluetoothEnabled()) return

        val paired = bluetoothScanner.getPairedDevices()
        val lastAddr = currentSettings.lastDeviceAddress
        val matched = when {
            !deviceAddress.isNullOrBlank() && paired.any { it.address == deviceAddress && it.isLikelyObd } ->
                paired.first { it.address == deviceAddress }
            !lastAddr.isNullOrBlank() ->
                paired.find { it.address == lastAddr } ?: BluetoothDeviceItem(
                    name = currentSettings.lastDeviceName ?: "OBD2 ELM327",
                    address = lastAddr,
                    transport = currentSettings.lastTransport,
                    isPaired = true,
                    isLikelyObd = true
                )
            else -> paired.firstOrNull { it.isLikelyObd }
        } ?: return

        sessionLogger.info(
            "BT_CAR",
            "BT-триггер ГУ (${deviceName ?: deviceAddress ?: "Car"}): автоподключение к ${matched.name}"
        )
        connectToDevice(matched)
    }

    fun tryAutoConnect(reason: String) {
        val currentConn = _connectionState.value
        val currentSettings = settings.value
        if (currentConn.isActiveTelemetry || currentConn is ObdConnectionState.Connecting) return
        if (userManuallyDisconnected || !currentSettings.autoConnectOnLaunch) return

        if (currentSettings.lastTransport == BluetoothTransportType.USB_SERIAL) {
            refreshUsbDevices()
            val permittedUsb = _usbDevices.value.firstOrNull { it.hasPermission } ?: return
            sessionLogger.info("AUTO_CONN", "Автоподключение USB OTG ($reason): ${permittedUsb.productName}")
            connectUsbDevice(permittedUsb.deviceId)
            return
        }

        if (currentSettings.lastTransport == BluetoothTransportType.WIFI_TCP) {
            val host = currentSettings.wifiHost
            val port = currentSettings.wifiPort
            sessionLogger.info("AUTO_CONN", "Автоподключение Wi-Fi ($reason) к $host:$port")
            connectWifi(host, port)
            return
        }

        if (!bluetoothScanner.hasBluetoothPermissions() || !bluetoothScanner.isBluetoothEnabled()) return

        val paired = bluetoothScanner.getPairedDevices()
        val lastAddr = currentSettings.lastDeviceAddress
        val lastName = currentSettings.lastDeviceName
        val targetDevice = when {
            lastAddr != null -> {
                paired.find { it.address == lastAddr } ?: BluetoothDeviceItem(
                    name = lastName ?: "OBD2 ELM327",
                    address = lastAddr,
                    transport = currentSettings.lastTransport,
                    isPaired = true,
                    isLikelyObd = true
                )
            }
            else -> paired.firstOrNull { it.isLikelyObd }
        } ?: return

        sessionLogger.info("AUTO_CONN", "Автоподключение ($reason) к ${targetDevice.name} (${targetDevice.address})")
        connectToDevice(targetDevice)
    }

    fun connectWifi(host: String, port: Int) {
        val cleanHost = host.trim().ifBlank { "192.168.0.10" }
        val cleanPort = port.coerceIn(1, 65535)
        scope.launch {
            settingsDataStore.saveWifiConfig(cleanHost, cleanPort)
        }
        val wifiDevice = BluetoothDeviceItem(
            name = "Wi-Fi ELM327 ($cleanHost:$cleanPort)",
            address = "$cleanHost:$cleanPort",
            transport = BluetoothTransportType.WIFI_TCP,
            isPaired = true,
            isLikelyObd = true
        )
        connectToDevice(wifiDevice)
    }

    fun connectToDevice(device: BluetoothDeviceItem) {
        userManuallyDisconnected = false
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val isEng = settings.value.isEnglish
            val maxAttempts = if (settings.value.autoReconnectOnDrop) 3 else 1
            var attempt = 0

            while (isActive && !userManuallyDisconnected && attempt < maxAttempts) {
                attempt++
                disconnectInternal(resetReadings = attempt == 1)
                if (attempt == 1) {
                    tripManager.resetTrip()
                    tripStartTimestampMs = System.currentTimeMillis()
                    currentTripSavedToHistory = false
                }
                sessionLogger.info(
                    "CONN",
                    "Попытка #$attempt подключения к '${device.name}' (${device.address}, ${device.transport.name})"
                )
                _connectionState.value = ObdConnectionState.Connecting(
                    deviceName = device.name,
                    step = when (device.transport) {
                        BluetoothTransportType.WIFI_TCP ->
                            if (isEng) "Connecting via Wi-Fi TCP..." else "Подключение по Wi-Fi TCP..."
                        BluetoothTransportType.USB_SERIAL ->
                            if (isEng) "Opening USB OTG serial port..." else "Открытие USB OTG Serial-порта..."
                        else ->
                            if (isEng) "Establishing Bluetooth link..." else "Установка Bluetooth-соединения..."
                    }
                )

                val transport: ObdTransport = when (device.transport) {
                    BluetoothTransportType.CLASSIC_SPP -> {
                        val btAdapter = bluetoothScanner.bluetoothAdapter
                        if (btAdapter == null || !bluetoothScanner.isBluetoothEnabled()) {
                            val msg = if (isEng) {
                                "Bluetooth is off. Please enable Bluetooth on your phone."
                            } else {
                                "Bluetooth выключен. Включите Bluetooth на телефоне."
                            }
                            sessionLogger.warn("CONN", msg)
                            _connectionState.value = ObdConnectionState.Error(msg, device.name)
                            return@launch
                        }
                        BluetoothClassicObdTransport(btAdapter, device.address, sessionLogger)
                    }
                    BluetoothTransportType.BLE -> {
                        val btAdapter = bluetoothScanner.bluetoothAdapter
                        if (btAdapter == null || !bluetoothScanner.isBluetoothEnabled()) {
                            val msg = if (isEng) {
                                "Bluetooth is off. Please enable Bluetooth on your phone."
                            } else {
                                "Bluetooth выключен. Включите Bluetooth на телефоне."
                            }
                            sessionLogger.warn("CONN", msg)
                            _connectionState.value = ObdConnectionState.Error(msg, device.name)
                            return@launch
                        }
                        BleObdTransport(context, btAdapter, device.address, sessionLogger)
                    }
                    BluetoothTransportType.WIFI_TCP -> {
                        val parts = device.address.split(":")
                        val host = parts.getOrNull(0)?.ifBlank { "192.168.0.10" } ?: "192.168.0.10"
                        val port = parts.getOrNull(1)?.toIntOrNull() ?: 35000
                        WifiObdTransport(host, port, sessionLogger)
                    }
                    BluetoothTransportType.USB_SERIAL -> {
                        val usbId = device.address.removePrefix("USB:").toIntOrNull()
                        val usbDev = usbId?.let { UsbObdHelper.findDeviceById(context, it) }
                            ?: run {
                                refreshUsbDevices()
                                _usbDevices.value.firstOrNull { it.hasPermission }?.deviceId
                                    ?.let { UsbObdHelper.findDeviceById(context, it) }
                            }
                        if (usbDev == null) {
                            val msg = if (isEng) "USB OTG ELM327 adapter disconnected"
                            else "USB OTG ELM327 адаптер отключён"
                            sessionLogger.warn("USB", msg)
                            _connectionState.value = ObdConnectionState.Error(msg, device.name)
                            return@launch
                        }
                        UsbObdTransport(context, usbDev, sessionLogger)
                    }
                }
                activeTransport = transport

                try {
                    transport.connect()

                    _connectionState.value = ObdConnectionState.Connecting(
                        deviceName = device.name,
                        step = if (isEng) "Initializing ELM327, VIN & PID map..." else "Инициализация ELM327, VIN и PID..."
                    )

                    val protocol = initializeElm327(transport)
                    settingsDataStore.saveLastDevice(device.address, device.name, device.transport)
                    unsupportedSensorIds.clear()

                    readVehicleIdentityInternal(transport)

                    sessionLogger.info("CONN", "Успешное подключение к ${device.name}, протокол: $protocol")
                    _connectionState.value = ObdConnectionState.Connected(
                        deviceName = device.name,
                        address = device.address,
                        protocol = protocol,
                        transport = device.transport,
                        pollingHz = 0f
                    )
                    pushWidgetSnapshot()
                    startForegroundService()

                    readDtcsInternal(transport, device.name, protocol)
                    attempt = 0 // Reset retry counter once healthy session runs
                    runRealPollingLoop(transport, device, protocol)

                    if (userManuallyDisconnected || !settings.value.autoReconnectOnDrop) {
                        autoSaveTripIfQualifies()
                        break
                    }
                    sessionLogger.warn("CONN", "Связь прервана, автопереподключение через 2.5 с...")
                    delay(2500L)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    sessionLogger.error("CONN", "Ошибка подключения к ${device.name}", e)
                    disconnectInternal(resetReadings = false)
                    if (userManuallyDisconnected || !settings.value.autoReconnectOnDrop || attempt >= maxAttempts) {
                        autoSaveTripIfQualifies()
                        _connectionState.value = ObdConnectionState.Error(
                            message = e.message ?: if (isEng) "Failed to connect to OBD2 adapter" else "Ошибка подключения к OBD2 адаптеру",
                            deviceName = device.name
                        )
                        pushWidgetSnapshot()
                        break
                    }
                    delay(2500L)
                }
            }
        }
    }

    fun startSimulationMode() {
        userManuallyDisconnected = false
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val isEng = settings.value.isEnglish
            disconnectInternal(resetReadings = true)
            tripManager.resetTrip()
            tripStartTimestampMs = System.currentTimeMillis()
            currentTripSavedToHistory = false
            _supportedMode01Pids.value = ObdPid.entries.map { it.modeAndPid }.toSet()
            _vehicleInfo.value = VehicleInfoState(
                vin = "JTDKB20U893514829",
                calibrationId = "89663-02E41",
                matchedProfileName = activeVehicleProfile()?.name
            )
            _imReadiness.value = Elm327Parser.parseImReadiness("41 01 00 07 E5 00")
            _freezeFrame.value = null
            sessionLogger.info("SIM", "Активирован режим симуляции OBD2")
            _connectionState.value = ObdConnectionState.Simulation(pollingHz = 2.8f)
            _dtcState.value = DtcState(
                milOn = false,
                ecuReportedCount = 0,
                codes = emptyList(),
                pendingCodes = emptyList(),
                mode06Tests = buildSimulatedMode06Tests(hasWarning = false),
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = if (isEng) "Simulation mode: no fault codes" else "Режим симуляции: неисправности отсутствуют"
            )
            pushWidgetSnapshot()
            if (bluetoothScanner.hasBluetoothPermissions()) {
                startForegroundService()
            }

            simTick = 0
            while (isActive) {
                val interval = settings.value.pollingIntervalMs.coerceIn(180L, 1200L)
                updateSimulatedReadings(simTick++)
                val hz = 1000f / interval.toFloat()
                _connectionState.value = ObdConnectionState.Simulation(pollingHz = hz)
                delay(interval)
            }
        }
    }

    fun disconnect() {
        userManuallyDisconnected = true
        pollingJob?.cancel()
        pollingJob = null
        scope.launch {
            sessionLogger.info("CONN", "Отключение от OBD2 адаптера пользователем")
            autoSaveTripIfQualifies()
            disconnectInternal(resetReadings = false)
            _connectionState.value = ObdConnectionState.Disconnected
            pushWidgetSnapshot()
            stopForegroundService()
        }
    }

    fun scanDtcs() {
        scope.launch {
            val isEng = settings.value.isEnglish
            val state = _connectionState.value
            if (state is ObdConnectionState.Simulation) {
                _dtcState.update {
                    it.copy(
                        isScanning = true,
                        statusMessage = if (isEng) "Scanning ECU (Mode 03/06/07 sim)..." else "Сканирование ЭБУ (Mode 03/06/07 тест)..."
                    )
                }
                delay(700)
                val demoCodes = listOf(
                    Elm327Parser.decodeDtc("P0171", isEng),
                    Elm327Parser.decodeDtc("P0420", isEng)
                )
                val demoPending = listOf(
                    Elm327Parser.decodeDtc("P0133", isEng)
                )
                val demoMode06 = buildSimulatedMode06Tests(hasWarning = true)
                _imReadiness.value = Elm327Parser.parseImReadiness("41 01 82 07 65 20")
                _freezeFrame.value = FreezeFrameData(
                    dtcCode = "P0171",
                    rpm = 2480f,
                    speedKmh = 76f,
                    coolantTempC = 88f,
                    engineLoadPct = 54f,
                    stftPct = 18.4f,
                    ltftPct = 14.1f,
                    intakeMapKpa = 68f,
                    isSimulated = true
                )
                val result = DtcState(
                    milOn = true,
                    ecuReportedCount = demoCodes.size,
                    codes = demoCodes,
                    pendingCodes = demoPending,
                    mode06Tests = demoMode06,
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = if (isEng) {
                        "Active: ${demoCodes.size}, Pending: ${demoPending.size} (sim)"
                    } else {
                        "Активных: ${demoCodes.size}, Ожидающих: ${demoPending.size} (тест)"
                    }
                )
                _dtcState.value = result
                pushWidgetSnapshot()
                sessionLogger.info("DTC", "Симуляция Mode 03/07/06: DTC=${demoCodes.joinToString { it.code }}, Pending=${demoPending.joinToString { it.code }}")
                diagnosticScanDao.insertScan(
                    DiagnosticScanEntity(
                        timestampMs = System.currentTimeMillis(),
                        adapterName = if (isEng) "OBD2 Simulation" else "Симуляция OBD2",
                        protocol = "ISO 15765-4 CAN (Demo)",
                        milOn = true,
                        dtcCount = demoCodes.size,
                        dtcSummary = demoCodes.joinToString(", ") { it.code },
                        batteryVoltage = _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value
                    )
                )
                return@launch
            }

            val transport = activeTransport
            if (state !is ObdConnectionState.Connected || transport == null || !transport.isConnected) {
                _dtcState.update {
                    it.copy(
                        isScanning = false,
                        statusMessage = if (isEng) "Connect an OBD2 adapter to read fault codes" else "Подключите OBD2 адаптер для чтения ошибок"
                    )
                }
                return@launch
            }
            readDtcsInternal(transport, state.deviceName, state.protocol)
        }
    }

    fun scanFreezeFrame() {
        scope.launch {
            val state = _connectionState.value
            if (state is ObdConnectionState.Simulation) {
                _freezeFrame.value = FreezeFrameData(
                    dtcCode = _dtcState.value.codes.firstOrNull()?.code ?: "P0171",
                    rpm = 2480f,
                    speedKmh = 76f,
                    coolantTempC = 88f,
                    engineLoadPct = 54f,
                    stftPct = 18.4f,
                    ltftPct = 14.1f,
                    intakeMapKpa = 68f,
                    isSimulated = true
                )
                return@launch
            }
            val transport = activeTransport
            if (state is ObdConnectionState.Connected && transport != null && transport.isConnected) {
                readFreezeFrameInternal(transport)
            }
        }
    }

    fun clearDtcs() {
        scope.launch {
            val isEng = settings.value.isEnglish
            val state = _connectionState.value
            if (state is ObdConnectionState.Simulation) {
                _dtcState.update {
                    it.copy(
                        isScanning = true,
                        statusMessage = if (isEng) "Clearing fault codes (simulation)..." else "Сброс ошибок (симуляция)..."
                    )
                }
                delay(500)
                _dtcState.value = DtcState(
                    milOn = false,
                    ecuReportedCount = 0,
                    codes = emptyList(),
                    pendingCodes = emptyList(),
                    mode06Tests = buildSimulatedMode06Tests(hasWarning = false),
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = if (isEng) "ECU fault codes cleared (simulation)" else "Ошибки ЭБУ сброшены (симуляция)"
                )
                _freezeFrame.value = null
                _imReadiness.value = Elm327Parser.parseImReadiness("41 01 00 07 E5 65")
                pushWidgetSnapshot()
                sessionLogger.info("DTC", "Симуляция сброса DTC")
                return@launch
            }

            val transport = activeTransport ?: return@launch
            if (!transport.isConnected) return@launch
            _dtcState.update {
                it.copy(
                    isScanning = true,
                    statusMessage = if (isEng) "Sending Mode 04 command..." else "Отправка команды Mode 04..."
                )
            }
            try {
                sessionLogger.info("DTC", "Отправка команды очистки Mode 04")
                commandMutex.withLock {
                    ensureCanHeader(transport, "7DF")
                    transport.sendCommand("04", timeoutMs = 4000L)
                }
                _freezeFrame.value = null
                delay(300)
                readDtcsInternal(
                    transport,
                    (state as? ObdConnectionState.Connected)?.deviceName ?: "ELM327",
                    (state as? ObdConnectionState.Connected)?.protocol ?: "OBD2"
                )
                _dtcState.update {
                    it.copy(
                        statusMessage = if (isEng) "ECU trouble code memory cleared" else "Память ошибок ЭБУ очищена"
                    )
                }
            } catch (e: Exception) {
                sessionLogger.error("DTC", "Ошибка сброса DTC", e)
                _dtcState.update {
                    it.copy(
                        isScanning = false,
                        statusMessage = if (isEng) "DTC clear error: ${e.message}" else "Ошибка сброса DTC: ${e.message}"
                    )
                }
            }
        }
    }

    fun runAiFaultAnalysis() {
        aiJob?.cancel()
        aiJob = scope.launch {
            val isEng = settings.value.isEnglish
            val profile = activeVehicleProfile()
            val vehicleLabel = profile?.name ?: (if (isEng) "Passenger Car" else "Автомобиль")
            val fuelLabel = profile?.fuelTypeEnum?.localizedLabel(isEng) ?: FuelType.PETROL.localizedLabel(isEng)
            sessionLogger.info("AI_DIAG", "Запуск AI-разбора диагностики (DTC=${_dtcState.value.codes.size})")
            aiAssistant.analyzeDiagnosticsStream(
                dtcState = _dtcState.value,
                freezeFrame = _freezeFrame.value,
                imReadiness = _imReadiness.value,
                vehicleInfo = _vehicleInfo.value,
                vehicleLabel = vehicleLabel,
                fuelTypeLabel = fuelLabel,
                readings = _readings.value,
                isEnglish = isEng
            ).collect { state ->
                _aiAnalysisState.value = state
            }
        }
    }

    fun clearAiAnalysis() {
        aiJob?.cancel()
        _aiAnalysisState.value = AiAnalysisState.Idle
    }

    // --- Trip Computer & Performance Timer ---

    fun resetTrip() {
        tripManager.resetTrip()
        tripStartTimestampMs = System.currentTimeMillis()
        currentTripSavedToHistory = false
    }

    fun saveCurrentTripToHistory() {
        scope.launch {
            persistCurrentTrip(markSaved = true)
            pushWidgetSnapshot()
        }
    }

    private suspend fun autoSaveTripIfQualifies() {
        if (currentTripSavedToHistory || !settings.value.autoSaveTripOnDisconnect) return
        val stats = tripManager.tripStats.value
        val activeProfile = activeVehicleProfile()
        if (activeProfile != null && (stats.distanceKm >= 0.05f || stats.elapsedSec >= 30L)) {
            vehicleProfileDao.addMileageAndHours(
                id = activeProfile.id,
                deltaKm = stats.distanceKm,
                deltaHours = stats.elapsedSec.toFloat() / 3600f
            )
        }
        if (stats.distanceKm >= 0.2f || stats.elapsedSec >= 60L) {
            persistCurrentTrip(markSaved = false)
            sessionLogger.info(
                "TRIP",
                "Автосохранение поездки при завершении: ${String.format(Locale.US, "%.2f", stats.distanceKm)} км"
            )
        }
    }

    private suspend fun persistCurrentTrip(markSaved: Boolean) {
        if (currentTripSavedToHistory) return
        currentTripSavedToHistory = true
        val stats = tripManager.tripStats.value
        val isEng = settings.value.isEnglish
        val activeProfile = activeVehicleProfile()
        val profileName = activeProfile?.name ?: (if (isEng) "My Car" else "Автомобиль")
        tripRecordDao.insert(
            TripRecordEntity(
                vehicleName = profileName,
                startedAtMs = tripStartTimestampMs,
                endedAtMs = System.currentTimeMillis(),
                distanceKm = stats.distanceKm,
                durationSec = stats.elapsedSec,
                fuelUsedLiters = stats.fuelUsedLiters,
                avgLPer100Km = stats.avgLPer100Km,
                avgSpeedKmh = stats.avgSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                estimatedCost = stats.estimatedCost,
                ecoScore = stats.ecoScore,
                peakHorsepower = stats.peakHorsepower,
                harshEventsCount = stats.harshEventsCount,
                coldRevSec = stats.coldRevSeconds
            )
        )
        if (markSaved && activeProfile != null && (stats.distanceKm >= 0.05f || stats.elapsedSec >= 10L)) {
            vehicleProfileDao.addMileageAndHours(
                id = activeProfile.id,
                deltaKm = stats.distanceKm,
                deltaHours = stats.elapsedSec.toFloat() / 3600f
            )
        }
        if (markSaved) {
            sessionLogger.info("TRIP", "Поездка сохранена: ${String.format(Locale.US, "%.2f", stats.distanceKm)} км")
        }
    }

    fun deleteTripRecord(id: Long) {
        scope.launch { tripRecordDao.deleteById(id) }
    }

    fun clearTripHistory() {
        scope.launch { tripRecordDao.clearAll() }
    }

    fun buildTripsCsv(trips: List<TripRecordEntity>): String {
        val header = "id,vehicle,startedAtMs,endedAtMs,distanceKm,durationSec,fuelLiters,avgL100Km,avgSpeedKmh,maxSpeedKmh,cost"
        val rows = trips.joinToString("\n") { t ->
            listOf(
                t.id,
                "\"${t.vehicleName.replace("\"", "'")}\"",
                t.startedAtMs,
                t.endedAtMs,
                String.format(Locale.US, "%.2f", t.distanceKm),
                t.durationSec,
                String.format(Locale.US, "%.2f", t.fuelUsedLiters),
                t.avgLPer100Km?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                String.format(Locale.US, "%.1f", t.avgSpeedKmh),
                String.format(Locale.US, "%.1f", t.maxSpeedKmh),
                String.format(Locale.US, "%.2f", t.estimatedCost)
            ).joinToString(",")
        }
        return "$header\n$rows"
    }

    fun selectAccelDiscipline(discipline: AccelDiscipline) {
        tripManager.selectAccelDiscipline(discipline)
    }

    fun armAccelTimer() {
        tripManager.armAccelTimer()
    }

    fun resetAccelTimer() {
        tripManager.resetAccelTimer()
    }

    fun clearAccelRuns() {
        scope.launch { accelerationRunDao.clearAll() }
    }

    // --- Vehicle Garage Profiles ---

    fun selectVehicleProfile(profileId: Long) {
        scope.launch {
            settingsDataStore.setActiveVehicleProfileId(profileId)
            val profile = vehicleProfiles.value.find { it.id == profileId }
            if (profile != null) {
                settingsDataStore.setShiftLightRpm(profile.shiftLightRpm)
                _vehicleInfo.update { it.copy(matchedProfileName = profile.name) }
                seedDefaultMaintenanceIfEmpty(profile.id, profile.syncVehicleKey, settings.value.isEnglish)
                sessionLogger.info("GARAGE", "Выбран профиль автомобиля: ${profile.name}")
                pushWidgetSnapshot()
            }
        }
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
        scope.launch {
            val cleanName = name.trim().ifBlank { "Auto" }
            val cleanVin = vin?.trim()?.uppercase()?.ifBlank { null }
            val currentExisting = existingId?.let { id -> vehicleProfiles.value.find { it.id == id } }
            val resolvedOdo = (odometerKm ?: currentExisting?.odometerKm ?: 0f).coerceIn(0f, 5_000_000f)
            val resolvedHours = currentExisting?.engineHours ?: 0f
            val entity = VehicleProfileEntity(
                id = existingId ?: 0L,
                name = cleanName,
                vin = cleanVin,
                displacementLiters = displacementLiters.coerceIn(0.6f, 8.0f),
                fuelType = fuelType.name,
                redlineRpm = redlineRpm.coerceIn(3000, 10000),
                shiftLightRpm = shiftLightRpm.coerceIn(2500, 9500),
                fuelPricePerLiter = fuelPricePerLiter.coerceIn(0f, 500f),
                preferredPresetId = preferredPresetId,
                odometerKm = resolvedOdo,
                engineHours = resolvedHours,
                curbWeightKg = curbWeightKg.coerceIn(600, 6000)
            )
            val id = if (existingId == null || existingId <= 0L) {
                vehicleProfileDao.insert(entity)
            } else {
                vehicleProfileDao.update(entity)
                existingId
            }
            val savedKey = cleanVin ?: "profile_$id"
            seedDefaultMaintenanceIfEmpty(id, savedKey, settings.value.isEnglish)
            settingsDataStore.setActiveVehicleProfileId(id)
            settingsDataStore.setShiftLightRpm(entity.shiftLightRpm)
            sessionLogger.info("GARAGE", "Сохранён профиль автомобиля: $cleanName")
            pushWidgetSnapshot()
        }
    }

    fun deleteVehicleProfile(profileId: Long) {
        scope.launch {
            maintenanceDao.deleteForVehicle(profileId)
            vehicleProfileDao.deleteById(profileId)
            val remaining = vehicleProfileDao.getAll()
            if (remaining.isEmpty()) {
                seedDefaultVehicleProfileIfEmpty()
            } else if (settings.value.activeVehicleProfileId == profileId) {
                settingsDataStore.setActiveVehicleProfileId(remaining.first().id)
            }
            pushWidgetSnapshot()
        }
    }

    // --- Backup JSON Export / Import ---

    fun exportConfigBackupJson(): String {
        return SettingsBackupManager.exportToJson(
            settings = settings.value,
            customPids = customPids.value,
            profiles = vehicleProfiles.value
        )
    }

    suspend fun importConfigBackupJson(rawJson: String): Result<Int> {
        val decoded = SettingsBackupManager.decodeBackup(rawJson)
        val payload = decoded.getOrElse { return Result.failure(it) }
        settingsDataStore.applyBackupPayload(payload)

        var importedCount = 0
        if (payload.customPids.isNotEmpty()) {
            val existingKeys = customPids.value.map { "${it.modeAndPid}|${it.equation}" }.toSet()
            val toInsert = payload.customPids
                .filter { "${it.modeAndPid}|${it.equation}" !in existingKeys }
                .map { dto ->
                    CustomPidEntity(
                        name = dto.name,
                        shortName = dto.shortName,
                        modeAndPid = dto.modeAndPid,
                        header = dto.header,
                        equation = dto.equation,
                        unit = dto.unit,
                        minValue = dto.minValue,
                        maxValue = dto.maxValue,
                        decimals = dto.decimals,
                        enabled = dto.enabled,
                        warningHigh = dto.warningHigh,
                        criticalHigh = dto.criticalHigh,
                        warningLow = dto.warningLow,
                        criticalLow = dto.criticalLow,
                        alertsEnabled = dto.alertsEnabled
                    )
                }
            if (toInsert.isNotEmpty()) {
                customPidDao.insertAll(toInsert)
                importedCount += toInsert.size
            }
        }
        if (payload.vehicleProfiles.isNotEmpty()) {
            val existingNames = vehicleProfiles.value.map { it.name.lowercase() }.toSet()
            val profilesToInsert = payload.vehicleProfiles
                .filter { it.name.lowercase() !in existingNames }
                .map { dto ->
                    VehicleProfileEntity(
                        name = dto.name,
                        vin = dto.vin,
                        displacementLiters = dto.displacementLiters,
                        fuelType = dto.fuelType,
                        redlineRpm = dto.redlineRpm,
                        shiftLightRpm = dto.shiftLightRpm,
                        fuelPricePerLiter = dto.fuelPricePerLiter,
                        preferredPresetId = dto.preferredPresetId,
                        curbWeightKg = dto.curbWeightKg.coerceIn(600, 6000)
                    )
                }
            if (profilesToInsert.isNotEmpty()) {
                vehicleProfileDao.insertAll(profilesToInsert)
                importedCount += profilesToInsert.size
            }
        }
        sessionLogger.info("BACKUP", "Импортирована резервная копия JSON (+$importedCount объектов)")
        return Result.success(importedCount)
    }

    // --- Probing, Benchmark, Settings ---

    suspend fun probeCustomPid(
        modeAndPid: String,
        header: String,
        equation: String
    ): LivePidProbeResult {
        val isEng = settings.value.isEnglish
        val cleanPid = TorqueEquationEvaluator.normalizeModeAndPid(modeAndPid)
        val cleanHeader = TorqueEquationEvaluator.normalizeHeader(header)
        val expectedHeader = TorqueEquationEvaluator.expectedResponseHeader(cleanPid)
        if (cleanPid.length < 4 || expectedHeader == null) {
            return LivePidProbeResult(
                isSuccess = false,
                modeAndPid = cleanPid,
                rawResponse = "",
                extractedBytes = emptyList(),
                evaluatedValue = null,
                latencyMs = 0L,
                errorMessage = if (isEng) {
                    "Enter a valid Mode+PID HEX code (at least 4 characters)."
                } else {
                    "Укажите корректный HEX-код Mode+PID (минимум 4 символа)."
                }
            )
        }
        val eqCheck = TorqueEquationEvaluator.validateEquation(equation, isEng)
        if (eqCheck.isFailure) {
            return LivePidProbeResult(
                isSuccess = false,
                modeAndPid = cleanPid,
                rawResponse = "",
                extractedBytes = emptyList(),
                evaluatedValue = null,
                latencyMs = 0L,
                errorMessage = eqCheck.exceptionOrNull()?.message ?: if (isEng) "Equation error" else "Ошибка в формуле"
            )
        }

        val conn = _connectionState.value
        if (conn is ObdConnectionState.Simulation) {
            delay(180)
            val simulatedBytes = when {
                cleanPid.endsWith("0C") -> listOf(0x14, 0xA0)
                cleanPid.endsWith("05") || cleanPid.endsWith("5C") || cleanPid.contains("1E1C") -> listOf(0x82, 0x10)
                cleanPid.endsWith("42") -> listOf(0x37, 0x8C)
                else -> listOf(0x4E, 0x20, 0x15, 0x80)
            }
            val hexJoined = simulatedBytes.joinToString(" ") { String.format("%02X", it) }
            val simulatedRaw = "${cleanHeader.ifBlank { "7E8" }} ${expectedHeader.chunked(2).joinToString(" ")} $hexJoined"
            val bytes = Elm327Parser.extractIsoTpPayload(expectedHeader, simulatedRaw) ?: simulatedBytes
            val eval = TorqueEquationEvaluator.evaluate(equation, bytes)
            sessionLogger.info("PROBE", "Тест PID $cleanPid (симуляция): $simulatedRaw -> $eval")
            return LivePidProbeResult(
                isSuccess = eval != null,
                modeAndPid = cleanPid,
                rawResponse = if (isEng) "$simulatedRaw (sim)" else "$simulatedRaw (симуляция)",
                extractedBytes = bytes,
                evaluatedValue = eval,
                latencyMs = 42L,
                errorMessage = if (eval == null) {
                    if (isEng) "Failed to evaluate equation" else "Не удалось вычислить формулу"
                } else null
            )
        }

        val transport = activeTransport
        if (conn !is ObdConnectionState.Connected || transport == null || !transport.isConnected) {
            return LivePidProbeResult(
                isSuccess = false,
                modeAndPid = cleanPid,
                rawResponse = "",
                extractedBytes = emptyList(),
                evaluatedValue = null,
                latencyMs = 0L,
                errorMessage = if (isEng) {
                    "Connect an OBD2 adapter or enable Simulation Mode to test ECU replies."
                } else {
                    "Подключите OBD2 адаптер или включите «Тест-режим» для проверки ответа ЭБУ."
                }
            )
        }

        return try {
            val targetHdr = cleanHeader.ifBlank { "7DF" }
            val (raw, latency) = commandMutex.withLock {
                ensureCanHeader(transport, targetHdr)
                val t0 = System.currentTimeMillis()
                val reply = transport.sendCommand(cleanPid, timeoutMs = 2800L)
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                reply to dt
            }
            val cleaned = Elm327Parser.cleanResponse(raw)
            if (Elm327Parser.isErrorResponse(raw) || cleaned.isBlank()) {
                sessionLogger.warn("PROBE", "Тест PID $cleanPid ($targetHdr) без ответа: $cleaned")
                LivePidProbeResult(
                    isSuccess = false,
                    modeAndPid = cleanPid,
                    rawResponse = cleaned.ifBlank { if (isEng) "Empty response" else "Пустой ответ" },
                    extractedBytes = emptyList(),
                    evaluatedValue = null,
                    latencyMs = latency,
                    errorMessage = if (isEng) {
                        "ECU returned error or NO DATA (${cleaned.ifBlank { "no reply" }})"
                    } else {
                        "ЭБУ вернул ошибку или NO DATA (${cleaned.ifBlank { "нет ответа" }})"
                    }
                )
            } else {
                val bytes = Elm327Parser.extractIsoTpPayload(expectedHeader, raw) ?: emptyList()
                val eval = if (bytes.isNotEmpty()) TorqueEquationEvaluator.evaluate(equation, bytes) else null
                sessionLogger.info("PROBE", "Тест PID $cleanPid ($targetHdr, ${latency}мс): $cleaned -> bytes=$bytes, val=$eval")
                LivePidProbeResult(
                    isSuccess = bytes.isNotEmpty() && eval != null,
                    modeAndPid = cleanPid,
                    rawResponse = cleaned,
                    extractedBytes = bytes,
                    evaluatedValue = eval,
                    latencyMs = latency,
                    errorMessage = when {
                        bytes.isEmpty() -> if (isEng) {
                            "Response received, but header $expectedHeader not found"
                        } else {
                            "Ответ получен, но заголовок $expectedHeader не найден"
                        }
                        eval == null -> if (isEng) {
                            "Failed to evaluate equation on bytes $bytes"
                        } else {
                            "Не удалось вычислить формулу по байтам $bytes"
                        }
                        else -> null
                    }
                )
            }
        } catch (e: Exception) {
            sessionLogger.error("PROBE", "Ошибка live-проверки PID $cleanPid", e)
            LivePidProbeResult(
                isSuccess = false,
                modeAndPid = cleanPid,
                rawResponse = "",
                extractedBytes = emptyList(),
                evaluatedValue = null,
                latencyMs = 0L,
                errorMessage = e.message ?: if (isEng) "Adapter communication error" else "Ошибка обмена с адаптером"
            )
        }
    }

    fun runAdapterBenchmark() {
        if (_isRunningBenchmark.value) return
        scope.launch {
            _isRunningBenchmark.value = true
            try {
                val conn = _connectionState.value
                val transport = activeTransport
                val result = when {
                    conn is ObdConnectionState.Simulation -> {
                        delay(650)
                        Elm327Benchmark.buildSimulatedBenchmark(isEnglish = settings.value.isEnglish)
                    }
                    conn is ObdConnectionState.Connected && transport != null && transport.isConnected -> {
                        sessionLogger.info("BENCH", "Запуск бенчмарка ELM327 (${conn.deviceName})")
                        val res = Elm327Benchmark.runRealBenchmark(
                            transport = transport,
                            commandMutex = commandMutex,
                            supportedMode01Count = _supportedMode01Pids.value?.size,
                            isEnglish = settings.value.isEnglish
                        )
                        activeCanHeader = "BENCH_UNKNOWN"
                        res
                    }
                    else -> {
                        sessionLogger.warn("BENCH", "Попытка запуска теста адаптера без подключения")
                        null
                    }
                }
                if (result != null) {
                    _benchmarkResult.value = result
                    sessionLogger.info(
                        "BENCH",
                        "Результат ELM327: ${result.adapterVersion}, ${result.avgLatencyMs} мс (${String.format("%.1f", result.maxPollingHz)} Гц) — ${result.qualityLabel}"
                    )
                }
            } catch (e: Exception) {
                sessionLogger.error("BENCH", "Ошибка бенчмарка адаптера", e)
            } finally {
                _isRunningBenchmark.value = false
            }
        }
    }

    fun toggleAndroidAutoSensor(sensorId: String) {
        scope.launch { settingsDataStore.toggleAndroidAutoSensor(sensorId) }
    }

    fun setAndroidAutoDisplayMode(mode: AndroidAutoDisplayMode) {
        scope.launch { settingsDataStore.setAndroidAutoDisplayMode(mode) }
    }

    fun setDialTheme(theme: CockpitDialTheme) {
        scope.launch { settingsDataStore.setDialTheme(theme) }
    }

    fun setShiftLightEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setShiftLightEnabled(enabled) }
    }

    fun setShiftLightRpm(rpm: Int) {
        scope.launch { settingsDataStore.setShiftLightRpm(rpm) }
    }

    fun selectCockpitPreset(presetId: String) {
        scope.launch {
            settingsDataStore.selectCockpitPreset(presetId)
            sessionLogger.info("PRESET", "Выбран пресет панели: $presetId")
        }
    }

    fun cycleNextCockpitPreset() {
        scope.launch {
            val next = settingsDataStore.cycleNextCockpitPreset()
            sessionLogger.info("PRESET", "Переключён пресет панели: ${next.name}")
        }
    }

    fun renameCockpitPreset(presetId: String, newName: String) {
        scope.launch { settingsDataStore.renameCockpitPreset(presetId, newName) }
    }

    fun setFullscreenDialSlot(slotIndex: Int, sensorId: String) {
        scope.launch { settingsDataStore.setFullscreenDialSlot(slotIndex, sensorId) }
    }

    fun setFullscreenStripSlot(slotIndex: Int, sensorId: String) {
        scope.launch { settingsDataStore.setFullscreenStripSlot(slotIndex, sensorId) }
    }

    fun resetFullscreenLayoutDefaults() {
        scope.launch { settingsDataStore.resetFullscreenLayoutDefaults() }
    }

    fun setAutoConnectOnLaunch(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoConnectOnLaunch(enabled) }
    }

    fun setAutoReconnectOnDrop(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoReconnectOnDrop(enabled) }
    }

    fun setFastSlowPollingEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setFastSlowPollingEnabled(enabled) }
    }

    fun setAutoConnectOnBtCar(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoConnectOnBtCar(enabled) }
    }

    fun setVoiceAlertsEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setVoiceAlertsEnabled(enabled) }
    }

    fun setAaPopupAlertsEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setAaPopupAlertsEnabled(enabled) }
    }

    fun preloadTtsVoices() {
        alertManager.preloadVoices()
    }

    fun setTtsVolume(volume: Float) {
        scope.launch { settingsDataStore.setTtsVolume(volume) }
    }

    fun setTtsSpeechRate(rate: Float) {
        scope.launch { settingsDataStore.setTtsSpeechRate(rate) }
    }

    fun setTtsVoiceName(voiceName: String?) {
        scope.launch { settingsDataStore.setTtsVoiceName(voiceName) }
    }

    fun setSensorAlertOverride(override: SensorAlertOverride) {
        scope.launch {
            settingsDataStore.setSensorAlertOverride(override)
            sessionLogger.info("ALERT", "Обновлены пороги оповещений для ${override.sensorId}")
        }
    }

    fun dismissActiveAlert() {
        alertManager.dismissActiveAlert()
    }

    fun triggerTestAlert() {
        alertManager.triggerTestAlert(settings.value)
    }

    fun markStartupPermissionsRequested() {
        scope.launch { settingsDataStore.markStartupPermissionsRequested() }
    }

    fun acceptSafetyDisclaimer() {
        scope.launch { settingsDataStore.acceptSafetyDisclaimer() }
    }

    fun setProPreviewEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setProPreviewEnabled(enabled) }
    }

    fun setAutoSaveTripOnDisconnect(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoSaveTripOnDisconnect(enabled) }
    }

    fun setFuelCalibrationFactor(factor: Float) {
        scope.launch { settingsDataStore.setFuelCalibrationFactor(factor) }
    }

    fun setSpeedCalibrationFactor(factor: Float) {
        scope.launch { settingsDataStore.setSpeedCalibrationFactor(factor) }
    }

    fun applyFuelFillupCalibration(actualLiters: Float, computerLiters: Float): Float? {
        if (actualLiters <= 0.1f || computerLiters <= 0.1f) return null
        val current = settings.value.fuelCalibrationFactor
        val updated = (current * (actualLiters / computerLiters)).coerceIn(0.70f, 1.35f)
        setFuelCalibrationFactor(updated)
        sessionLogger.info("CALIB", "Калибровка расхода по заправке: ${String.format(Locale.US, "%.3f", updated)}")
        return updated
    }

    fun startGpsSpeedCheck() {
        gpsSpeedMonitor.startTracking(settings.value.isEnglish)
    }

    fun stopGpsSpeedCheck() {
        gpsSpeedMonitor.stopTracking()
    }

    fun applySuggestedGpsSpeedFactor() {
        val suggested = gpsSpeedState.value.suggestedSpeedFactor ?: return
        setSpeedCalibrationFactor(suggested)
        sessionLogger.info("GPS", "Применён коэффициент скорости по GPS: ${String.format(Locale.US, "%.3f", suggested)}")
    }

    // --- Datalogger ---

    fun startDatalogger() {
        datalogger.startRecording()
        sessionLogger.info("LOG", "Старт записи чёрного ящика (Datalogger)")
    }

    fun stopDatalogger() {
        datalogger.stopRecording()
        sessionLogger.info("LOG", "Запись Datalogger остановлена (${dataloggerState.value.frames.size} кадров)")
    }

    fun clearDatalogger() {
        datalogger.clearRecording()
    }

    fun buildDataloggerCsv(): String {
        val s = settings.value
        return datalogger.exportCsv(
            sensors = sensors.value,
            useFahrenheit = s.useFahrenheit,
            pressureUnit = s.pressureUnit
        )
    }

    // --- OEM Brand PID Catalog ---

    suspend fun importOemPack(packId: String): Int {
        val pack = OemPidCatalog.findById(packId) ?: return 0
        val isEng = settings.value.isEnglish
        val existingKeys = customPids.value.map { "${it.modeAndPid}|${it.equation}" }.toSet()
        val toInsert = pack.pids
            .filter { "${it.modeAndPid}|${it.equation}" !in existingKeys }
            .map { spec ->
                CustomPidEntity(
                    name = spec.localizedName(isEng),
                    shortName = spec.shortName,
                    modeAndPid = spec.modeAndPid,
                    header = spec.header,
                    equation = spec.equation,
                    unit = spec.unit,
                    minValue = spec.minValue,
                    maxValue = spec.maxValue,
                    decimals = spec.decimals,
                    enabled = true,
                    warningHigh = spec.warningHigh,
                    criticalHigh = spec.criticalHigh,
                    alertsEnabled = spec.warningHigh != null || spec.criticalHigh != null
                )
            }
        if (toInsert.isNotEmpty()) {
            customPidDao.insertAll(toInsert)
            unsupportedSensorIds.clear()
            sessionLogger.info("OEM_PID", "Импортирован пакет ${pack.brandName}: +${toInsert.size} PID")
        }
        return toInsert.size
    }

    // --- Maintenance Tracker ---

    private suspend fun seedDefaultMaintenanceIfEmpty(
        vehicleId: Long,
        vehicleKey: String,
        isEnglish: Boolean
    ) {
        if (maintenanceDao.getForVehicle(vehicleId).isNotEmpty()) return
        val veh = vehicleProfileDao.getAll().find { it.id == vehicleId }
        val odo = veh?.odometerKm ?: 0f
        val hrs = veh?.engineHours ?: 0f
        val now = System.currentTimeMillis()
        val defaults = listOf(
            MaintenanceItemEntity(
                vehicleId = vehicleId,
                vehicleKey = vehicleKey,
                title = if (isEnglish) "Engine Oil & Filter" else "Масло ДВС и фильтр",
                category = "OIL",
                intervalKm = 8000,
                intervalHours = 250,
                lastServiceOdometerKm = odo,
                lastServiceEngineHours = hrs,
                lastServiceTimestampMs = now
            ),
            MaintenanceItemEntity(
                vehicleId = vehicleId,
                vehicleKey = vehicleKey,
                title = if (isEnglish) "Air & Cabin Filters" else "Воздушный и салонный фильтры",
                category = "FILTERS",
                intervalKm = 15000,
                intervalHours = 0,
                lastServiceOdometerKm = odo,
                lastServiceEngineHours = hrs,
                lastServiceTimestampMs = now
            ),
            MaintenanceItemEntity(
                vehicleId = vehicleId,
                vehicleKey = vehicleKey,
                title = if (isEnglish) "Brake Fluid & Pads Check" else "Тормозная жидкость и колодки",
                category = "BRAKES",
                intervalKm = 30000,
                intervalHours = 0,
                lastServiceOdometerKm = odo,
                lastServiceEngineHours = hrs,
                lastServiceTimestampMs = now
            ),
            MaintenanceItemEntity(
                vehicleId = vehicleId,
                vehicleKey = vehicleKey,
                title = if (isEnglish) "Spark / Glow Plugs" else "Свечи зажигания / накала",
                category = "IGNITION",
                intervalKm = 40000,
                intervalHours = 0,
                lastServiceOdometerKm = odo,
                lastServiceEngineHours = hrs,
                lastServiceTimestampMs = now
            )
        )
        maintenanceDao.insertAll(defaults)
    }

    fun addMaintenanceItem(
        title: String,
        category: String,
        intervalKm: Int,
        intervalHours: Int,
        notes: String?
    ) {
        scope.launch {
            val veh = activeVehicleProfile() ?: return@launch
            maintenanceDao.insert(
                MaintenanceItemEntity(
                    vehicleId = veh.id,
                    vehicleKey = veh.syncVehicleKey,
                    title = title.trim().ifBlank { "TO" },
                    category = category,
                    intervalKm = intervalKm.coerceIn(500, 300_000),
                    intervalHours = intervalHours.coerceIn(0, 10_000),
                    lastServiceOdometerKm = veh.odometerKm,
                    lastServiceEngineHours = veh.engineHours,
                    lastServiceTimestampMs = System.currentTimeMillis(),
                    notes = notes?.trim()?.ifBlank { null }
                )
            )
            pushWidgetSnapshot()
        }
    }

    fun markMaintenanceDone(itemId: Long) {
        scope.launch {
            val item = maintenanceDao.getAll().find { it.id == itemId } ?: return@launch
            val veh = vehicleProfileDao.getAll().find { it.id == item.vehicleId }
            maintenanceDao.update(
                item.copy(
                    lastServiceOdometerKm = veh?.odometerKm ?: item.lastServiceOdometerKm,
                    lastServiceEngineHours = veh?.engineHours ?: item.lastServiceEngineHours,
                    lastServiceTimestampMs = System.currentTimeMillis()
                )
            )
            sessionLogger.info("MAINT", "Отмечено выполненным ТО: ${item.title}")
            pushWidgetSnapshot()
        }
    }

    fun deleteMaintenanceItem(itemId: Long) {
        scope.launch {
            maintenanceDao.deleteById(itemId)
            pushWidgetSnapshot()
        }
    }

    // --- Voice AI Driver Assistant ---

    fun askVoiceDriverQuestion(question: String, speakReply: Boolean = true) {
        voiceJob?.cancel()
        voiceJob = scope.launch {
            val isEng = settings.value.isEnglish
            val profile = activeVehicleProfile()
            val vLabel = profile?.name ?: (if (isEng) "Car" else "Автомобиль")
            val fuelLabel = profile?.fuelTypeEnum?.localizedLabel(isEng) ?: FuelType.PETROL.localizedLabel(isEng)
            val currentOdo = profile?.odometerKm ?: 0f
            val maintSummary = maintenanceItems.value.joinToString("; ") { item ->
                val driven = (currentOdo - item.lastServiceOdometerKm).coerceAtLeast(0f)
                val rem = item.intervalKm - driven
                "${item.title}: remaining ${rem.toInt()} km"
            }

            _voiceAssistantState.value = VoiceAssistantState.Thinking(question)
            val result = aiAssistant.askDriverQuestion(
                question = question,
                dtcState = _dtcState.value,
                freezeFrame = _freezeFrame.value,
                vehicleLabel = vLabel,
                fuelTypeLabel = fuelLabel,
                readings = _readings.value,
                maintenanceSummary = maintSummary,
                isEnglish = isEng
            )
            result.onSuccess { answer ->
                _voiceAssistantState.value = VoiceAssistantState.Answered(question, answer)
                if (speakReply && settings.value.voiceAlertsEnabled) {
                    alertManager.speakText(answer, settings.value)
                }
            }.onFailure { err ->
                _voiceAssistantState.value = VoiceAssistantState.Error(
                    err.localizedMessage ?: if (isEng) "Voice assistant failed" else "Ошибка AI-ассистента"
                )
            }
        }
    }

    fun clearVoiceAssistantState() {
        voiceJob?.cancel()
        _voiceAssistantState.value = VoiceAssistantState.Idle
    }

    fun speakCarStatusSummary() {
        voiceJob?.cancel()
        voiceJob = scope.launch {
            val isEng = settings.value.isEnglish
            val profile = activeVehicleProfile()
            val vLabel = profile?.name ?: (if (isEng) "Car" else "Автомобиль")
            val currentOdo = profile?.odometerKm ?: 0f
            val maintSummary = maintenanceItems.value.joinToString("; ") { item ->
                val driven = (currentOdo - item.lastServiceOdometerKm).coerceAtLeast(0f)
                val rem = item.intervalKm - driven
                "${item.title}: remaining ${rem.toInt()} km"
            }
            val question = if (isEng) "Status summary" else "Сводка состояния"
            _voiceAssistantState.value = VoiceAssistantState.Thinking(question)
            aiAssistant.generateCarStatusSummary(
                vehicleLabel = vLabel,
                dtcState = _dtcState.value,
                readings = _readings.value,
                maintenanceSummary = maintSummary,
                isEnglish = isEng
            ).onSuccess { answer ->
                _voiceAssistantState.value = VoiceAssistantState.Answered(question, answer)
                alertManager.speakText(answer, settings.value)
            }.onFailure { err ->
                _voiceAssistantState.value = VoiceAssistantState.Error(
                    err.localizedMessage ?: if (isEng) "Summary error" else "Ошибка AI-сводки"
                )
            }
        }
    }

    // --- Pre-Purchase Inspection + PDF Report ---

    fun runPrePurchaseInspection() {
        prePurchaseJob?.cancel()
        prePurchaseJob = scope.launch {
            val isEng = settings.value.isEnglish
            val conn = _connectionState.value
            val profile = activeVehicleProfile()
            val vehicleName = profile?.name ?: (if (isEng) "Inspected Vehicle" else "Проверяемое авто")

            _prePurchaseResult.value = PrePurchaseInspectionResult(
                vehicleName = vehicleName,
                isRunning = true
            )

            try {
                var vin = _vehicleInfo.value.vin
                var calId = _vehicleInfo.value.calibrationId
                var im = _imReadiness.value
                var milOn = _dtcState.value.milOn
                var activeDtcs = _dtcState.value.codes.size
                var pendingDtcs = _dtcState.value.pendingCodes.size
                var m06Failed = _dtcState.value.mode06Tests.count { !it.passed }
                var distSinceClear: Int? = null
                var warmupsSinceClear: Int? = null
                var timeSinceClearMin: Int? = null
                var distMilOn: Int? = null
                val batteryV = _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value
                val coolantC = _readings.value[ObdPid.COOLANT_TEMP.modeAndPid]?.value

                val transport = activeTransport
                if (conn is ObdConnectionState.Simulation) {
                    delay(650)
                    vin = vin ?: "WVWZZZ3CZGE184920"
                    calId = calId ?: "04E906027HD-4920"
                    distSinceClear = if (milOn) 140 else 1840
                    warmupsSinceClear = if (milOn) 3 else 42
                    timeSinceClearMin = if (milOn) 95 else 2480
                    distMilOn = if (milOn) 118 else 0
                    im = im ?: Elm327Parser.parseImReadiness("41 01 00 07 E5 00")
                } else if (conn is ObdConnectionState.Connected && transport != null && transport.isConnected) {
                    readDtcsInternal(transport, conn.deviceName, conn.protocol)
                    val extra = commandMutex.withLock {
                        ensureCanHeader(transport, "7DF")
                        val r0902 = runCatching { transport.sendCommand("0902", timeoutMs = 3000L) }.getOrDefault("")
                        val r0904 = runCatching { transport.sendCommand("0904", timeoutMs = 2500L) }.getOrDefault("")
                        val r0121 = runCatching { transport.sendCommand("0121", timeoutMs = 1500L) }.getOrDefault("")
                        val r0130 = runCatching { transport.sendCommand("0130", timeoutMs = 1500L) }.getOrDefault("")
                        val r0131 = runCatching { transport.sendCommand("0131", timeoutMs = 1500L) }.getOrDefault("")
                        val r014e = runCatching { transport.sendCommand("014E", timeoutMs = 1500L) }.getOrDefault("")
                        listOf(r0902, r0904, r0121, r0130, r0131, r014e)
                    }
                    vin = Elm327Parser.parseMode09Vin(extra[0]) ?: vin
                    calId = Elm327Parser.parseMode09CalId(extra[1]) ?: calId
                    distMilOn = Elm327Parser.parseMode01UInt16("0121", extra[2])
                    warmupsSinceClear = Elm327Parser.parseMode01UInt8("0130", extra[3])
                    distSinceClear = Elm327Parser.parseMode01UInt16("0131", extra[4])
                    timeSinceClearMin = Elm327Parser.parseMode01UInt16("014E", extra[5])
                    im = _imReadiness.value
                    milOn = _dtcState.value.milOn
                    activeDtcs = _dtcState.value.codes.size
                    pendingDtcs = _dtcState.value.pendingCodes.size
                    m06Failed = _dtcState.value.mode06Tests.count { !it.passed }
                } else {
                    _prePurchaseResult.value = PrePurchaseInspectionResult(
                        vehicleName = vehicleName,
                        isRunning = false,
                        errorMessage = if (isEng) {
                            "Connect an OBD2 adapter or start Simulation Mode first."
                        } else {
                            "Сначала подключите OBD2 адаптер или включите Тест-режим."
                        }
                    )
                    return@launch
                }

                val supportedMonitors = im?.monitors?.filter { it.supported }.orEmpty()
                val readyCount = supportedMonitors.count { it.completed }
                val supportedCount = supportedMonitors.size
                val allReady = supportedCount == 0 || readyCount == supportedCount

                val suspicion = when {
                    (!milOn && !allReady && ((distSinceClear != null && distSinceClear <= 80) || (warmupsSinceClear != null && warmupsSinceClear <= 3))) ->
                        ResetSuspicionLevel.HIGH
                    (distSinceClear != null && distSinceClear <= 30 && warmupsSinceClear != null && warmupsSinceClear <= 1) ->
                        ResetSuspicionLevel.HIGH
                    !allReady || milOn || activeDtcs > 0 || pendingDtcs > 0 || m06Failed > 0 || (distSinceClear != null && distSinceClear < 150) ->
                        ResetSuspicionLevel.CAUTION
                    else -> ResetSuspicionLevel.NONE
                }

                val technicalDraft = PrePurchaseInspectionResult(
                    timestampMs = System.currentTimeMillis(),
                    vehicleName = vehicleName,
                    vin = vin,
                    calId = calId,
                    milOn = milOn,
                    activeDtcCount = activeDtcs,
                    pendingDtcCount = pendingDtcs,
                    distanceSinceCodesClearedKm = distSinceClear,
                    timeSinceCodesClearedMin = timeSinceClearMin,
                    distanceWithMilOnKm = distMilOn,
                    warmupsSinceCodesCleared = warmupsSinceClear,
                    imReadyCount = readyCount,
                    imSupportedCount = supportedCount,
                    imAllReady = allReady,
                    mode06FailedCount = m06Failed,
                    batteryVoltage = batteryV,
                    coolantTempC = coolantC,
                    resetSuspicion = suspicion,
                    isRunning = true
                )
                _prePurchaseResult.value = technicalDraft

                val verdictRes = aiAssistant.generatePrePurchaseVerdict(technicalDraft, isEng)
                val verdictText = verdictRes.getOrElse { err ->
                    sessionLogger.warn("INSPECT", "AI-заключение недоступно: ${err.message}")
                    if (isEng) {
                        "Technical check completed ($suspicion). AI summary unavailable offline."
                    } else {
                        "Техническая проверка выполнена (${suspicion.name}). Онлайн-заключение AI сейчас недоступно."
                    }
                }
                _prePurchaseResult.value = technicalDraft.copy(
                    aiVerdict = verdictText,
                    isRunning = false
                )
                sessionLogger.info("INSPECT", "Предпродажная проверка завершена: риск=${suspicion.name}, 0131=${distSinceClear}км")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                sessionLogger.error("INSPECT", "Ошибка предпродажной проверки", e)
                _prePurchaseResult.value = PrePurchaseInspectionResult(
                    vehicleName = vehicleName,
                    isRunning = false,
                    errorMessage = e.message ?: if (isEng) "Inspection error" else "Ошибка проверки"
                )
            }
        }
    }

    fun createPrePurchasePdfFile(): File? {
        val current = _prePurchaseResult.value ?: return null
        return runCatching {
            PrePurchasePdfGenerator.generateReportPdf(
                context = context,
                result = current,
                isEnglish = settings.value.isEnglish
            )
        }.onFailure { err ->
            sessionLogger.error("PDF", "Ошибка создания PDF-отчёта", err)
        }.getOrNull()
    }

    // --- Guided Battery & Alternator Health Test ---

    fun startBatteryAlternatorTest() {
        batteryTestJob?.cancel()
        batteryTestJob = scope.launch {
            val isEng = settings.value.isEnglish
            val conn = _connectionState.value
            val transport = activeTransport
            val isSim = conn is ObdConnectionState.Simulation
            val isReal = conn is ObdConnectionState.Connected && transport != null && transport.isConnected

            if (!isSim && !isReal) {
                _batteryTestState.value = BatteryTestState(
                    phase = BatteryTestPhase.FAILED,
                    errorMessage = if (isEng) {
                        "Connect OBD2 adapter or turn on Simulation Mode."
                    } else {
                        "Подключите OBD2 адаптер или включите Тест-режим."
                    }
                )
                return@launch
            }

            suspend fun sampleVoltage(phase: BatteryTestPhase, step: Int): Float {
                if (isSim) {
                    return when (phase) {
                        BatteryTestPhase.RESTING_SAMPLE -> 12.56f + (step % 2) * 0.02f
                        BatteryTestPhase.CRANKING_WATCH -> when (step) {
                            1, 2 -> 10.42f
                            3 -> 10.75f
                            else -> 12.15f
                        }
                        BatteryTestPhase.CHARGING_IDLE -> 14.24f - (step % 2) * 0.03f
                        BatteryTestPhase.CHARGING_LOAD -> 13.94f + (step % 2) * 0.02f
                        else -> 13.8f
                    }
                }
                val activeT = transport ?: return 0f
                val v = commandMutex.withLock {
                    if (!activeT.isConnected) return@withLock null
                    ensureCanHeader(activeT, "7DF")
                    val r42 = runCatching { activeT.sendCommand("0142", timeoutMs = 1200L) }.getOrDefault("")
                    Elm327Parser.parseMode01Pid(ObdPid.CONTROL_MODULE_VOLTAGE, r42)
                        ?: Elm327Parser.parseAtrvVoltage(
                            runCatching { activeT.sendCommand("ATRV", timeoutMs = 1200L) }.getOrDefault("")
                        )
                }
                return v ?: _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value ?: 12.4f
            }

            try {
                // Stage 1: Resting (Ignition ON, engine OFF)
                val restingSamples = mutableListOf<Float>()
                for (i in 0 until 4) {
                    val v = sampleVoltage(BatteryTestPhase.RESTING_SAMPLE, i)
                    restingSamples += v
                    _batteryTestState.value = BatteryTestState(
                        phase = BatteryTestPhase.RESTING_SAMPLE,
                        liveVoltage = v,
                        restingVoltage = restingSamples.average().toFloat(),
                        progressPct = 0.05f + i * 0.05f
                    )
                    delay(650L)
                }
                val restingV = restingSamples.average().toFloat()

                // Stage 2: Cranking Watch (Start the engine)
                var minCrank = restingV
                for (i in 0 until 6) {
                    val v = sampleVoltage(BatteryTestPhase.CRANKING_WATCH, i)
                    if (v < minCrank) minCrank = v
                    _batteryTestState.value = BatteryTestState(
                        phase = BatteryTestPhase.CRANKING_WATCH,
                        liveVoltage = v,
                        restingVoltage = restingV,
                        minCrankingVoltage = minCrank,
                        progressPct = 0.28f + i * 0.05f
                    )
                    delay(600L)
                }

                // Stage 3: Idle Charging
                val idleSamples = mutableListOf<Float>()
                for (i in 0 until 4) {
                    val v = sampleVoltage(BatteryTestPhase.CHARGING_IDLE, i)
                    idleSamples += v
                    _batteryTestState.value = BatteryTestState(
                        phase = BatteryTestPhase.CHARGING_IDLE,
                        liveVoltage = v,
                        restingVoltage = restingV,
                        minCrankingVoltage = minCrank,
                        idleChargingVoltage = idleSamples.average().toFloat(),
                        progressPct = 0.60f + i * 0.05f
                    )
                    delay(700L)
                }
                val idleV = idleSamples.average().toFloat()

                // Stage 4: Loaded Charging (Headlights + AC/Heater)
                val loadSamples = mutableListOf<Float>()
                for (i in 0 until 4) {
                    val v = sampleVoltage(BatteryTestPhase.CHARGING_LOAD, i)
                    loadSamples += v
                    _batteryTestState.value = BatteryTestState(
                        phase = BatteryTestPhase.CHARGING_LOAD,
                        liveVoltage = v,
                        restingVoltage = restingV,
                        minCrankingVoltage = minCrank,
                        idleChargingVoltage = idleV,
                        loadChargingVoltage = loadSamples.average().toFloat(),
                        progressPct = 0.80f + i * 0.05f
                    )
                    delay(700L)
                }
                val loadV = loadSamples.average().toFloat()

                val (batRu, batEn) = when {
                    minCrank < 9.3f || restingV < 11.8f ->
                        "Слабая просадка при старте / низкий заряд — проверьте АКБ" to "Weak cranking dip / low SOC — service or replace battery"
                    minCrank < 10.0f || restingV < 12.2f ->
                        "Удовлетворительно — рекомендуется подзарядка АКБ" to "Fair — battery recharge recommended"
                    else ->
                        "АКБ в норме (уверенный пуск и заряд покоя)" to "Battery healthy (strong resting & cranking voltage)"
                }

                val (altRu, altEn) = when {
                    idleV > 15.0f || loadV > 15.0f ->
                        "Перезаряд! Проверьте реле-регулятор генератора" to "Overcharging! Inspect alternator voltage regulator"
                    idleV < 13.4f || loadV < 13.1f ->
                        "Недозаряд под нагрузкой — проверьте генератор и ремень" to "Undercharging under load — inspect alternator & belt"
                    else ->
                        "Генератор и реле-регулятор работают исправно" to "Alternator & regulator operating within normal range"
                }

                _batteryTestState.value = BatteryTestState(
                    phase = BatteryTestPhase.COMPLETED,
                    liveVoltage = loadV,
                    restingVoltage = restingV,
                    minCrankingVoltage = minCrank,
                    idleChargingVoltage = idleV,
                    loadChargingVoltage = loadV,
                    progressPct = 1f,
                    batteryStatusRu = batRu,
                    batteryStatusEn = batEn,
                    alternatorStatusRu = altRu,
                    alternatorStatusEn = altEn
                )
                sessionLogger.info(
                    "BATT_TEST",
                    "Тест АКБ завершён: покой=${String.format(Locale.US, "%.2f", restingV)}V, пуск=${String.format(Locale.US, "%.2f", minCrank)}V, ХХ=${String.format(Locale.US, "%.2f", idleV)}V, нагр=${String.format(Locale.US, "%.2f", loadV)}V"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                sessionLogger.error("BATT_TEST", "Ошибка теста АКБ и генератора", e)
                _batteryTestState.value = BatteryTestState(
                    phase = BatteryTestPhase.FAILED,
                    errorMessage = e.message ?: if (isEng) "Battery test failed" else "Ошибка теста АКБ"
                )
            }
        }
    }

    fun cancelBatteryTest() {
        batteryTestJob?.cancel()
        _batteryTestState.value = BatteryTestState()
    }

    // --- Multimodal AI Vision (Photo Analysis) ---

    fun analyzeSelectedPhoto(uri: Uri, mode: PhotoAnalysisMode) {
        photoJob?.cancel()
        photoJob = scope.launch {
            val isEng = settings.value.isEnglish
            _photoAnalysisState.value = PhotoAnalysisState.Analyzing(mode)
            try {
                val decoded = decodeScaledBitmap(uri, maxDimension = 1280)
                    ?: throw IllegalStateException(
                        if (isEng) "Failed to read selected image" else "Не удалось прочитать выбранное фото"
                    )
                val profile = activeVehicleProfile()
                val vLabel = profile?.name ?: (if (isEng) "Car" else "Автомобиль")
                val res = aiAssistant.analyzeAutoPhoto(
                    bitmap = decoded,
                    mode = mode,
                    vehicleLabel = vLabel,
                    readings = _readings.value,
                    isEnglish = isEng
                )
                decoded.recycle()
                res.onSuccess { state ->
                    _photoAnalysisState.value = state
                    sessionLogger.info("AI_VISION", "Анализ фото (${mode.name}) успешно выполнен")
                }.onFailure { err ->
                    _photoAnalysisState.value = PhotoAnalysisState.Error(
                        err.localizedMessage ?: if (isEng) "Photo analysis error" else "Ошибка анализа фото"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                sessionLogger.error("AI_VISION", "Ошибка анализа фото", e)
                _photoAnalysisState.value = PhotoAnalysisState.Error(
                    e.localizedMessage ?: if (isEng) "Failed to process image" else "Ошибка обработки изображения"
                )
            }
        }
    }

    fun clearPhotoAnalysis() {
        photoJob?.cancel()
        _photoAnalysisState.value = PhotoAnalysisState.Idle
    }

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val largest = maxOf(w, h)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (w * ratio).roundToInt().coerceAtLeast(1),
                        (h * ratio).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } ?: return null

        val w = rawBitmap.width
        val h = rawBitmap.height
        val largest = maxOf(w, h)
        if (largest <= maxDimension) return rawBitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            rawBitmap,
            (w * scale).roundToInt().coerceAtLeast(1),
            (h * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled != rawBitmap) rawBitmap.recycle()
        return scaled
    }

    // --- VIN / Model AI Profile Auto-Fill ---

    fun requestAiVehicleSuggestion(vin: String?, query: String) {
        scope.launch {
            val isEng = settings.value.isEnglish
            _aiVehicleSuggestionState.value = AiVehicleSuggestionState.Loading
            aiAssistant.suggestVehicleProfileAndMaintenance(
                vin = vin,
                query = query,
                isEnglish = isEng
            ).onSuccess { suggestion ->
                _aiVehicleSuggestionState.value = AiVehicleSuggestionState.Ready(suggestion)
                sessionLogger.info("AI_GARAGE", "AI подобрал профиль: ${suggestion.displayName} (${suggestion.curbWeightKg} кг)")
            }.onFailure { err ->
                _aiVehicleSuggestionState.value = AiVehicleSuggestionState.Error(
                    err.localizedMessage ?: if (isEng) "AI auto-fill error" else "Ошибка AI-подбора профиля"
                )
            }
        }
    }

    fun clearAiVehicleSuggestion() {
        _aiVehicleSuggestionState.value = AiVehicleSuggestionState.Idle
    }

    fun applyAiSuggestionExtras(suggestion: AiVehicleSuggestion) {
        scope.launch {
            val profile = activeVehicleProfile() ?: return@launch
            val existingTitles = maintenanceDao.getForVehicle(profile.id).map { it.title.lowercase() }.toSet()
            val toInsert = suggestion.maintenanceItems
                .filter { it.title.lowercase() !in existingTitles }
                .map { spec ->
                    MaintenanceItemEntity(
                        vehicleId = profile.id,
                        vehicleKey = profile.syncVehicleKey,
                        title = spec.title,
                        category = spec.category,
                        intervalKm = spec.intervalKm,
                        intervalHours = spec.intervalHours,
                        lastServiceOdometerKm = profile.odometerKm,
                        lastServiceEngineHours = profile.engineHours,
                        lastServiceTimestampMs = System.currentTimeMillis()
                    )
                }
            if (toInsert.isNotEmpty()) {
                maintenanceDao.insertAll(toInsert)
            }
            suggestion.recommendedOemPackId?.let { packId ->
                importOemPack(packId)
            }
            pushWidgetSnapshot()
        }
    }

    // --- Interactive ELM327 Terminal ---

    fun sendRawElmCommand(rawCommand: String) {
        val cmd = rawCommand.trim().uppercase()
        if (cmd.isBlank()) return
        scope.launch {
            val isEng = settings.value.isEnglish
            val conn = _connectionState.value
            val transport = activeTransport

            val (reply, latencyMs, isError) = when {
                conn is ObdConnectionState.Simulation -> {
                    delay(45)
                    val simReply = when {
                        cmd == "ATI" -> "ELM327 v1.5 (OBD2 Auto Sim)"
                        cmd == "ATZ" -> "ELM327 v1.5 OK"
                        cmd == "ATRV" -> "${String.format(Locale.US, "%.1f", _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value ?: 13.9f)}V"
                        cmd == "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
                        cmd == "0100" -> "41 00 BE 3E B8 13"
                        cmd == "010C" -> "41 0C 1A F8"
                        cmd == "010D" -> "41 0D 42"
                        cmd == "0131" -> "41 31 07 30"
                        cmd == "0902" -> "49 02 01 4A 54 44 4B 42 32 30 55 38 39 33 35 31 34 38 32 39"
                        cmd == "03" -> if (_dtcState.value.codes.isEmpty()) "43 00" else "43 01 71 04 20"
                        cmd == "07" -> "47 00"
                        else -> "OK (SIM)"
                    }
                    Triple("$simReply (SIM)", 28L, false)
                }
                conn is ObdConnectionState.Connected && transport != null && transport.isConnected -> {
                    try {
                        val (res, dt) = commandMutex.withLock {
                            val t0 = System.currentTimeMillis()
                            val out = transport.sendCommand(cmd, timeoutMs = 3500L)
                            val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                            if (cmd.startsWith("ATSH")) {
                                activeCanHeader = cmd.removePrefix("ATSH").trim().ifBlank { "7DF" }
                            } else if (cmd == "ATZ") {
                                activeCanHeader = "7DF"
                            }
                            out to elapsed
                        }
                        val cleaned = Elm327Parser.cleanResponse(res).ifBlank { "(empty)" }
                        val err = Elm327Parser.isErrorResponse(res)
                        Triple(cleaned, dt, err)
                    } catch (e: Exception) {
                        Triple(e.message ?: "Error", 0L, true)
                    }
                }
                else -> {
                    Triple(
                        if (isEng) "Not connected to ELM327 adapter" else "Нет подключения к ELM327 адаптеру",
                        0L,
                        true
                    )
                }
            }

            terminalEntrySeq++
            val entry = TerminalEntry(
                id = terminalEntrySeq,
                command = cmd,
                response = reply,
                latencyMs = latencyMs,
                isError = isError
            )
            _terminalEntries.update { (it + entry).takeLast(60) }
            sessionLogger.info("TERM", ">$cmd -> $reply (${latencyMs}ms)")
        }
    }

    fun clearTerminal() {
        _terminalEntries.value = emptyList()
    }

    // --- Cloud Sync (Firebase Auth + Firestore) ---

    fun attemptSilentCloudSignIn(activityContext: Context) {
        scope.launch {
            val summary = cloudAuthManager.attemptSilentSignIn(activityContext)
            if (summary != null) {
                _cloudSyncState.update { it.copy(user = summary) }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        scope.launch {
            val isEng = settings.value.isEnglish
            _cloudSyncState.update { it.copy(status = CloudSyncStatus.SigningIn) }
            cloudAuthManager.signInWithGoogle(activityContext, isEng)
                .onSuccess { user ->
                    _cloudSyncState.value = CloudSyncState(user = user, status = CloudSyncStatus.Idle)
                    syncCloudNow()
                }
                .onFailure { err ->
                    _cloudSyncState.update {
                        it.copy(
                            status = CloudSyncStatus.Error(
                                err.localizedMessage ?: if (isEng) "Sign-in failed" else "Ошибка входа Google"
                            )
                        )
                    }
                }
        }
    }

    fun syncCloudNow() {
        scope.launch {
            val isEng = settings.value.isEnglish
            _cloudSyncState.update { it.copy(status = CloudSyncStatus.Syncing) }
            cloudSyncRepository.syncAll(isEng)
                .onSuccess { count ->
                    _cloudSyncState.update {
                        it.copy(
                            user = cloudAuthManager.currentUserSummary(),
                            status = CloudSyncStatus.Synced(
                                lastSyncMs = System.currentTimeMillis(),
                                syncedItemsCount = count
                            )
                        )
                    }
                    pushWidgetSnapshot()
                    sessionLogger.info("CLOUD", "Облачная синхронизация выполнена ($count объектов)")
                }
                .onFailure { err ->
                    _cloudSyncState.update {
                        it.copy(
                            status = CloudSyncStatus.Error(
                                err.localizedMessage ?: if (isEng) "Sync error" else "Ошибка синхронизации"
                            )
                        )
                    }
                }
        }
    }

    fun signOutCloud() {
        scope.launch {
            cloudAuthManager.signOut()
            _cloudSyncState.value = CloudSyncState(user = null, status = CloudSyncStatus.Idle)
            sessionLogger.info("CLOUD", "Выход из облачного аккаунта выполнен")
        }
    }

    fun setPollingIntervalMs(intervalMs: Long) {
        scope.launch { settingsDataStore.setPollingIntervalMs(intervalMs) }
    }

    fun setUseFahrenheit(useFahrenheit: Boolean) {
        scope.launch { settingsDataStore.setUseFahrenheit(useFahrenheit) }
    }

    fun setPressureUnit(unit: PressureUnit) {
        scope.launch { settingsDataStore.setPressureUnit(unit) }
    }

    fun setAppLanguage(language: AppLanguage) {
        scope.launch {
            settingsDataStore.setAppLanguage(language)
            sessionLogger.info("LANG", "Смена языка интерфейса: ${language.name}")
            pushWidgetSnapshot()
        }
    }

    fun clearScanHistory() {
        scope.launch { diagnosticScanDao.clearHistory() }
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
        scope.launch {
            val entity = CustomPidEntity(
                name = name.trim(),
                shortName = shortName.trim().ifBlank { name.trim().take(12) },
                modeAndPid = TorqueEquationEvaluator.normalizeModeAndPid(modeAndPid),
                header = TorqueEquationEvaluator.normalizeHeader(header),
                equation = equation.trim(),
                unit = unit.trim(),
                minValue = minValue,
                maxValue = if (maxValue > minValue) maxValue else minValue + 10f,
                decimals = decimals.coerceIn(0, 3),
                enabled = true,
                warningHigh = warningHigh,
                criticalHigh = criticalHigh,
                warningLow = warningLow,
                criticalLow = criticalLow,
                alertsEnabled = alertsEnabled
            )
            val newId = customPidDao.insert(entity)
            unsupportedSensorIds.remove("CUSTOM_$newId")
            sessionLogger.info("PID", "Добавлен Torque PID: ${entity.name} [${entity.modeAndPid}] = ${entity.equation}")
        }
    }

    fun updateCustomPid(entity: CustomPidEntity) {
        scope.launch {
            customPidDao.update(entity)
            unsupportedSensorIds.remove(entity.sensorId)
            sessionLogger.info("PID", "Обновлён Torque PID: ${entity.name} [${entity.modeAndPid}]")
        }
    }

    fun deleteCustomPid(id: Long) {
        scope.launch {
            val sensorId = "CUSTOM_$id"
            customPidDao.deleteById(id)
            settingsDataStore.removeAndroidAutoSensor(sensorId)
            unsupportedSensorIds.remove(sensorId)
            _readings.update { it - sensorId }
            sessionLogger.info("PID", "Удалён пользовательский PID #$id")
        }
    }

    suspend fun importTorqueCsv(csvText: String): Int {
        val parsed = TorqueEquationEvaluator.parseTorqueCsv(csvText)
        if (parsed.isEmpty()) return 0
        val entities = parsed.map { item ->
            CustomPidEntity(
                name = item.name,
                shortName = item.shortName,
                modeAndPid = item.modeAndPid,
                header = item.header,
                equation = item.equation,
                unit = item.unit,
                minValue = item.minValue,
                maxValue = item.maxValue,
                decimals = 1,
                enabled = true
            )
        }
        customPidDao.insertAll(entities)
        unsupportedSensorIds.clear()
        sessionLogger.info("PID", "Импортировано ${entities.size} PID из Torque CSV")
        return entities.size
    }

    fun buildErrorReportText(): String {
        return sessionLogger.buildReportText(
            connectionState = _connectionState.value,
            dtcState = _dtcState.value,
            hasBtPermissions = bluetoothScanner.hasBluetoothPermissions(),
            isBtEnabled = bluetoothScanner.isBluetoothEnabled(),
            customPidCount = customPids.value.size,
            isEnglish = settings.value.isEnglish
        )
    }

    fun createShareableReportFile(): File {
        return sessionLogger.createShareableReportFile(buildErrorReportText())
    }

    fun clearLogs() {
        sessionLogger.clear()
    }

    private suspend fun ensureCanHeader(transport: ObdTransport, targetHeader: String) {
        val normalized = targetHeader.ifBlank { "7DF" }
        if (activeCanHeader != normalized) {
            runCatching { transport.sendCommand("ATSH$normalized", timeoutMs = 1200L) }
            activeCanHeader = normalized
        }
    }

    private suspend fun initializeElm327(transport: ObdTransport): String = commandMutex.withLock {
        val isEng = settings.value.isEnglish
        val resetResp = transport.sendCommand("ATZ", timeoutMs = 4000L)
        if (resetResp.isBlank()) {
            throw IllegalStateException(
                if (isEng) "Adapter did not respond to ATZ" else "Адаптер не ответил на ATZ"
            )
        }
        delay(450)
        transport.sendCommand("ATE0")
        transport.sendCommand("ATL0")
        transport.sendCommand("ATS0")
        transport.sendCommand("ATH0")
        transport.sendCommand("ATSP0")
        activeCanHeader = "7DF"

        val probe00 = transport.sendCommand("0100", timeoutMs = 10_000L)
        if (Elm327Parser.isErrorResponse(probe00)) {
            throw IllegalStateException(
                if (isEng) "Vehicle ECU is not responding (check ignition)" else "ЭБУ автомобиля не отвечает (проверьте зажигание)"
            )
        }

        val supported = mutableSetOf<String>()
        supported += Elm327Parser.parseSupportedPidBitmap(0x00, probe00)
        if ("0120" in supported) {
            val probe20 = runCatching { transport.sendCommand("0120", timeoutMs = 3000L) }.getOrDefault("")
            supported += Elm327Parser.parseSupportedPidBitmap(0x20, probe20)
        }
        if ("0140" in supported) {
            val probe40 = runCatching { transport.sendCommand("0140", timeoutMs = 3000L) }.getOrDefault("")
            supported += Elm327Parser.parseSupportedPidBitmap(0x40, probe40)
        }
        if ("0160" in supported) {
            val probe60 = runCatching { transport.sendCommand("0160", timeoutMs = 3000L) }.getOrDefault("")
            supported += Elm327Parser.parseSupportedPidBitmap(0x60, probe60)
        }

        if (supported.isNotEmpty()) {
            _supportedMode01Pids.value = supported
            val builtInSupportedCount = ObdPid.entries.count { it.modeAndPid in supported }
            sessionLogger.info(
                "PID_MAP",
                "Карта поддерживаемых PID (0100..0160): ${supported.size} всего, из встроенных $builtInSupportedCount/${ObdPid.entries.size}"
            )
        } else {
            _supportedMode01Pids.value = null
            sessionLogger.warn("PID_MAP", "Не удалось декодировать битовую карту 0100 — опрос всех стандартных PID")
        }

        val protocolRaw = runCatching { transport.sendCommand("ATDP") }.getOrDefault("OBD-II Auto")
        val protocolClean = Elm327Parser.cleanResponse(protocolRaw).ifBlank { "OBD-II Auto" }
        return protocolClean.removePrefix("AUTO,").trim()
    }

    private suspend fun readVehicleIdentityInternal(transport: ObdTransport) {
        try {
            val (vinRaw, calRaw) = commandMutex.withLock {
                ensureCanHeader(transport, "7DF")
                val vin = runCatching { transport.sendCommand("0902", timeoutMs = 3500L) }.getOrDefault("")
                val cal = runCatching { transport.sendCommand("0904", timeoutMs = 3000L) }.getOrDefault("")
                vin to cal
            }
            val vin = Elm327Parser.parseMode09Vin(vinRaw)
            val calId = Elm327Parser.parseMode09CalId(calRaw)
            var matchedName: String? = null
            if (!vin.isNullOrBlank()) {
                val matched = vehicleProfileDao.findByVin(vin)
                if (matched != null) {
                    matchedName = matched.name
                    settingsDataStore.setActiveVehicleProfileId(matched.id)
                    settingsDataStore.setShiftLightRpm(matched.shiftLightRpm)
                    if (matched.preferredPresetId.isNotBlank()) {
                        settingsDataStore.selectCockpitPreset(matched.preferredPresetId)
                    }
                    sessionLogger.info("VIN", "VIN $vin сопоставлен с профилем '${matched.name}'")
                } else {
                    sessionLogger.info("VIN", "Считан VIN: $vin (Calibration ID: ${calId ?: "—"})")
                }
            }
            _vehicleInfo.value = VehicleInfoState(
                vin = vin,
                calibrationId = calId,
                matchedProfileName = matchedName
            )
        } catch (e: Exception) {
            sessionLogger.warn("VIN", "Не удалось прочитать Mode 09 VIN/CalID: ${e.message}")
        }
    }

    private suspend fun runRealPollingLoop(
        transport: ObdTransport,
        device: BluetoothDeviceItem,
        protocol: String
    ) {
        var cycle = 0
        var lastCycleTimeMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive && transport.isConnected) {
            val currentSettings = settings.value
            val allSensors = sensors.value
            val supportedMap = _supportedMode01Pids.value

            val activePreset = currentSettings.activePreset
            val priorityIds = if (currentSettings.effectiveAndroidAutoDisplayMode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) {
                (activePreset.dialSensorIds +
                    activePreset.stripSensorIds +
                    currentSettings.androidAutoSensorIds +
                    listOf(ObdPid.VEHICLE_SPEED.modeAndPid, ObdPid.ENGINE_RPM.modeAndPid)).distinct()
            } else {
                (currentSettings.androidAutoSensorIds +
                    activePreset.dialSensorIds +
                    activePreset.stripSensorIds +
                    listOf(ObdPid.VEHICLE_SPEED.modeAndPid, ObdPid.ENGINE_RPM.modeAndPid)).distinct()
            }

            fun isPollable(sensor: TelemetrySensor): Boolean {
                if (sensor.id in unsupportedSensorIds) return false
                if (supportedMap != null &&
                    !sensor.isCustom &&
                    sensor.builtInPid != null &&
                    sensor.builtInPid != ObdPid.CONTROL_MODULE_VOLTAGE &&
                    sensor.modeAndPid !in supportedMap
                ) {
                    return false
                }
                return true
            }

            val prioritySensors = priorityIds
                .mapNotNull { id -> allSensors.find { it.id == id } }
                .filter(::isPollable)
            val secondarySensors = allSensors.filter { it.id !in priorityIds && isPollable(it) }

            val secondarySlice = if (secondarySensors.isNotEmpty() && (!currentSettings.fastSlowPollingEnabled || cycle % 5 == 0)) {
                val startIdx = (cycle * 2) % secondarySensors.size
                listOfNotNull(
                    secondarySensors.getOrNull(startIdx),
                    secondarySensors.getOrNull((startIdx + 1) % secondarySensors.size)
                )
            } else {
                emptyList()
            }

            val sensorsToPoll = (prioritySensors + secondarySlice).distinctBy { it.id }
            for (sensor in sensorsToPoll) {
                if (!currentCoroutineContext().isActive || !transport.isConnected) break

                val targetHeader = sensor.header.ifBlank { "7DF" }
                val (raw, atrvRaw) = commandMutex.withLock {
                    if (!transport.isConnected) return@withLock "" to null
                    ensureCanHeader(transport, targetHeader)
                    val response = transport.sendCommand(sensor.modeAndPid, timeoutMs = 1500L)
                    val fallbackAtrv = if (
                        sensor.builtInPid == ObdPid.CONTROL_MODULE_VOLTAGE &&
                        Elm327Parser.parseMode01Pid(ObdPid.CONTROL_MODULE_VOLTAGE, response) == null
                    ) {
                        transport.sendCommand("ATRV", timeoutMs = 1200L)
                    } else null
                    response to fallbackAtrv
                }

                var value: Float? = if (sensor.isCustom && sensor.equation != null) {
                    TorqueEquationEvaluator.parseCustomPidResponse(sensor.modeAndPid, sensor.equation, raw)
                } else {
                    sensor.builtInPid?.let { Elm327Parser.parseMode01Pid(it, raw) }
                }

                if (value == null && atrvRaw != null) {
                    value = Elm327Parser.parseAtrvVoltage(atrvRaw)
                }

                if (value != null) {
                    recordSensorValue(sensor, value)
                } else if (raw.uppercase().contains("NO DATA")) {
                    unsupportedSensorIds.add(sensor.id)
                    sessionLogger.warn("POLL", "Датчик ${sensor.displayName} (${sensor.modeAndPid}) вернул NO DATA")
                }
            }

            feedTripComputerFromLatestReadings()
            datalogger.onReadingsSnapshot(_readings.value)
            if (widgetPushCounter++ % 8 == 0) {
                pushWidgetSnapshot()
            }

            val now = System.currentTimeMillis()
            val elapsed = (now - lastCycleTimeMs).coerceAtLeast(1L)
            lastCycleTimeMs = now
            val hz = 1000f / elapsed.toFloat()

            _connectionState.value = ObdConnectionState.Connected(
                deviceName = device.name,
                address = device.address,
                protocol = protocol,
                transport = device.transport,
                pollingHz = hz
            )

            cycle++
            val targetDelay = currentSettings.pollingIntervalMs
            if (elapsed < targetDelay) {
                delay(targetDelay - elapsed)
            }
        }
    }

    private suspend fun readDtcsInternal(
        transport: ObdTransport,
        adapterName: String,
        protocol: String
    ) {
        val isEng = settings.value.isEnglish
        _dtcState.update {
            it.copy(
                isScanning = true,
                statusMessage = if (isEng) "Reading ECU Mode 03/06/07..." else "Чтение ошибок и тестов (Mode 03/06/07)..."
            )
        }
        try {
            val (milRaw, dtcRaw, pendingRaw, m06Responses) = commandMutex.withLock {
                ensureCanHeader(transport, "7DF")
                val mil = transport.sendCommand("0101", timeoutMs = 2500L)
                val dtc = transport.sendCommand("03", timeoutMs = 3500L)
                val pending = runCatching { transport.sendCommand("07", timeoutMs = 3000L) }.getOrDefault("")
                val m06List = listOf("0601", "0621", "06A1").map { cmd ->
                    runCatching { transport.sendCommand(cmd, timeoutMs = 1800L) }.getOrDefault("")
                }
                DtcReadBatch(mil, dtc, pending, m06List)
            }
            val imResult = Elm327Parser.parseImReadiness(milRaw)
            if (imResult != null) {
                _imReadiness.value = imResult
            }
            val milInfo = Elm327Parser.parseMilStatus(milRaw)
            val codes = Elm327Parser.parseMode03Dtcs(dtcRaw, isEng)
            val pendingCodes = Elm327Parser.parseMode07Dtcs(pendingRaw, isEng)
            val mode06Tests = m06Responses
                .flatMap { raw -> Elm327Parser.parseMode06Tests(raw) }
                .distinctBy { "${it.midHex}_${it.tidHex}" }

            val milOn = milInfo?.first ?: codes.isNotEmpty()
            val count = maxOf(milInfo?.second ?: 0, codes.size)

            _dtcState.value = DtcState(
                milOn = milOn,
                ecuReportedCount = count,
                codes = codes,
                pendingCodes = pendingCodes,
                mode06Tests = mode06Tests,
                isScanning = false,
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = if (codes.isEmpty() && pendingCodes.isEmpty()) {
                    if (isEng) "No ECU trouble codes found" else "Ошибок ЭБУ не обнаружено"
                } else {
                    if (isEng) {
                        "Active: ${codes.size}, Pending: ${pendingCodes.size}"
                    } else {
                        "Активных: ${codes.size}, Ожидающих: ${pendingCodes.size}"
                    }
                }
            )
            pushWidgetSnapshot()
            sessionLogger.info(
                "DTC",
                "Считано: Mode03=${codes.size}, Mode07=${pendingCodes.size}, Mode06=${mode06Tests.size} (MIL=${if (milOn) "ON" else "OFF"})"
            )

            if (milOn || codes.isNotEmpty()) {
                readFreezeFrameInternal(transport)
            }

            diagnosticScanDao.insertScan(
                DiagnosticScanEntity(
                    timestampMs = System.currentTimeMillis(),
                    adapterName = adapterName,
                    protocol = protocol,
                    milOn = milOn,
                    dtcCount = codes.size,
                    dtcSummary = if (codes.isEmpty()) {
                        if (isEng) "No faults" else "Ошибок нет"
                    } else {
                        codes.joinToString(", ") { it.code }
                    },
                    batteryVoltage = _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value
                )
            )
        } catch (e: Exception) {
            sessionLogger.error("DTC", "Ошибка чтения DTC", e)
            _dtcState.update {
                it.copy(
                    isScanning = false,
                    statusMessage = if (isEng) "DTC read error: ${e.message}" else "Ошибка чтения DTC: ${e.message}"
                )
            }
        }
    }

    private suspend fun readFreezeFrameInternal(transport: ObdTransport) {
        try {
            val ff = commandMutex.withLock {
                ensureCanHeader(transport, "7DF")
                val dtcRaw = runCatching { transport.sendCommand("020200", timeoutMs = 2000L) }.getOrDefault("")
                val triggerDtc = Elm327Parser.parseFreezeFrameDtc(dtcRaw)
                val rpm = Elm327Parser.parseMode02Pid(
                    ObdPid.ENGINE_RPM,
                    runCatching { transport.sendCommand("020C00", timeoutMs = 1500L) }.getOrDefault("")
                )
                val speed = Elm327Parser.parseMode02Pid(
                    ObdPid.VEHICLE_SPEED,
                    runCatching { transport.sendCommand("020D00", timeoutMs = 1500L) }.getOrDefault("")
                )
                val coolant = Elm327Parser.parseMode02Pid(
                    ObdPid.COOLANT_TEMP,
                    runCatching { transport.sendCommand("020500", timeoutMs = 1500L) }.getOrDefault("")
                )
                val load = Elm327Parser.parseMode02Pid(
                    ObdPid.ENGINE_LOAD,
                    runCatching { transport.sendCommand("020400", timeoutMs = 1500L) }.getOrDefault("")
                )
                val stft = Elm327Parser.parseMode02Pid(
                    ObdPid.SHORT_FUEL_TRIM,
                    runCatching { transport.sendCommand("020600", timeoutMs = 1500L) }.getOrDefault("")
                )
                val ltft = Elm327Parser.parseMode02Pid(
                    ObdPid.LONG_FUEL_TRIM,
                    runCatching { transport.sendCommand("020700", timeoutMs = 1500L) }.getOrDefault("")
                )
                val map = Elm327Parser.parseMode02Pid(
                    ObdPid.INTAKE_MAP,
                    runCatching { transport.sendCommand("020B00", timeoutMs = 1500L) }.getOrDefault("")
                )
                if (triggerDtc == null && rpm == null && coolant == null && load == null) {
                    null
                } else {
                    FreezeFrameData(
                        dtcCode = triggerDtc,
                        rpm = rpm,
                        speedKmh = speed,
                        coolantTempC = coolant,
                        engineLoadPct = load,
                        stftPct = stft,
                        ltftPct = ltft,
                        intakeMapKpa = map,
                        isSimulated = false
                    )
                }
            }
            if (ff != null) {
                _freezeFrame.value = ff
                sessionLogger.info("FREEZE", "Считан Freeze Frame (DTC=${ff.dtcCode ?: "?"}, RPM=${ff.rpm?.toInt()})")
            }
        } catch (e: Exception) {
            sessionLogger.warn("FREEZE", "Не удалось считать Mode 02 Freeze Frame: ${e.message}")
        }
    }

    private fun updateSimulatedReadings(tick: Int) {
        val phase = tick * 0.14f
        val accelWave = ((sin(phase.toDouble()) + 1.0) * 0.5).toFloat()
        val cruiseWave = ((cos((phase * 0.45f).toDouble()) + 1.0) * 0.5).toFloat()

        val rpm = (820f + accelWave * 2850f + Random.nextFloat() * 90f).coerceIn(750f, 6500f)
        val speed = (18f + cruiseWave * 94f + accelWave * 18f).coerceIn(0f, 165f)
        val coolant = (84f + (tick.coerceAtMost(80) / 80f) * 8f + sin(phase * 0.2) * 1.8).toFloat()
        val voltage = (13.9f + sin(phase * 0.3) * 0.25f).toFloat()
        val load = (22f + accelWave * 54f).coerceIn(14f, 92f)
        val throttle = (14f + accelWave * 48f).coerceIn(10f, 88f)
        val intakeTemp = (27f + sin(phase * 0.15) * 3.5).toFloat()
        val mapKpa = (36f + accelWave * 112f).coerceIn(28f, 195f)
        val maf = (3.4f + (rpm / 1000f) * (load / 100f) * 18f).coerceIn(2.5f, 140f)
        val fuel = (68f - (tick * 0.008f)).coerceIn(12f, 100f)
        val oilTemp = (89f + (tick.coerceAtMost(100) / 100f) * 11f).toFloat()
        val timing = (12f + accelWave * 22f).coerceIn(5f, 42f)
        val stft = (sin(phase * 0.6) * 3.8).toFloat()
        val ltft = (1.6f + cos(phase * 0.1) * 0.7f).toFloat()

        val isEng = settings.value.isEnglish
        val currentSensors = sensors.value
        fun resolveSensor(pid: ObdPid): TelemetrySensor =
            currentSensors.find { it.id == pid.modeAndPid } ?: pid.toTelemetrySensor(isEng)

        recordSensorValue(resolveSensor(ObdPid.ENGINE_RPM), rpm)
        recordSensorValue(resolveSensor(ObdPid.VEHICLE_SPEED), speed)
        recordSensorValue(resolveSensor(ObdPid.COOLANT_TEMP), coolant)
        recordSensorValue(resolveSensor(ObdPid.CONTROL_MODULE_VOLTAGE), voltage)
        recordSensorValue(resolveSensor(ObdPid.ENGINE_LOAD), load)
        recordSensorValue(resolveSensor(ObdPid.THROTTLE_POS), throttle)
        recordSensorValue(resolveSensor(ObdPid.INTAKE_AIR_TEMP), intakeTemp)
        recordSensorValue(resolveSensor(ObdPid.INTAKE_MAP), mapKpa)
        recordSensorValue(resolveSensor(ObdPid.MAF_FLOW), maf)
        recordSensorValue(resolveSensor(ObdPid.FUEL_LEVEL), fuel)
        recordSensorValue(resolveSensor(ObdPid.OIL_TEMP), oilTemp)
        recordSensorValue(resolveSensor(ObdPid.TIMING_ADVANCE), timing)
        recordSensorValue(resolveSensor(ObdPid.SHORT_FUEL_TRIM), stft)
        recordSensorValue(resolveSensor(ObdPid.LONG_FUEL_TRIM), ltft)

        for (custom in customPids.value) {
            if (!custom.enabled) continue
            val sensor = currentSensors.find { it.id == custom.sensorId } ?: custom.toTelemetrySensor()
            val wave = ((sin((tick + custom.id * 5) * 0.12) + 1.0) * 0.5).toFloat()
            val value = sensor.minRange + wave * (sensor.maxRange - sensor.minRange) * 0.75f
            recordSensorValue(sensor, value)
        }

        feedTripComputerFromLatestReadings()
        datalogger.onReadingsSnapshot(_readings.value)
        if (widgetPushCounter++ % 8 == 0) {
            pushWidgetSnapshot()
        }
    }

    private fun feedTripComputerFromLatestReadings() {
        val r = _readings.value
        val s = settings.value
        val profile = activeVehicleProfile()
        val rawSpeed = r[ObdPid.VEHICLE_SPEED.modeAndPid]?.value
        gpsSpeedMonitor.updateObdRawSpeed(rawSpeed)
        val rpm = r[ObdPid.ENGINE_RPM.modeAndPid]?.value
        val coolant = r[ObdPid.COOLANT_TEMP.modeAndPid]?.value
        val maf = r[ObdPid.MAF_FLOW.modeAndPid]?.value
        val map = r[ObdPid.INTAKE_MAP.modeAndPid]?.value
        val iat = r[ObdPid.INTAKE_AIR_TEMP.modeAndPid]?.value

        tripManager.onTelemetrySample(
            speedKmh = rawSpeed,
            rpm = rpm,
            coolantTempC = coolant,
            mafGramsPerSec = maf,
            intakeMapKpa = map,
            intakeAirTempC = iat,
            displacementLiters = profile?.displacementLiters ?: 2.0f,
            fuelType = profile?.fuelTypeEnum ?: FuelType.PETROL,
            fuelPricePerLiter = profile?.fuelPricePerLiter ?: 58.0f,
            speedCalibrationFactor = s.speedCalibrationFactor,
            fuelCalibrationFactor = s.fuelCalibrationFactor,
            curbWeightKg = profile?.curbWeightKg ?: 1450,
            shiftLightRpm = profile?.shiftLightRpm ?: s.shiftLightRpm,
            onAccelRunCompleted = { discipline, sec, peakSpeed, peakRpm ->
                val timerSnapshot = tripManager.accelTimer.value
                val csvCurve = timerSnapshot.samples.joinToString("|") { pt ->
                    String.format(Locale.US, "%.2f;%.1f;%.0f;%.0f", pt.elapsedSec, pt.speedKmh, pt.rpm, pt.horsepower)
                }
                scope.launch {
                    val vName = profile?.name ?: "Auto"
                    accelerationRunDao.insert(
                        AccelerationRunEntity(
                            vehicleName = vName,
                            discipline = discipline.id,
                            elapsedSec = sec,
                            peakSpeedKmh = peakSpeed,
                            peakRpm = peakRpm,
                            timestampMs = System.currentTimeMillis(),
                            peakHorsepower = timerSnapshot.peakHorsepower,
                            peakTorqueNm = timerSnapshot.peakTorqueNm,
                            curveCsv = csvCurve
                        )
                    )
                    sessionLogger.info(
                        "PERF",
                        "Замер ${discipline.id}: ${String.format(Locale.US, "%.2f", sec)} с (пик ${peakSpeed.toInt()} км/ч, ${timerSnapshot.peakHorsepower.toInt()} л.с.)"
                    )
                }
            }
        )
    }

    private fun buildSimulatedMode06Tests(hasWarning: Boolean): List<Mode06TestResult> {
        return listOf(
            Mode06TestResult(
                midHex = "01",
                tidHex = "81",
                nameRu = "Датчик O2 (Банк 1, Д1) отклик",
                nameEn = "O2 Sensor B1S1 Rich-to-Lean",
                value = if (hasWarning) 142 else 210,
                minLimit = 120,
                maxLimit = 450,
                passed = true,
                unitLabel = "mV"
            ),
            Mode06TestResult(
                midHex = "21",
                tidHex = "80",
                nameRu = "Эффективность катализатора B1",
                nameEn = "Catalyst Monitor Efficiency B1",
                value = if (hasWarning) 41 else 78,
                minLimit = 50,
                maxLimit = 100,
                passed = !hasWarning,
                unitLabel = "%"
            ),
            Mode06TestResult(
                midHex = "A2",
                tidHex = "0B",
                nameRu = "Пропуски зажигания (цилиндр 1)",
                nameEn = "Misfire Cylinder 1 EWMA Count",
                value = if (hasWarning) 4 else 0,
                minLimit = 0,
                maxLimit = 25,
                passed = true,
                unitLabel = "cnt"
            )
        )
    }

    private fun pushWidgetSnapshot() {
        scope.launch {
            val isEng = settings.value.isEnglish
            val profile = activeVehicleProfile()
            val conn = _connectionState.value
            val latestReadings = _readings.value
            val coolant = latestReadings[ObdPid.COOLANT_TEMP.modeAndPid]?.value
            val voltage = latestReadings[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value
            val coolantStr = coolant?.let { "${it.toInt()}°C" } ?: "--"
            val voltStr = voltage?.let { String.format(Locale.US, "%.1fV", it) } ?: "--"

            val (statusLabel, statusColor) = when (conn) {
                is ObdConnectionState.Connected -> (if (isEng) "ONLINE" else "ONLINE") to Color.parseColor("#10B981")
                is ObdConnectionState.Simulation -> (if (isEng) "SIM" else "ТЕСТ") to Color.parseColor("#FFB020")
                is ObdConnectionState.Connecting -> (if (isEng) "LINK" else "ПОИСК") to Color.parseColor("#00D4FF")
                is ObdConnectionState.Error -> (if (isEng) "ERR" else "ОШИБКА") to Color.parseColor("#EF4444")
                is ObdConnectionState.Disconnected -> (if (isEng) "OFFLINE" else "ОФЛАЙН") to Color.parseColor("#94A3B8")
            }

            val lastTrip = tripRecordDao.observeTrips().first().firstOrNull()
            val tripLine = if (lastTrip != null) {
                val avgStr = lastTrip.avgLPer100Km?.let { String.format(Locale.US, "%.1f L/100", it) } ?: "--"
                if (isEng) {
                    "Last trip: ${String.format(Locale.US, "%.1f", lastTrip.distanceKm)} km • $avgStr"
                } else {
                    "Поездка: ${String.format(Locale.US, "%.1f", lastTrip.distanceKm)} км • $avgStr"
                }
            } else {
                if (isEng) "Last trip: no trips yet" else "Поездка: нет данных"
            }

            val odo = profile?.odometerKm ?: 0f
            val maintList = profile?.let { maintenanceDao.getForVehicle(it.id) }.orEmpty()
            val nextMaint = maintList.minByOrNull { item ->
                val driven = (odo - item.lastServiceOdometerKm).coerceAtLeast(0f)
                item.intervalKm - driven
            }
            val maintLine = if (nextMaint != null) {
                val remKm = (nextMaint.intervalKm - (odo - nextMaint.lastServiceOdometerKm).coerceAtLeast(0f)).toInt()
                if (remKm <= 0) {
                    if (isEng) "Service overdue: ${nextMaint.title}" else "ТО просрочено: ${nextMaint.title}"
                } else {
                    if (isEng) "Next: ${nextMaint.title} in $remKm km" else "ТО: ${nextMaint.title} через $remKm км"
                }
            } else {
                if (isEng) "Service schedule OK" else "ТО: график в норме"
            }

            ObdStatusWidgetProvider.pushSnapshot(
                context,
                ObdWidgetSnapshot(
                    vehicleName = profile?.name ?: (if (isEng) "OBD2 Auto" else "OBD2 Авто"),
                    statusLabel = statusLabel,
                    statusColorHex = statusColor,
                    primaryTelemetry = if (isEng) "ECT $coolantStr  •  $voltStr" else "ОЖ $coolantStr  •  $voltStr",
                    lastTripLine = tripLine,
                    maintenanceLine = maintLine,
                    dtcCount = _dtcState.value.codes.size
                )
            )
        }
    }

    private data class DtcReadBatch(
        val milRaw: String,
        val dtcRaw: String,
        val pendingRaw: String,
        val mode06RawList: List<String>
    )

    private fun applyAlertOverride(sensor: TelemetrySensor, userSettings: ObdUserSettings): TelemetrySensor {
        val override = userSettings.alertOverrides[sensor.id] ?: return sensor
        return sensor.copy(
            alertsEnabled = override.alertsEnabled,
            warningThreshold = override.warningHigh,
            criticalThreshold = override.criticalHigh,
            lowWarningThreshold = override.warningLow,
            lowCriticalThreshold = override.criticalLow
        )
    }

    private fun recordSensorValue(sensor: TelemetrySensor, value: Float) {
        val currentSettings = settings.value
        val effectiveSensor = applyAlertOverride(sensor, currentSettings)
        var createdReading: SensorReading? = null
        _readings.update { currentMap ->
            val prev = currentMap[effectiveSensor.id]
            val newHistory = ((prev?.history ?: emptyList()) + value).takeLast(36)
            val newMin = if (prev == null) value else minOf(prev.minValue, value)
            val newMax = if (prev == null) value else maxOf(prev.maxValue, value)
            val reading = SensorReading(
                sensorId = effectiveSensor.id,
                sensor = effectiveSensor,
                value = value,
                minValue = newMin,
                maxValue = newMax,
                history = newHistory,
                alertLevel = effectiveSensor.evaluateAlert(value),
                timestampMs = System.currentTimeMillis()
            )
            createdReading = reading
            currentMap + (effectiveSensor.id to reading)
        }
        createdReading?.let { reading ->
            alertManager.evaluateAndNotify(effectiveSensor, reading, currentSettings)
        }
    }

    private suspend fun disconnectInternal(resetReadings: Boolean) {
        try {
            activeTransport?.disconnect()
        } catch (_: Exception) {
        }
        activeTransport = null
        activeCanHeader = "7DF"
        _supportedMode01Pids.value = null
        alertManager.resetSessionState()
        if (resetReadings) {
            _readings.value = emptyMap()
            _vehicleInfo.value = VehicleInfoState()
            _imReadiness.value = null
            _freezeFrame.value = null
        }
    }

    private fun startForegroundService() {
        runCatching {
            val intent = Intent(context, ObdForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun stopForegroundService() {
        runCatching {
            context.stopService(Intent(context, ObdForegroundService::class.java))
        }
    }
}
