package com.example.obd.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.obd.model.AiVehicleSuggestion
import com.example.obd.model.DtcState
import com.example.obd.model.FreezeFrameData
import com.example.obd.model.FuelType
import com.example.obd.model.ImReadinessResult
import com.example.obd.model.ObdPid
import com.example.obd.model.ParsedFuelReceipt
import com.example.obd.model.ParsedServiceInvoice
import com.example.obd.model.PhotoAnalysisMode
import com.example.obd.model.PhotoAnalysisState
import com.example.obd.model.PrePurchaseInspectionResult
import com.example.obd.model.SensorReading
import com.example.obd.model.SuggestedMaintenanceSpec
import com.example.obd.model.VehicleInfoState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data object Loading : AiAnalysisState
    data class Streaming(val text: String) : AiAnalysisState
    data class Success(val text: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}

class AiDiagnosticAssistant {

    companion object {
        private const val TAG = "AiDiagnosticAssistant"
        private const val MODEL_NAME = "gemini-3.8-flash"
    }

    private val model by lazy {
        Firebase.ai.generativeModel(MODEL_NAME)
    }

    private val fuelReceiptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "liters" to Schema.double("Fuel volume in liters"),
                        "totalCost" to Schema.double("Total receipt amount"),
                        "pricePerLiter" to Schema.double("Fuel price per liter"),
                        "note" to Schema.string("Fuel grade or station name")
                    ),
                    optionalProperties = listOf("note")
                )
            }
        )
    }

    private val serviceInvoiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string("Short service task title, e.g. Engine Oil 5W-40"),
                        "category" to Schema.enumeration(listOf("OIL", "FILTERS", "BRAKES", "IGNITION", "CUSTOM")),
                        "odometerKm" to Schema.integer("Vehicle odometer in km if shown on invoice", nullable = true),
                        "notes" to Schema.string("Key parts, oil viscosity, or work summary")
                    ),
                    optionalProperties = listOf("odometerKm", "notes")
                )
            }
        )
    }

    private val vehicleSuggestionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    properties = mapOf(
                        "displayName" to Schema.string("Normalized make, model and engine label"),
                        "displacementLiters" to Schema.double("Engine displacement in liters"),
                        "fuelType" to Schema.enumeration(listOf("PETROL", "DIESEL", "HYBRID", "LPG")),
                        "redlineRpm" to Schema.integer("Typical engine redline RPM"),
                        "shiftLightRpm" to Schema.integer("Recommended shift light RPM"),
                        "curbWeightKg" to Schema.integer("Typical vehicle curb weight in kilograms"),
                        "recommendedOemPackId" to Schema.string("Matching OEM PID pack id: vag, toyota_hybrid, hyundai_kia, bmw, ford_mazda, diesel_dpf, or empty", nullable = true),
                        "maintenanceItems" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "title" to Schema.string("Maintenance task name"),
                                    "category" to Schema.enumeration(listOf("OIL", "FILTERS", "BRAKES", "IGNITION", "CUSTOM")),
                                    "intervalKm" to Schema.integer("Interval in kilometers"),
                                    "intervalHours" to Schema.integer("Optional interval in engine hours")
                                ),
                                optionalProperties = listOf("intervalHours")
                            ),
                            description = "3 to 5 essential scheduled maintenance items for this specific vehicle"
                        )
                    ),
                    optionalProperties = listOf("recommendedOemPackId")
                )
            }
        )
    }

    fun analyzeDiagnosticsStream(
        dtcState: DtcState,
        freezeFrame: FreezeFrameData?,
        imReadiness: ImReadinessResult?,
        vehicleInfo: VehicleInfoState,
        vehicleLabel: String,
        fuelTypeLabel: String,
        readings: Map<String, SensorReading>,
        isEnglish: Boolean
    ): Flow<AiAnalysisState> = flow {
        emit(AiAnalysisState.Loading)
        val prompt = buildDiagnosticPrompt(
            dtcState = dtcState,
            freezeFrame = freezeFrame,
            imReadiness = imReadiness,
            vehicleInfo = vehicleInfo,
            vehicleLabel = vehicleLabel,
            fuelTypeLabel = fuelTypeLabel,
            readings = readings,
            isEnglish = isEnglish
        )

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiAnalysisState.Streaming(accumulated.toString()))
                    }
                }
            }
            val result = accumulated.toString().trim()
            if (result.isNotBlank()) {
                emit(AiAnalysisState.Success(result))
            } else {
                emit(
                    AiAnalysisState.Error(
                        if (isEnglish) "Gemini returned an empty diagnostic analysis."
                        else "Модель вернула пустой диагностический ответ."
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI diagnostic analysis timed out", e)
            emit(
                AiAnalysisState.Error(
                    if (isEnglish) "Diagnostic analysis timed out after 60 seconds. Please retry."
                    else "Превышено время ожидания анализа (60 с). Повторите попытку."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic diagnostic analysis failed", e)
            emit(
                AiAnalysisState.Error(
                    e.localizedMessage ?: if (isEnglish) "AI diagnostic analysis failed."
                    else "Ошибка AI-анализа неисправностей."
                )
            )
        }
    }

    private fun buildDiagnosticPrompt(
        dtcState: DtcState,
        freezeFrame: FreezeFrameData?,
        imReadiness: ImReadinessResult?,
        vehicleInfo: VehicleInfoState,
        vehicleLabel: String,
        fuelTypeLabel: String,
        readings: Map<String, SensorReading>,
        isEnglish: Boolean
    ): String {
        val dtcList = if (dtcState.codes.isEmpty()) {
            if (isEnglish) "None stored in Mode 03" else "Ошибок Mode 03 не обнаружено"
        } else {
            dtcState.codes.joinToString("\n") { "- ${it.code} (${it.system}): ${it.description}" }
        }

        val ffText = freezeFrame?.let { ff ->
            buildString {
                appendLine("Trigger DTC: ${ff.dtcCode ?: "Unknown"}")
                ff.rpm?.let { appendLine("RPM: ${it.toInt()}") }
                ff.speedKmh?.let { appendLine("Speed: ${it.toInt()} km/h") }
                ff.coolantTempC?.let { appendLine("Coolant: ${it.toInt()} °C") }
                ff.engineLoadPct?.let { appendLine("Engine Load: ${it.toInt()} %") }
                ff.stftPct?.let { appendLine("STFT: ${String.format(Locale.US, "%.1f", it)} %") }
                ff.ltftPct?.let { appendLine("LTFT: ${String.format(Locale.US, "%.1f", it)} %") }
                ff.intakeMapKpa?.let { appendLine("MAP: ${it.toInt()} kPa") }
            }.trim()
        } ?: "Not captured"

        val liveSnap = buildString {
            readings[ObdPid.ENGINE_RPM.modeAndPid]?.let { append("RPM=${it.value.toInt()}, ") }
            readings[ObdPid.COOLANT_TEMP.modeAndPid]?.let { append("Coolant=${it.value.toInt()}°C, ") }
            readings[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.let { append("Voltage=${String.format(Locale.US, "%.1f", it.value)}V, ") }
            readings[ObdPid.SHORT_FUEL_TRIM.modeAndPid]?.let { append("STFT=${String.format(Locale.US, "%.1f", it.value)}%, ") }
            readings[ObdPid.LONG_FUEL_TRIM.modeAndPid]?.let { append("LTFT=${String.format(Locale.US, "%.1f", it.value)}%, ") }
            readings[ObdPid.INTAKE_MAP.modeAndPid]?.let { append("MAP=${it.value.toInt()}kPa, ") }
            readings[ObdPid.MAF_FLOW.modeAndPid]?.let { append("MAF=${String.format(Locale.US, "%.1f", it.value)}g/s") }
        }.ifBlank { "No live telemetry stream" }

        val readinessSummary = imReadiness?.let { im ->
            val readyCount = im.monitors.count { !it.supported || it.completed }
            val incomplete = im.monitors.filter { it.supported && !it.completed }.joinToString { it.nameEn }
            "Supported monitors ready: $readyCount/${im.monitors.size}; Incomplete: ${incomplete.ifBlank { "none" }}"
        } ?: "Not read"

        val langInstruction = if (isEnglish) "Respond concisely in clear English." else "Отвечай кратко, структурировано и понятно на русском языке."
        return """
            You are an experienced automotive OBD2 diagnostic technician.
            $langInstruction

            Vehicle: $vehicleLabel ($fuelTypeLabel), VIN=${vehicleInfo.vin ?: "Unknown"}, CalID=${vehicleInfo.calibrationId ?: "Unknown"}
            MIL Status: ${if (dtcState.milOn) "ON" else "OFF"} (ECU reported count: ${dtcState.ecuReportedCount})
            Active DTCs:
            $dtcList

            Freeze Frame (Mode 02):
            $ffText

            I/M Readiness:
            $readinessSummary

            Live ECU Snapshot:
            $liveSnap

            Provide:
            1. Most likely root causes ranked by probability (correlating DTC + Freeze Frame + STFT/LTFT + Voltage/Coolant if present).
            2. Concrete physical inspection steps in priority order (what to check with hands/multimeter/smoke test).
            3. Driving safety verdict (can drive calmly vs stop/service urgently).
        """.trimIndent()
    }

    suspend fun askDriverQuestion(
        question: String,
        dtcState: DtcState,
        freezeFrame: FreezeFrameData?,
        vehicleLabel: String,
        fuelTypeLabel: String,
        readings: Map<String, SensorReading>,
        maintenanceSummary: String,
        isEnglish: Boolean
    ): Result<String> {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            return Result.failure(
                IllegalArgumentException(if (isEnglish) "Question is empty" else "Вопрос не задан")
            )
        }
        val activeDtcs = dtcState.codes.joinToString(", ") { "${it.code} (${it.description})" }
            .ifBlank { if (isEnglish) "none" else "нет" }
        val pendingDtcs = dtcState.pendingCodes.joinToString(", ") { it.code }
            .ifBlank { if (isEnglish) "none" else "нет" }

        val telemetrySnap = buildString {
            readings[ObdPid.ENGINE_RPM.modeAndPid]?.let { append("RPM=${it.value.toInt()}, ") }
            readings[ObdPid.VEHICLE_SPEED.modeAndPid]?.let { append("Speed=${it.value.toInt()}km/h, ") }
            readings[ObdPid.COOLANT_TEMP.modeAndPid]?.let { append("Coolant=${it.value.toInt()}C, ") }
            readings[ObdPid.OIL_TEMP.modeAndPid]?.let { append("Oil=${it.value.toInt()}C, ") }
            readings[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.let { append("Voltage=${String.format(Locale.US, "%.1f", it.value)}V, ") }
            readings[ObdPid.SHORT_FUEL_TRIM.modeAndPid]?.let { append("STFT=${String.format(Locale.US, "%.1f", it.value)}%, ") }
            readings[ObdPid.LONG_FUEL_TRIM.modeAndPid]?.let { append("LTFT=${String.format(Locale.US, "%.1f", it.value)}%, ") }
            readings[ObdPid.INTAKE_MAP.modeAndPid]?.let { append("MAP=${it.value.toInt()}kPa") }
        }.ifBlank { "Offline / no live stream" }

        val ffLine = freezeFrame?.let {
            "Trigger=${it.dtcCode ?: "?"}, RPM=${it.rpm?.toInt()}, Coolant=${it.coolantTempC?.toInt()}C, STFT=${it.stftPct}, LTFT=${it.ltftPct}"
        } ?: "None"

        val langRule = if (isEnglish) {
            "Reply in clear English in 2-3 short spoken sentences without markdown bullets or asterisks."
        } else {
            "Ответь на русском языке в 2–3 коротких разговорных предложениях без markdown-символов, звёздочек и списков, чтобы текст удобно читался голосом в дороге."
        }

        val prompt = """
            You are an in-car AI automotive assistant answering a driver's spoken question.
            $langRule

            Driver's question: "$cleanQuestion"
            Vehicle: $vehicleLabel ($fuelTypeLabel)
            Active DTCs: $activeDtcs
            Pending DTCs: $pendingDtcs
            Freeze Frame: $ffLine
            Live Telemetry: $telemetrySnap
            Maintenance notes: ${maintenanceSummary.ifBlank { "None" }}
        """.trimIndent()

        return try {
            val responseText = withTimeout(30_000L) {
                model.generateContent(prompt).text.orEmpty().trim()
            }
            if (responseText.isNotBlank()) {
                Result.success(responseText)
            } else {
                Result.failure(
                    IllegalStateException(
                        if (isEnglish) "Empty response from assistant" else "Пустой ответ от голосового ассистента"
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice assistant request timed out", e)
            Result.failure(
                IllegalStateException(
                    if (isEnglish) "Assistant request timed out" else "Превышено время ожидания ассистента"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice assistant failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePrePurchaseVerdict(
        inspection: PrePurchaseInspectionResult,
        isEnglish: Boolean
    ): Result<String> {
        val langRule = if (isEnglish) {
            "Respond in clear, objective English in 4-5 concise plain-text sentences suitable for a PDF inspection report."
        } else {
            "Ответь объективно на русском языке в 4–5 кратких абзацах без markdown-звёздочек, чтобы текст красиво лёг в PDF-отчёт осмотра авто перед покупкой."
        }

        val prompt = """
            You are an independent automotive pre-purchase OBD2 inspector.
            $langRule

            Vehicle: ${inspection.vehicleName}, VIN=${inspection.vin ?: "Unavailable"}, CalID=${inspection.calId ?: "Unavailable"}
            MIL Status: ${if (inspection.milOn) "ON" else "OFF"}
            Active DTCs: ${inspection.activeDtcCount}, Pending DTCs: ${inspection.pendingDtcCount}, Failed Mode06 tests: ${inspection.mode06FailedCount}
            Distance since codes cleared (0131): ${inspection.distanceSinceCodesClearedKm ?: "N/A"} km
            Warmups since codes cleared (0130): ${inspection.warmupsSinceCodesCleared ?: "N/A"}
            Time since codes cleared (014E): ${inspection.timeSinceCodesClearedMin ?: "N/A"} min
            Distance with MIL on (0121): ${inspection.distanceWithMilOnKm ?: 0} km
            I/M Readiness: ${inspection.imReadyCount}/${inspection.imSupportedCount} (all ready=${inspection.imAllReady})
            Reset Suspicion Level: ${inspection.resetSuspicion.name}
            Coolant Temp: ${inspection.coolantTempC?.toInt() ?: "N/A"} C, Battery Voltage: ${inspection.batteryVoltage ?: "N/A"} V

            Give a clear buyer recommendation: red flags, whether the seller likely cleared fault codes right before inspection, and next physical checks before purchase.
        """.trimIndent()

        return try {
            val text = withTimeout(45_000L) {
                model.generateContent(prompt).text.orEmpty().trim()
            }
            if (text.isNotBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Empty inspection verdict"))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Pre-purchase AI verdict timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Pre-purchase AI verdict failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateCarStatusSummary(
        vehicleLabel: String,
        dtcState: DtcState,
        readings: Map<String, SensorReading>,
        maintenanceSummary: String,
        isEnglish: Boolean
    ): Result<String> {
        val question = if (isEnglish) {
            "Give me a quick 2-sentence spoken status report of my car right now while driving."
        } else {
            "Сделай короткую голосовую сводку состояния автомобиля прямо сейчас в дороге за 2 предложения."
        }
        return askDriverQuestion(
            question = question,
            dtcState = dtcState,
            freezeFrame = null,
            vehicleLabel = vehicleLabel,
            fuelTypeLabel = "",
            readings = readings,
            maintenanceSummary = maintenanceSummary,
            isEnglish = isEnglish
        )
    }

    suspend fun analyzeAutoPhoto(
        bitmap: Bitmap,
        mode: PhotoAnalysisMode,
        vehicleLabel: String,
        readings: Map<String, SensorReading>,
        isEnglish: Boolean
    ): Result<PhotoAnalysisState> {
        return try {
            when (mode) {
                PhotoAnalysisMode.DASH_WARNING_LIGHT -> {
                    val prompt = if (isEnglish) {
                        "Identify every dashboard warning symbol/icon or gauge warning shown in this photo for vehicle '$vehicleLabel'. Explain what each symbol means, severity (safe to drive vs pull over immediately), and concrete next check."
                    } else {
                        "Определи по фото индикатор или предупреждение на приборной панели автомобиля '$vehicleLabel'. Объясни простым языком: что означает значок, можно ли продолжать движение и что проверить в первую очередь."
                    }
                    val text = withTimeout(60_000L) {
                        model.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        ).text.orEmpty().trim()
                    }
                    if (text.isNotBlank()) {
                        Result.success(PhotoAnalysisState.DashLightResult(text))
                    } else {
                        Result.failure(IllegalStateException("Empty photo analysis response"))
                    }
                }
                PhotoAnalysisMode.FUEL_RECEIPT -> {
                    val prompt = if (isEnglish) {
                        "Extract fuel volume (liters), total amount paid, price per liter, and fuel grade/station note from this gas station receipt photo."
                    } else {
                        "Извлеки с фото чека АЗС: количество литров топлива (liters), итоговую сумму (totalCost), цену за литр (pricePerLiter) и краткую заметку (марка топлива / АЗС)."
                    }
                    val jsonStr = withTimeout(60_000L) {
                        fuelReceiptModel.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        ).text ?: throw IllegalStateException("Empty receipt JSON")
                    }
                    val obj = JSONObject(jsonStr)
                    val liters = obj.optDouble("liters", 0.0).toFloat().coerceIn(0f, 500f)
                    val total = obj.optDouble("totalCost", 0.0).toFloat().coerceAtLeast(0f)
                    val price = obj.optDouble("pricePerLiter", 0.0).toFloat().let { p ->
                        if (p > 0f) p else if (liters > 0.1f && total > 0f) total / liters else 0f
                    }
                    val note = obj.optString("note", "")
                    val summary = if (isEnglish) {
                        "${String.format(Locale.US, "%.2f", liters)} L • ${String.format(Locale.US, "%.2f", price)}/L • Total ${String.format(Locale.US, "%.0f", total)} $note".trim()
                    } else {
                        "${String.format(Locale.US, "%.2f", liters)} л • ${String.format(Locale.US, "%.2f", price)} ₽/л • Итого ${String.format(Locale.US, "%.0f", total)} ₽ $note".trim()
                    }
                    Result.success(
                        PhotoAnalysisState.FuelReceiptResult(
                            receipt = ParsedFuelReceipt(liters = liters, totalCost = total, pricePerLiter = price, note = note),
                            rawSummary = summary
                        )
                    )
                }
                PhotoAnalysisMode.SERVICE_INVOICE -> {
                    val prompt = if (isEnglish) {
                        "Extract the main automotive service/maintenance work from this service invoice photo into title, category (OIL/FILTERS/BRAKES/IGNITION/CUSTOM), odometerKm (integer if present), and notes."
                    } else {
                        "Извлеки основную работу из фото заказ-наряда СТО: краткое название (title), категорию (OIL/FILTERS/BRAKES/IGNITION/CUSTOM), пробег odometerKm (если указан) и заметки по запчастям/маслу (notes)."
                    }
                    val jsonStr = withTimeout(60_000L) {
                        serviceInvoiceModel.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        ).text ?: throw IllegalStateException("Empty invoice JSON")
                    }
                    val obj = JSONObject(jsonStr)
                    val title = obj.optString("title", if (isEnglish) "Service Work" else "Плановое ТО")
                    val category = obj.optString("category", "CUSTOM")
                    val odo = if (obj.has("odometerKm") && !obj.isNull("odometerKm")) {
                        obj.optInt("odometerKm").takeIf { it > 0 }
                    } else null
                    val notes = obj.optString("notes", "")
                    val summary = buildString {
                        append(title)
                        odo?.let { append(" • $it km") }
                        if (notes.isNotBlank()) append(" ($notes)")
                    }
                    Result.success(
                        PhotoAnalysisState.ServiceInvoiceResult(
                            invoice = ParsedServiceInvoice(title = title, category = category, odometerKm = odo, notes = notes),
                            rawSummary = summary
                        )
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo analysis timed out", e)
            Result.failure(
                IllegalStateException(
                    if (isEnglish) "Photo analysis timed out after 60s." else "Превышено время ожидания анализа фото (60 с)."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun suggestVehicleProfileAndMaintenance(
        vin: String?,
        query: String,
        isEnglish: Boolean
    ): Result<AiVehicleSuggestion> {
        val cleanVin = vin?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val cleanQuery = query.trim()
        if (cleanVin == null && cleanQuery.isBlank()) {
            return Result.failure(
                IllegalArgumentException(if (isEnglish) "Enter VIN or vehicle model" else "Укажите VIN или модель автомобиля")
            )
        }
        val langNote = if (isEnglish) "Write maintenance task titles in concise English." else "Названия работ ТО пиши кратко на русском языке."
        val prompt = """
            Identify technical vehicle parameters for an OBD2 telemetry profile.
            $langNote
            VIN: ${cleanVin ?: "Unknown"}
            Model / Name query: ${cleanQuery.ifBlank { "Infer from VIN" }}
            Choose recommendedOemPackId ONLY from: vag, toyota_hybrid, hyundai_kia, bmw, ford_mazda, diesel_dpf (or empty string if none matches).
        """.trimIndent()

        return try {
            val jsonStr = withTimeout(45_000L) {
                vehicleSuggestionModel.generateContent(prompt).text
                    ?: throw IllegalStateException("Empty vehicle suggestion JSON")
            }
            val obj = JSONObject(jsonStr)
            val fuelName = obj.optString("fuelType", "PETROL")
            val fuel = FuelType.entries.find { it.name == fuelName } ?: FuelType.PETROL
            val arr = obj.optJSONArray("maintenanceItems")
            val items = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val itemObj = arr.optJSONObject(i) ?: continue
                        val title = itemObj.optString("title").trim()
                        if (title.isNotEmpty()) {
                            add(
                                SuggestedMaintenanceSpec(
                                    title = title,
                                    category = itemObj.optString("category", "CUSTOM"),
                                    intervalKm = itemObj.optInt("intervalKm", 10000).coerceIn(1000, 200_000),
                                    intervalHours = itemObj.optInt("intervalHours", 0).coerceIn(0, 5000)
                                )
                            )
                        }
                    }
                }
            }
            val packId = obj.optString("recommendedOemPackId", "").trim().takeIf { it.isNotEmpty() }
            Result.success(
                AiVehicleSuggestion(
                    displayName = obj.optString("displayName", cleanQuery.ifBlank { "Auto" }),
                    displacementLiters = obj.optDouble("displacementLiters", 2.0).toFloat().coerceIn(0.6f, 8.0f),
                    fuelType = fuel,
                    redlineRpm = obj.optInt("redlineRpm", 6500).coerceIn(3000, 10000),
                    shiftLightRpm = obj.optInt("shiftLightRpm", 6000).coerceIn(2500, 9500),
                    curbWeightKg = obj.optInt("curbWeightKg", 1450).coerceIn(600, 6000),
                    recommendedOemPackId = packId,
                    maintenanceItems = items
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vehicle suggestion timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle suggestion failed", e)
            Result.failure(e)
        }
    }
}

