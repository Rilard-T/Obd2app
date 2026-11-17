package com.example.obd.data

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.obd.model.DtcState
import com.example.obd.model.ObdConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    OBD_TX,
    OBD_RX
}

data class ObdLogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun formatLine(formatter: SimpleDateFormat): String {
        val timeStr = formatter.format(Date(timestampMs))
        return "$timeStr [${level.name}/$tag] $message"
    }
}

class ObdSessionLogger(private val context: Context) {

    companion object {
        private const val MAX_IN_MEMORY_ENTRIES = 500
        private const val MAX_LOG_FILE_BYTES = 512 * 1024L
        private const val LOG_FILE_NAME = "obd2_session_log.txt"
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileLock = Any()

    private val _entries = MutableStateFlow<List<ObdLogEntry>>(emptyList())
    val entries: StateFlow<List<ObdLogEntry>> = _entries.asStateFlow()

    private val logFile: File
        get() {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, LOG_FILE_NAME)
        }

    init {
        info("APP", "Сессия запущена (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT})")
    }

    fun info(tag: String, message: String) = append(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = append(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) {
            "$message | ${throwable::class.java.simpleName}: ${throwable.message}"
        } else {
            message
        }
        append(LogLevel.ERROR, tag, full)
    }

    fun tx(command: String) = append(LogLevel.OBD_TX, "ELM327", "TX -> ${command.trim()}")
    fun rx(response: String) {
        val singleLine = response.replace("\r", " ").replace("\n", " ").trim()
        append(LogLevel.OBD_RX, "ELM327", "RX <- $singleLine")
    }

    fun clear() {
        _entries.value = emptyList()
        synchronized(fileLock) {
            runCatching {
                if (logFile.exists()) logFile.writeText("")
            }
        }
        info("APP", "Журнал логов очищен пользователем")
    }

    fun buildReportText(
        connectionState: ObdConnectionState,
        dtcState: DtcState,
        hasBtPermissions: Boolean,
        isBtEnabled: Boolean,
        customPidCount: Int,
        isEnglish: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        if (isEnglish) {
            sb.appendLine("OBD2 AUTO — ERROR REPORT & DIAGNOSTIC SESSION LOG")
            sb.appendLine("==================================================")
            sb.appendLine("Generated: ${fileDateFormat.format(Date())}")
            sb.appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            sb.appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            sb.appendLine("Bluetooth On: $isBtEnabled | Permissions Granted: $hasBtPermissions")
            sb.appendLine("Custom PIDs: $customPidCount")
            sb.appendLine("OBD2 Status: $connectionState")
            sb.appendLine("MIL Status: ${if (dtcState.milOn) "ON" else "OFF"} | DTCs: ${dtcState.codes.size}")
            if (dtcState.codes.isNotEmpty()) {
                sb.appendLine("DTC Codes: ${dtcState.codes.joinToString(", ") { it.code }}")
            }
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("EVENT LOG:")
            sb.appendLine("--------------------------------------------------")
        } else {
            sb.appendLine("OBD2 АВТО — ОТЧЁТ ОБ ОШИБКЕ И ДИАГНОСТИЧЕСКИЙ ЛОГ")
            sb.appendLine("==================================================")
            sb.appendLine("Дата формирования: ${fileDateFormat.format(Date())}")
            sb.appendLine("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            sb.appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            sb.appendLine("ОС Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            sb.appendLine("Bluetooth включён: $isBtEnabled | Разрешения выданы: $hasBtPermissions")
            sb.appendLine("Пользовательских PID: $customPidCount")
            sb.appendLine("Статус OBD2: $connectionState")
            sb.appendLine("Статус MIL: ${if (dtcState.milOn) "ВКЛ" else "ВЫКЛ"} | Ошибок: ${dtcState.codes.size}")
            if (dtcState.codes.isNotEmpty()) {
                sb.appendLine("Коды DTC: ${dtcState.codes.joinToString(", ") { it.code }}")
            }
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("ЖУРНАЛ СОБЫТИЙ:")
            sb.appendLine("--------------------------------------------------")
        }

        val diskContent = synchronized(fileLock) {
            runCatching {
                if (logFile.exists()) logFile.readText() else ""
            }.getOrDefault("")
        }
        if (diskContent.isNotBlank()) {
            sb.append(diskContent)
        } else {
            val snapshot = _entries.value
            synchronized(timeFormat) {
                for (entry in snapshot) {
                    sb.appendLine(entry.formatLine(timeFormat))
                }
            }
        }
        return sb.toString()
    }

    fun createShareableReportFile(reportText: String): File {
        val dir = File(context.cacheDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "obd2_report_$stamp.txt")
        outFile.writeText(reportText, Charsets.UTF_8)
        return outFile
    }

    private fun append(level: LogLevel, tag: String, message: String) {
        val entry = ObdLogEntry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        _entries.update { current ->
            (current + entry).takeLast(MAX_IN_MEMORY_ENTRIES)
        }
        val line = synchronized(timeFormat) { entry.formatLine(timeFormat) }
        synchronized(fileLock) {
            runCatching {
                val file = logFile
                if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                    val tail = file.readLines().takeLast(250).joinToString("\n") + "\n"
                    file.writeText(tail, Charsets.UTF_8)
                }
                file.appendText("$line\n", Charsets.UTF_8)
            }
        }
    }
}
