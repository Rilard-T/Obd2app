package com.example.obd.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.obd.ObdForegroundService
import com.example.obd.model.AppLanguage
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.BluetoothTransportType
import com.example.obd.model.CockpitPreset
import com.example.obd.model.DtcState
import com.example.obd.model.Elm327BenchmarkResult
import com.example.obd.model.LivePidProbeResult
import com.example.obd.model.ObdAlertEvent
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import com.example.obd.model.TtsVoiceOption
import com.example.obd.protocol.BleObdTransport
import com.example.obd.protocol.BluetoothClassicObdTransport
import com.example.obd.protocol.BluetoothScanner
import com.example.obd.protocol.Elm327Benchmark
import com.example.obd.protocol.Elm327Parser
import com.example.obd.protocol.ObdTransport
import com.example.obd.protocol.TorqueEquationEvaluator
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ObdTelemetryRepository(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val diagnosticScanDao: DiagnosticScanDao,
    private val customPidDao: CustomPidDao,
    val bluetoothScanner: BluetoothScanner,
    val sessionLogger: ObdSessionLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

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

    private val _supportedMode01Pids = MutableStateFlow<Set<String>?>(null)
    val supportedMode01Pids: StateFlow<Set<String>?> = _supportedMode01Pids.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<Elm327BenchmarkResult?>(null)
    val benchmarkResult: StateFlow<Elm327BenchmarkResult?> = _benchmarkResult.asStateFlow()

    private val _isRunningBenchmark = MutableStateFlow(false)
    val isRunningBenchmark: StateFlow<Boolean> = _isRunningBenchmark.asStateFlow()

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
    private val unsupportedSensorIds = mutableSetOf<String>()
    private var simTick = 0
    @Volatile
    private var activeCanHeader: String = "7DF"
    @Volatile
    private var userManuallyDisconnected: Boolean = false

    fun tryAutoConnect(reason: String) {
        val currentConn = _connectionState.value
        val currentSettings = settings.value
        if (currentConn.isActiveTelemetry || currentConn is ObdConnectionState.Connecting) return
        if (userManuallyDisconnected || !currentSettings.autoConnectOnLaunch) return
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

    fun connectToDevice(device: BluetoothDeviceItem) {
        userManuallyDisconnected = false
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val isEng = settings.value.isEnglish
            disconnectInternal(resetReadings = true)
            sessionLogger.info(
                "CONN",
                "Запуск подключения к '${device.name}' (${device.address}, ${device.transport.name})"
            )
            _connectionState.value = ObdConnectionState.Connecting(
                deviceName = device.name,
                step = if (isEng) "Establishing Bluetooth link..." else "Установка Bluetooth-соединения..."
            )

            val btAdapter = bluetoothScanner.bluetoothAdapter
            if (btAdapter == null || !bluetoothScanner.isBluetoothEnabled()) {
                val msg = if (isEng) {
                    "Bluetooth is off. Please enable Bluetooth on your phone."
                } else {
                    "Bluetooth выключен. Включите Bluetooth на телефоне."
                }
                sessionLogger.warn("CONN", msg)
                _connectionState.value = ObdConnectionState.Error(
                    message = msg,
                    deviceName = device.name
                )
                return@launch
            }

            val transport: ObdTransport = when (device.transport) {
                BluetoothTransportType.CLASSIC_SPP ->
                    BluetoothClassicObdTransport(btAdapter, device.address, sessionLogger)
                BluetoothTransportType.BLE ->
                    BleObdTransport(context, btAdapter, device.address, sessionLogger)
            }
            activeTransport = transport

            try {
                transport.connect()

                _connectionState.value = ObdConnectionState.Connecting(
                    deviceName = device.name,
                    step = if (isEng) "Initializing ELM327 & PID map..." else "Инициализация ELM327 и карты PID..."
                )

                val protocol = initializeElm327(transport)
                settingsDataStore.saveLastDevice(device.address, device.name, device.transport)
                unsupportedSensorIds.clear()

                sessionLogger.info("CONN", "Успешное подключение к ${device.name}, протокол: $protocol")
                _connectionState.value = ObdConnectionState.Connected(
                    deviceName = device.name,
                    address = device.address,
                    protocol = protocol,
                    transport = device.transport,
                    pollingHz = 0f
                )
                startForegroundService()

                readDtcsInternal(transport, device.name, protocol)
                runRealPollingLoop(transport, device, protocol)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                sessionLogger.error("CONN", "Ошибка подключения к ${device.name}", e)
                disconnectInternal(resetReadings = false)
                _connectionState.value = ObdConnectionState.Error(
                    message = e.message ?: if (isEng) "Failed to connect to OBD2 adapter" else "Ошибка подключения к OBD2 адаптеру",
                    deviceName = device.name
                )
            }
        }
    }

    fun startSimulationMode() {
        userManuallyDisconnected = false
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val isEng = settings.value.isEnglish
            disconnectInternal(resetReadings = true)
            _supportedMode01Pids.value = ObdPid.entries.map { it.modeAndPid }.toSet()
            sessionLogger.info("SIM", "Активирован режим симуляции OBD2")
            _connectionState.value = ObdConnectionState.Simulation(pollingHz = 2.8f)
            _dtcState.value = DtcState(
                milOn = false,
                ecuReportedCount = 0,
                codes = emptyList(),
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = if (isEng) "Simulation mode: no fault codes" else "Режим симуляции: неисправности отсутствуют"
            )
            startForegroundService()

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
            disconnectInternal(resetReadings = false)
            _connectionState.value = ObdConnectionState.Disconnected
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
                        statusMessage = if (isEng) "Scanning ECU (simulation)..." else "Сканирование ЭБУ (симуляция)..."
                    )
                }
                delay(700)
                val demoCodes = listOf(
                    Elm327Parser.decodeDtc("P0171", isEng),
                    Elm327Parser.decodeDtc("P0420", isEng)
                )
                val result = DtcState(
                    milOn = true,
                    ecuReportedCount = demoCodes.size,
                    codes = demoCodes,
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = if (isEng) {
                        "Faults found: ${demoCodes.size} (simulation)"
                    } else {
                        "Найдено ошибок: ${demoCodes.size} (симуляция)"
                    }
                )
                _dtcState.value = result
                sessionLogger.info("DTC", "Симуляция сканирования DTC: ${demoCodes.joinToString { it.code }}")
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
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = if (isEng) "ECU fault codes cleared (simulation)" else "Ошибки ЭБУ сброшены (симуляция)"
                )
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

        // SAE J1979 Supported Mode 01 PID bitmap discovery
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
            val priorityIds = if (currentSettings.androidAutoDisplayMode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) {
                (activePreset.dialSensorIds +
                    activePreset.stripSensorIds +
                    currentSettings.androidAutoSensorIds).distinct()
            } else {
                (currentSettings.androidAutoSensorIds +
                    activePreset.dialSensorIds +
                    activePreset.stripSensorIds).distinct()
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

            val secondarySlice = if (secondarySensors.isNotEmpty()) {
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
                statusMessage = if (isEng) "Reading ECU trouble codes..." else "Чтение ошибок ЭБУ..."
            )
        }
        try {
            val (milRaw, dtcRaw) = commandMutex.withLock {
                ensureCanHeader(transport, "7DF")
                val mil = transport.sendCommand("0101", timeoutMs = 2500L)
                val dtc = transport.sendCommand("03", timeoutMs = 3500L)
                mil to dtc
            }
            val milInfo = Elm327Parser.parseMilStatus(milRaw)
            val codes = Elm327Parser.parseMode03Dtcs(dtcRaw, isEng)
            val milOn = milInfo?.first ?: codes.isNotEmpty()
            val count = maxOf(milInfo?.second ?: 0, codes.size)

            _dtcState.value = DtcState(
                milOn = milOn,
                ecuReportedCount = count,
                codes = codes,
                isScanning = false,
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = if (codes.isEmpty()) {
                    if (isEng) "No ECU trouble codes found" else "Ошибок ЭБУ не обнаружено"
                } else {
                    if (isEng) "Fault codes found: ${codes.size}" else "Найдено ошибок: ${codes.size}"
                }
            )
            sessionLogger.info("DTC", "Считано ошибок: ${codes.size} (MIL=${if (milOn) "ON" else "OFF"})")

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
    }

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
