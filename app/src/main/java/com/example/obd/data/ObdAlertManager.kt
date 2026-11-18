package com.example.obd.data

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdAlertEvent
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import com.example.obd.model.TtsVoiceOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ObdAlertManager(
    context: Context,
    private val sessionLogger: ObdSessionLogger
) {
    private val appContext = context.applicationContext
    private val alertSeq = AtomicInteger(100)

    private val _activeAlert = MutableStateFlow<ObdAlertEvent?>(null)
    val activeAlert: StateFlow<ObdAlertEvent?> = _activeAlert.asStateFlow()

    private val _alertEvents = MutableSharedFlow<ObdAlertEvent>(extraBufferCapacity = 8)
    val alertEvents: SharedFlow<ObdAlertEvent> = _alertEvents.asSharedFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val lastLevelBySensor = mutableMapOf<String, AlertLevel>()
    private val lastAlertTimestampBySensor = mutableMapOf<String, Long>()

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady: Boolean = false
    @Volatile
    private var ttsInitAttempted: Boolean = false
    @Volatile
    private var pendingSpeech: Pair<String, ObdUserSettings>? = null

    fun resetSessionState() {
        lastLevelBySensor.clear()
        lastAlertTimestampBySensor.clear()
        _activeAlert.value = null
    }

    fun dismissActiveAlert() {
        _activeAlert.value = null
    }

    fun preloadVoices() {
        if (ttsReady) {
            refreshAvailableVoices(tts)
        } else {
            initTtsIfNeeded()
        }
    }

    fun evaluateAndNotify(
        sensor: TelemetrySensor,
        reading: SensorReading,
        settings: ObdUserSettings
    ) {
        val level = reading.alertLevel
        val previousLevel = lastLevelBySensor[sensor.id] ?: AlertLevel.NORMAL
        lastLevelBySensor[sensor.id] = level

        if (level == AlertLevel.NORMAL) {
            if (_activeAlert.value?.sensorId == sensor.id) {
                _activeAlert.value = null
            }
            return
        }

        val now = System.currentTimeMillis()
        val lastFired = lastAlertTimestampBySensor[sensor.id] ?: 0L
        val escalated = level == AlertLevel.CRITICAL && previousLevel != AlertLevel.CRITICAL
        val newlyTriggered = previousLevel == AlertLevel.NORMAL
        val cooldownExpired = now - lastFired >= REPEAT_COOLDOWN_MS

        if (!newlyTriggered && !escalated && !cooldownExpired) {
            return
        }

        lastAlertTimestampBySensor[sensor.id] = now

        val isEnglish = settings.isEnglish
        val formatted = sensor.formatValue(
            rawValue = reading.value,
            useFahrenheit = settings.useFahrenheit,
            pressureUnit = settings.pressureUnit,
            isEnglish = isEnglish
        )
        val unit = sensor.displayUnit(
            useFahrenheit = settings.useFahrenheit,
            pressureUnit = settings.pressureUnit,
            isEnglish = isEnglish
        )
        val sensorDisplayName = sensor.localizedDisplayName(isEnglish)
        val sensorShortName = sensor.localizedShortName(isEnglish)
        val prefix = if (isEnglish) {
            if (level == AlertLevel.CRITICAL) "Critical" else "Warning"
        } else {
            if (level == AlertLevel.CRITICAL) "Критично" else "Внимание"
        }
        val unitSpoken = spokenUnit(unit, isEnglish)
        val displayMsg = "$prefix: $sensorDisplayName — $formatted $unit".trim()
        val spokenMsg = "$prefix: $sensorShortName $formatted $unitSpoken".trim()

        val event = ObdAlertEvent(
            id = alertSeq.incrementAndGet(),
            sensorId = sensor.id,
            sensorName = sensorDisplayName,
            level = level,
            formattedValue = formatted,
            unit = unit,
            message = displayMsg,
            timestampMs = now
        )

        _activeAlert.value = event
        _alertEvents.tryEmit(event)
        sessionLogger.warn("ALERT", displayMsg)

        if (settings.voiceAlertsEnabled) {
            speak(spokenMsg, settings)
        }
    }

    fun triggerTestAlert(
        settings: ObdUserSettings,
        pressureUnit: PressureUnit = settings.pressureUnit
    ) {
        val isEnglish = settings.isEnglish
        val msg = if (isEnglish) {
            "Warning: Test Alert — Transmission Temp 108 °C"
        } else {
            "Внимание: Тестовое оповещение — Темп. АКПП 108 °C"
        }
        val event = ObdAlertEvent(
            id = alertSeq.incrementAndGet(),
            sensorId = "TEST_ALERT",
            sensorName = if (isEnglish) "Alert Test" else "Тест оповещений",
            level = AlertLevel.WARNING,
            formattedValue = "108",
            unit = if (settings.useFahrenheit) "°F" else "°C",
            message = msg
        )
        _activeAlert.value = event
        _alertEvents.tryEmit(event)
        sessionLogger.info("ALERT", "Triggered test voice & banner alert (isEnglish=$isEnglish)")
        if (settings.voiceAlertsEnabled) {
            val spoken = if (isEnglish) {
                "Warning: OBD2 Auto voice alert test"
            } else {
                "Внимание: проверка голосовых оповещений OBD2 Авто"
            }
            speak(spoken, settings)
        }
    }

    fun speakText(text: String, settings: ObdUserSettings) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        speak(cleaned, settings)
    }

    private fun speak(text: String, settings: ObdUserSettings) {
        if (ttsReady) {
            tts?.let { speakWithEngine(it, text, settings) }
            return
        }
        pendingSpeech = text to settings
        initTtsIfNeeded()
    }

    @Synchronized
    private fun initTtsIfNeeded() {
        if (ttsInitAttempted) return
        ttsInitAttempted = true
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        applyDefaultLocale(engine, isEnglish = false)
                        ttsReady = true
                        refreshAvailableVoices(engine)
                        pendingSpeech?.let { (queuedText, queuedSettings) ->
                            pendingSpeech = null
                            speakWithEngine(engine, queuedText, queuedSettings)
                        }
                    }
                } else {
                    ttsInitAttempted = false
                    sessionLogger.warn("TTS", "TTS engine unavailable (status=$status)")
                }
            }
        }.onFailure { err ->
            ttsInitAttempted = false
            sessionLogger.warn("TTS", "TTS init failed: ${err.message}")
        }
    }

    private fun applyDefaultLocale(engine: TextToSpeech, isEnglish: Boolean) {
        val targetLocale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
        val res = engine.setLanguage(targetLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
    }

    private fun speakWithEngine(engine: TextToSpeech, text: String, settings: ObdUserSettings) {
        val isEnglish = settings.isEnglish
        runCatching {
            val selectedName = settings.ttsVoiceName
            if (!selectedName.isNullOrBlank()) {
                val matched = engine.voices?.find { it.name == selectedName }
                if (matched != null) {
                    engine.voice = matched
                } else {
                    applyDefaultLocale(engine, isEnglish)
                }
            } else {
                applyDefaultLocale(engine, isEnglish)
            }
            engine.setSpeechRate(settings.ttsSpeechRate.coerceIn(0.6f, 1.6f))
            val params = Bundle().apply {
                putFloat(
                    TextToSpeech.Engine.KEY_PARAM_VOLUME,
                    settings.ttsVolume.coerceIn(0.1f, 1.0f)
                )
            }
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, "obd_alert_${System.nanoTime()}")
        }.onFailure { err ->
            sessionLogger.warn("TTS", "TTS speak error: ${err.message}")
        }
    }

    private fun refreshAvailableVoices(engine: TextToSpeech?) {
        if (engine == null) return
        val voiceList = runCatching {
            val raw = engine.voices ?: emptySet()
            val filtered = raw.filter { voice ->
                val lang = voice.locale?.language?.lowercase(Locale.ROOT)
                lang == "ru" || lang == "en"
            }.sortedWith(
                compareBy<android.speech.tts.Voice>(
                    { if (it.locale?.language?.lowercase(Locale.ROOT) == "ru") 0 else 1 },
                    { it.isNetworkConnectionRequired },
                    { it.locale?.toLanguageTag().orEmpty() },
                    { it.name }
                )
            )
            var ruCounter = 0
            var enCounter = 0
            filtered.map { voice ->
                val lang = voice.locale?.language?.lowercase(Locale.ROOT)
                val tag = voice.locale?.toLanguageTag().orEmpty()
                val isNet = voice.isNetworkConnectionRequired
                val netLabel = if (isNet) "online" else "offline"
                val label = if (lang == "ru") {
                    ruCounter += 1
                    "Русский #$ruCounter ($netLabel • $tag)"
                } else {
                    enCounter += 1
                    "English #$enCounter ($netLabel • $tag)"
                }
                TtsVoiceOption(
                    name = voice.name,
                    label = label,
                    localeTag = tag,
                    isNetworkRequired = isNet
                )
            }
        }.getOrDefault(emptyList())
        _availableVoices.value = voiceList
    }

    private fun spokenUnit(unit: String, isEnglish: Boolean): String {
        return if (isEnglish) {
            when (unit) {
                "°C" -> "degrees Celsius"
                "°F" -> "degrees Fahrenheit"
                "V", "В" -> "volts"
                "%" -> "percent"
                "kPa", "кПа" -> "kilopascals"
                "bar", "бар" -> "bar"
                "km/h", "км/ч" -> "kilometers per hour"
                "RPM", "об/мин" -> "RPM"
                "g/s", "г/с" -> "grams per second"
                else -> unit
            }
        } else {
            when (unit) {
                "°C" -> "градусов"
                "°F" -> "градусов Фаренгейта"
                "В", "V" -> "вольт"
                "%" -> "процентов"
                "кПа", "kPa" -> "килопаскаль"
                "бар", "bar" -> "бар"
                "км/ч", "km/h" -> "километров в час"
                "об/мин", "RPM" -> "оборотов в минуту"
                "г/с", "g/s" -> "грамм в секунду"
                else -> unit
            }
        }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
        ttsInitAttempted = false
    }

    companion object {
        private const val REPEAT_COOLDOWN_MS = 45_000L
    }
}
