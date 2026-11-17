package com.example.obd.protocol

import com.example.obd.model.Elm327BenchmarkResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

object Elm327Benchmark {

    suspend fun runRealBenchmark(
        transport: ObdTransport,
        commandMutex: Mutex,
        supportedMode01Count: Int?,
        isEnglish: Boolean = false
    ): Elm327BenchmarkResult = commandMutex.withLock {
        val versionRaw = runCatching { transport.sendCommand("ATI", timeoutMs = 1800L) }.getOrDefault("")
        val version = Elm327Parser.cleanResponse(versionRaw)
            .ifBlank { if (isEnglish) "Unknown" else "Неизвестно" }

        val protoRaw = runCatching { transport.sendCommand("ATDP", timeoutMs = 1500L) }.getOrDefault("")
        val protocol = Elm327Parser.cleanResponse(protoRaw)
            .removePrefix("AUTO,")
            .trim()
            .ifBlank { "OBD-II Auto" }

        val atatResp = runCatching { transport.sendCommand("ATAT2", timeoutMs = 1200L) }.getOrDefault("")
        val supportsAdaptiveTiming = atatResp.uppercase().contains("OK")

        val atstResp = runCatching { transport.sendCommand("ATST16", timeoutMs = 1200L) }.getOrDefault("")
        val supportsTimeoutCmd = atstResp.uppercase().contains("OK")

        val latencies = mutableListOf<Long>()
        val probeCmd = "010C"
        for (i in 0 until 8) {
            val t0 = System.currentTimeMillis()
            val resp = runCatching { transport.sendCommand(probeCmd, timeoutMs = 1800L) }.getOrDefault("")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            if (resp.isNotBlank()) {
                latencies.add(dt)
            }
        }
        // Fallback probe if engine off & 010C empty
        if (latencies.isEmpty()) {
            for (i in 0 until 5) {
                val t0 = System.currentTimeMillis()
                runCatching { transport.sendCommand("0100", timeoutMs = 1800L) }
                latencies.add((System.currentTimeMillis() - t0).coerceAtLeast(1L))
            }
        }

        val minMs = latencies.minOrNull() ?: 999L
        val maxMs = latencies.maxOrNull() ?: 999L
        val avgMs = (latencies.average().roundToInt().toLong()).coerceAtLeast(1L)
        val maxHz = 1000f / avgMs.toFloat()

        val (quality, summary) = classifyAdapter(
            version = version,
            avgLatencyMs = avgMs,
            supportsAdaptiveTiming = supportsAdaptiveTiming,
            supportsTimeoutCmd = supportsTimeoutCmd,
            isEnglish = isEnglish
        )

        Elm327BenchmarkResult(
            adapterVersion = version,
            protocolName = protocol,
            avgLatencyMs = avgMs,
            minLatencyMs = minMs,
            maxLatencyMs = maxMs,
            maxPollingHz = maxHz,
            supportsAdaptiveTiming = supportsAdaptiveTiming,
            supportsTimeoutCmd = supportsTimeoutCmd,
            supportedMode01Count = supportedMode01Count,
            qualityLabel = quality,
            summary = summary,
            isSimulated = false
        )
    }

    fun buildSimulatedBenchmark(isEnglish: Boolean = false): Elm327BenchmarkResult {
        return Elm327BenchmarkResult(
            adapterVersion = "ELM327 v1.5 (Demo)",
            protocolName = "ISO 15765-4 CAN (11 bit, 500 kbaud)",
            avgLatencyMs = 42L,
            minLatencyMs = 34L,
            maxLatencyMs = 58L,
            maxPollingHz = 23.8f,
            supportsAdaptiveTiming = true,
            supportsTimeoutCmd = true,
            supportedMode01Count = 14,
            qualityLabel = if (isEnglish) "Excellent (Demo)" else "Отличный (Симуляция)",
            summary = if (isEnglish) {
                "Demo profile: fast CAN adapter supporting ATAT2/ATST adaptive timing."
            } else {
                "Тестовый профиль: быстрый CAN-адаптер с поддержкой ATAT2/ATST и адаптивного опроса."
            },
            isSimulated = true
        )
    }

    internal fun classifyAdapter(
        version: String,
        avgLatencyMs: Long,
        supportsAdaptiveTiming: Boolean,
        supportsTimeoutCmd: Boolean,
        isEnglish: Boolean = false
    ): Pair<String, String> {
        val upperVer = version.uppercase()
        val isSlow = avgLatencyMs > 180L
        val missingAt = !supportsAdaptiveTiming || !supportsTimeoutCmd
        val looksLikeV21Clone = upperVer.contains("V2.1") && missingAt

        return if (isEnglish) {
            when {
                looksLikeV21Clone -> {
                    "Stripped Clone (v2.1)" to
                        "Adapter rejects AT commands (ATAT2/ATST) and is often unstable with extended PIDs. ELM327 v1.5 (PIC18F25K80) or OBDLink is recommended."
                }
                missingAt && isSlow -> {
                    "Slow Clone" to
                        "High latency ($avgLatencyMs ms) and incomplete AT support. Increase the polling interval in settings."
                }
                !missingAt && avgLatencyMs <= 65L -> {
                    "Excellent / Fast" to
                        "Low latency ($avgLatencyMs ms) and full adaptive timing support. Analog needles update smoothly."
                }
                !missingAt && avgLatencyMs <= 140L -> {
                    "Good (Stable)" to
                        "Stable response ($avgLatencyMs ms) with ATAT2/ATST support. Well suited for daily Android Auto driving."
                }
                else -> {
                    "Moderate Speed" to
                        "Latency is $avgLatencyMs ms. Keep 3–5 priority sensors selected for smooth gauge updates."
                }
            }
        } else {
            when {
                looksLikeV21Clone -> {
                    "Урезанный клон (v2.1)" to
                        "Адаптер отклоняет AT-команды (ATAT2/ATST) и часто нестабилен с расширенными PID. Рекомендуется ELM327 v1.5 (PIC18F25K80) или OBDLink."
                }
                missingAt && isSlow -> {
                    "Медленный клон" to
                        "Высокая задержка (${avgLatencyMs} мс) и неполная поддержка AT-команд. Увеличьте интервал опроса в настройках."
                }
                !missingAt && avgLatencyMs <= 65L -> {
                    "Отличный / Быстрый" to
                        "Низкая задержка (${avgLatencyMs} мс) и полная поддержка адаптивных таймингов. Стрелки обновляются максимально плавно."
                }
                !missingAt && avgLatencyMs <= 140L -> {
                    "Хороший (стабильный)" to
                        "Стабильный отклик (${avgLatencyMs} мс) и поддержка ATAT2/ATST. Подходит для постоянной езды в Android Auto."
                }
                else -> {
                    "Средняя скорость" to
                        "Задержка ${avgLatencyMs} мс. Для плавной работы оставьте 3–5 самых важных датчиков."
                }
            }
        }
    }
}
