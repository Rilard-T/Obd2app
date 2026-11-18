package com.example.obd.data

import com.example.obd.model.DataloggerFrame
import com.example.obd.model.DataloggerState
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * High-resolution telemetry blackbox datalogger.
 * Records synchronized channel snapshots for multi-channel comparison plotting and CSV export.
 */
class TelemetryDatalogger {

    private val _state = MutableStateFlow(DataloggerState())
    val state: StateFlow<DataloggerState> = _state.asStateFlow()

    @Synchronized
    fun startRecording() {
        _state.value = DataloggerState(
            isRecording = true,
            startedAtMs = System.currentTimeMillis(),
            frames = emptyList(),
            sampleCount = 0
        )
    }

    @Synchronized
    fun stopRecording() {
        _state.update { it.copy(isRecording = false) }
    }

    @Synchronized
    fun clearRecording() {
        _state.value = DataloggerState()
    }

    @Synchronized
    fun onReadingsSnapshot(readings: Map<String, SensorReading>) {
        val current = _state.value
        if (!current.isRecording || readings.isEmpty()) return

        val now = System.currentTimeMillis()
        val lastTs = current.frames.lastOrNull()?.timestampMs ?: 0L
        if (now - lastTs < 200L) return

        val valuesMap = readings.mapValues { (_, r) -> r.value }
        val nextFrames = (current.frames + DataloggerFrame(timestampMs = now, values = valuesMap))
            .takeLast(MAX_FRAMES)
        _state.value = current.copy(
            frames = nextFrames,
            sampleCount = current.sampleCount + 1
        )
    }

    fun exportCsv(
        sensors: List<TelemetrySensor>,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit
    ): String {
        val frames = _state.value.frames
        if (frames.isEmpty()) return "timestamp_ms\n"

        val recordedIds = frames.flatMap { it.values.keys }.distinct()
        val orderedSensors = recordedIds.mapNotNull { id -> sensors.find { it.id == id } }
        val headerCols = listOf("timestamp_ms", "elapsed_sec") + orderedSensors.map { sensor ->
            val unit = sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish = true)
            "\"${sensor.shortName.replace("\"", "'")} ($unit)\""
        }

        val startTs = frames.first().timestampMs
        val rows = frames.map { frame ->
            val elapsedSec = (frame.timestampMs - startTs) / 1000.0
            val vals = orderedSensors.map { sensor ->
                val raw = frame.values[sensor.id]
                if (raw == null) {
                    ""
                } else {
                    val converted = sensor.builtInPid?.convertValue(raw, useFahrenheit, pressureUnit) ?: raw
                    String.format(Locale.US, "%.2f", converted)
                }
            }
            (listOf(frame.timestampMs.toString(), String.format(Locale.US, "%.2f", elapsedSec)) + vals)
                .joinToString(",")
        }

        return (listOf(headerCols.joinToString(",")) + rows).joinToString("\n")
    }

    companion object {
        private const val MAX_FRAMES = 3600
    }
}
