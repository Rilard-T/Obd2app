package com.example.obd.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.obd.ObdForegroundService
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.BluetoothTransportType
import com.example.obd.model.DtcState
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.SensorReading
import com.example.obd.protocol.BleObdTransport
import com.example.obd.protocol.BluetoothClassicObdTransport
import com.example.obd.protocol.BluetoothScanner
import com.example.obd.protocol.Elm327Parser
import com.example.obd.protocol.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ObdTelemetryRepository(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val diagnosticScanDao: DiagnosticScanDao,
    val bluetoothScanner: BluetoothScanner
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<Map<ObdPid, SensorReading>>(emptyMap())
    val readings: StateFlow<Map<ObdPid, SensorReading>> = _readings.asStateFlow()

    private val _dtcState = MutableStateFlow(DtcState())
    val dtcState: StateFlow<DtcState> = _dtcState.asStateFlow()

    val settings: StateFlow<ObdUserSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ObdUserSettings()
    )

    val recentScans = diagnosticScanDao.observeRecentScans()

    private var activeTransport: ObdTransport? = null
    private var pollingJob: Job? = null
    private var unsupportedPids = mutableSetOf<ObdPid>()
    private var simTick = 0

    fun connectToDevice(device: BluetoothDeviceItem) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            disconnectInternal(resetReadings = true)
            _connectionState.value = ObdConnectionState.Connecting(
                deviceName = device.name,
                step = "Установка Bluetooth-соединения..."
            )

            val btAdapter = bluetoothScanner.bluetoothAdapter
            if (btAdapter == null || !bluetoothScanner.isBluetoothEnabled()) {
                _connectionState.value = ObdConnectionState.Error(
                    message = "Bluetooth выключен. Включите Bluetooth на телефоне.",
                    deviceName = device.name
                )
                return@launch
            }

            val transport: ObdTransport = when (device.transport) {
                BluetoothTransportType.CLASSIC_SPP ->
                    BluetoothClassicObdTransport(btAdapter, device.address)
                BluetoothTransportType.BLE ->
                    BleObdTransport(context, btAdapter, device.address)
            }
            activeTransport = transport

            try {
                transport.connect()

                _connectionState.value = ObdConnectionState.Connecting(
                    deviceName = device.name,
                    step = "Инициализация ELM327 (ATZ, ATSP0)..."
                )

                val protocol = initializeElm327(transport)
                settingsDataStore.saveLastDevice(device.address, device.name, device.transport)
                unsupportedPids.clear()

                _connectionState.value = ObdConnectionState.Connected(
                    deviceName = device.name,
                    address = device.address,
                    protocol = protocol,
                    transport = device.transport,
                    pollingHz = 0f
                )
                startForegroundService()

                // Read initial MIL & DTC status once connected
                readDtcsInternal(transport, device.name, protocol)

                // Enter continuous telemetry polling loop
                runRealPollingLoop(transport, device, protocol)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                disconnectInternal(resetReadings = false)
                _connectionState.value = ObdConnectionState.Error(
                    message = e.message ?: "Ошибка подключения к OBD2 адаптеру",
                    deviceName = device.name
                )
            }
        }
    }

    fun startSimulationMode() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            disconnectInternal(resetReadings = true)
            _connectionState.value = ObdConnectionState.Simulation(pollingHz = 2.8f)
            _dtcState.value = DtcState(
                milOn = false,
                ecuReportedCount = 0,
                codes = emptyList(),
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = "Режим симуляции: неисправности отсутствуют"
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
        pollingJob?.cancel()
        pollingJob = null
        scope.launch {
            disconnectInternal(resetReadings = false)
            _connectionState.value = ObdConnectionState.Disconnected
            stopForegroundService()
        }
    }

    fun scanDtcs() {
        scope.launch {
            val state = _connectionState.value
            if (state is ObdConnectionState.Simulation) {
                _dtcState.update { it.copy(isScanning = true, statusMessage = "Сканирование ЭБУ (симуляция)...") }
                delay(700)
                val demoCodes = listOf(
                    Elm327Parser.decodeDtc("P0171"),
                    Elm327Parser.decodeDtc("P0420")
                )
                val result = DtcState(
                    milOn = true,
                    ecuReportedCount = demoCodes.size,
                    codes = demoCodes,
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = "Найдено ошибок: ${demoCodes.size} (симуляция)"
                )
                _dtcState.value = result
                diagnosticScanDao.insertScan(
                    DiagnosticScanEntity(
                        timestampMs = System.currentTimeMillis(),
                        adapterName = "Симуляция OBD2",
                        protocol = "ISO 15765-4 CAN (Demo)",
                        milOn = true,
                        dtcCount = demoCodes.size,
                        dtcSummary = demoCodes.joinToString(", ") { it.code },
                        batteryVoltage = _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE]?.value
                    )
                )
                return@launch
            }

            val transport = activeTransport
            if (state !is ObdConnectionState.Connected || transport == null || !transport.isConnected) {
                _dtcState.update {
                    it.copy(isScanning = false, statusMessage = "Подключите OBD2 адаптер для чтения ошибок")
                }
                return@launch
            }
            readDtcsInternal(transport, state.deviceName, state.protocol)
        }
    }

    fun clearDtcs() {
        scope.launch {
            val state = _connectionState.value
            if (state is ObdConnectionState.Simulation) {
                _dtcState.update { it.copy(isScanning = true, statusMessage = "Сброс ошибок (симуляция)...") }
                delay(500)
                _dtcState.value = DtcState(
                    milOn = false,
                    ecuReportedCount = 0,
                    codes = emptyList(),
                    isScanning = false,
                    lastScanTimestampMs = System.currentTimeMillis(),
                    statusMessage = "Ошибки ЭБУ сброшены (симуляция)"
                )
                return@launch
            }

            val transport = activeTransport ?: return@launch
            if (!transport.isConnected) return@launch
            _dtcState.update { it.copy(isScanning = true, statusMessage = "Отправка команды Mode 04...") }
            try {
                transport.sendCommand("04", timeoutMs = 4000L)
                delay(300)
                readDtcsInternal(transport, (state as? ObdConnectionState.Connected)?.deviceName ?: "ELM327",
                    (state as? ObdConnectionState.Connected)?.protocol ?: "OBD2")
                _dtcState.update { it.copy(statusMessage = "Память ошибок ЭБУ очищена") }
            } catch (e: Exception) {
                _dtcState.update {
                    it.copy(isScanning = false, statusMessage = "Ошибка сброса DTC: ${e.message}")
                }
            }
        }
    }

    fun toggleAndroidAutoPid(pid: ObdPid) {
        scope.launch { settingsDataStore.toggleAndroidAutoPid(pid) }
    }

    fun setPollingIntervalMs(intervalMs: Long) {
        scope.launch { settingsDataStore.setPollingIntervalMs(intervalMs) }
    }

    fun setUseFahrenheit(useFahrenheit: Boolean) {
        scope.launch { settingsDataStore.setUseFahrenheit(useFahrenheit) }
    }

    fun setPressureUnit(unit: com.example.obd.model.PressureUnit) {
        scope.launch { settingsDataStore.setPressureUnit(unit) }
    }

    fun clearScanHistory() {
        scope.launch { diagnosticScanDao.clearHistory() }
    }

    private suspend fun initializeElm327(transport: ObdTransport): String {
        val resetResp = transport.sendCommand("ATZ", timeoutMs = 4000L)
        if (resetResp.isBlank()) {
            throw IllegalStateException("Адаптер не ответил на ATZ")
        }
        delay(250)
        transport.sendCommand("ATE0") // Echo off
        transport.sendCommand("ATL0") // Linefeeds off
        transport.sendCommand("ATS0") // Spaces off for faster parsing
        transport.sendCommand("ATH0") // Headers off
        transport.sendCommand("ATSP0") // Auto-detect vehicle OBD2 protocol

        // Probe ECU connection with Mode 01 PID 00
        val probe = transport.sendCommand("0100", timeoutMs = 6000L)
        if (Elm327Parser.isErrorResponse(probe)) {
            throw IllegalStateException("ЭБУ автомобиля не отвечает (проверьте зажигание)")
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
        while (kotlinx.coroutines.currentCoroutineContext().isActive && transport.isConnected) {
            val currentSettings = settings.value
            val priorityPids = currentSettings.androidAutoPids
            val secondaryPids = ObdPid.entries.filter { it !in priorityPids && it !in unsupportedPids }

            // Poll all priority Android Auto PIDs every cycle, plus 2 secondary PIDs per cycle
            val secondarySlice = if (secondaryPids.isNotEmpty()) {
                val startIdx = (cycle * 2) % secondaryPids.size
                listOfNotNull(
                    secondaryPids.getOrNull(startIdx),
                    secondaryPids.getOrNull((startIdx + 1) % secondaryPids.size)
                )
            } else {
                emptyList()
            }

            val pidsToPoll = (priorityPids + secondarySlice).distinct()
            for (pid in pidsToPoll) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive || !transport.isConnected) break
                if (pid in unsupportedPids) continue

                val raw = transport.sendCommand(pid.modeAndPid, timeoutMs = 1500L)
                var value = Elm327Parser.parseMode01Pid(pid, raw)

                if (value == null && pid == ObdPid.CONTROL_MODULE_VOLTAGE) {
                    val atrv = transport.sendCommand("ATRV", timeoutMs = 1200L)
                    value = Elm327Parser.parseAtrvVoltage(atrv)
                }

                if (value != null) {
                    recordSensorValue(pid, value)
                } else if (raw.uppercase().contains("NO DATA")) {
                    unsupportedPids.add(pid)
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
        _dtcState.update { it.copy(isScanning = true, statusMessage = "Чтение ошибок ЭБУ...") }
        try {
            val milRaw = transport.sendCommand("0101", timeoutMs = 2500L)
            val milInfo = Elm327Parser.parseMilStatus(milRaw)
            val dtcRaw = transport.sendCommand("03", timeoutMs = 3500L)
            val codes = Elm327Parser.parseMode03Dtcs(dtcRaw)
            val milOn = milInfo?.first ?: codes.isNotEmpty()
            val count = maxOf(milInfo?.second ?: 0, codes.size)

            _dtcState.value = DtcState(
                milOn = milOn,
                ecuReportedCount = count,
                codes = codes,
                isScanning = false,
                lastScanTimestampMs = System.currentTimeMillis(),
                statusMessage = if (codes.isEmpty()) "Ошибок ЭБУ не обнаружено" else "Найдено ошибок: ${codes.size}"
            )

            diagnosticScanDao.insertScan(
                DiagnosticScanEntity(
                    timestampMs = System.currentTimeMillis(),
                    adapterName = adapterName,
                    protocol = protocol,
                    milOn = milOn,
                    dtcCount = codes.size,
                    dtcSummary = if (codes.isEmpty()) "Ошибок нет" else codes.joinToString(", ") { it.code },
                    batteryVoltage = _readings.value[ObdPid.CONTROL_MODULE_VOLTAGE]?.value
                )
            )
        } catch (e: Exception) {
            _dtcState.update {
                it.copy(isScanning = false, statusMessage = "Ошибка чтения DTC: ${e.message}")
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

        recordSensorValue(ObdPid.ENGINE_RPM, rpm)
        recordSensorValue(ObdPid.VEHICLE_SPEED, speed)
        recordSensorValue(ObdPid.COOLANT_TEMP, coolant)
        recordSensorValue(ObdPid.CONTROL_MODULE_VOLTAGE, voltage)
        recordSensorValue(ObdPid.ENGINE_LOAD, load)
        recordSensorValue(ObdPid.THROTTLE_POS, throttle)
        recordSensorValue(ObdPid.INTAKE_AIR_TEMP, intakeTemp)
        recordSensorValue(ObdPid.INTAKE_MAP, mapKpa)
        recordSensorValue(ObdPid.MAF_FLOW, maf)
        recordSensorValue(ObdPid.FUEL_LEVEL, fuel)
        recordSensorValue(ObdPid.OIL_TEMP, oilTemp)
        recordSensorValue(ObdPid.TIMING_ADVANCE, timing)
        recordSensorValue(ObdPid.SHORT_FUEL_TRIM, stft)
        recordSensorValue(ObdPid.LONG_FUEL_TRIM, ltft)
    }

    private fun recordSensorValue(pid: ObdPid, value: Float) {
        _readings.update { currentMap ->
            val prev = currentMap[pid]
            val newHistory = ((prev?.history ?: emptyList()) + value).takeLast(36)
            val newMin = if (prev == null) value else minOf(prev.minValue, value)
            val newMax = if (prev == null) value else maxOf(prev.maxValue, value)
            currentMap + (
                pid to SensorReading(
                    pid = pid,
                    value = value,
                    minValue = newMin,
                    maxValue = newMax,
                    history = newHistory,
                    alertLevel = pid.evaluateAlert(value),
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun disconnectInternal(resetReadings: Boolean) {
        try {
            activeTransport?.disconnect()
        } catch (_: Exception) {
        }
        activeTransport = null
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
