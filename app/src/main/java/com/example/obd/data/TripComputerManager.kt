package com.example.obd.data

import com.example.obd.model.AccelDiscipline
import com.example.obd.model.AccelSamplePoint
import com.example.obd.model.AccelTimerPhase
import com.example.obd.model.AccelTimerState
import com.example.obd.model.FuelType
import com.example.obd.model.TripStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.abs

/**
 * Computes real-time trip telemetry (distance, consumption via MAF or Speed-Density MAP+RPM,
 * fuel cost, idle time, Virtual Dyno HP/Torque, Eco Score, peaks) and manages the
 * 0-60 / 0-100 / 60-100 / 0-402m performance timer with telemetry curve sampling.
 */
class TripComputerManager {

    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats.asStateFlow()

    private val _accelTimer = MutableStateFlow(AccelTimerState())
    val accelTimer: StateFlow<AccelTimerState> = _accelTimer.asStateFlow()

    private var tripStartMs: Long = 0L
    private var lastUpdateMs: Long = 0L
    private var lastSpeedKmh: Float = 0f
    private var lastSpeedSampleMs: Long = 0L
    private var smoothedHorsepower: Float = 0f
    private var smoothedTorqueNm: Float = 0f
    private var idleAccumMs: Long = 0L
    private var coldRevAccumMs: Long = 0L
    private var ecoPenalty: Float = 0f
    private var harshEventsCount: Int = 0
    private var lastHarshEventMs: Long = 0L
    private var lastOverRevPenaltyMs: Long = 0L
    private var runStartMs: Long = 0L
    private var runPrevSampleMs: Long = 0L
    private var runPrevSpeedKmh: Float = 0f
    private val tripCurvePoints = ArrayList<String>(128)
    private var lastCurveSampleMs: Long = 0L
    private var lastCurveSampleSpeedKmh: Float = -100f

    @Synchronized
    fun resetTrip() {
        tripStartMs = 0L
        lastUpdateMs = 0L
        lastSpeedKmh = 0f
        lastSpeedSampleMs = 0L
        smoothedHorsepower = 0f
        smoothedTorqueNm = 0f
        idleAccumMs = 0L
        coldRevAccumMs = 0L
        ecoPenalty = 0f
        harshEventsCount = 0
        lastHarshEventMs = 0L
        lastOverRevPenaltyMs = 0L
        tripCurvePoints.clear()
        lastCurveSampleMs = 0L
        lastCurveSampleSpeedKmh = -100f
        _tripStats.value = TripStats()
    }

    @Synchronized
    fun getTripCurveCsv(): String = tripCurvePoints.joinToString("|")

    @Synchronized
    fun onTelemetrySample(
        timestampMs: Long = System.currentTimeMillis(),
        speedKmh: Float?,
        rpm: Float?,
        coolantTempC: Float?,
        mafGramsPerSec: Float?,
        intakeMapKpa: Float?,
        intakeAirTempC: Float?,
        displacementLiters: Float,
        fuelType: FuelType,
        fuelPricePerLiter: Float,
        speedCalibrationFactor: Float = 1.0f,
        fuelCalibrationFactor: Float = 1.0f,
        curbWeightKg: Int = 1450,
        shiftLightRpm: Int = 6000,
        onAccelRunCompleted: ((AccelDiscipline, Float, Float, Float) -> Unit)? = null
    ) {
        if (tripStartMs == 0L) {
            tripStartMs = timestampMs
            lastUpdateMs = timestampMs
        }
        val rawDtMs = (timestampMs - lastUpdateMs).coerceAtLeast(0L)
        // Guard against huge jumps after sleep/pause (> 8 sec treated as 0 integration step)
        val dtMs = if (rawDtMs > 8_000L) 0L else rawDtMs
        val dtHours = dtMs / 3_600_000.0
        lastUpdateMs = timestampMs

        val safeSpeedFactor = speedCalibrationFactor.coerceIn(0.85f, 1.15f)
        val safeFuelFactor = fuelCalibrationFactor.coerceIn(0.70f, 1.35f)
        val calibratedSpeedSample = speedKmh?.let { (it * safeSpeedFactor).coerceAtLeast(0f) }

        val prevSpeedForDelta = lastSpeedKmh
        val currentSpeed = (calibratedSpeedSample ?: lastSpeedKmh).coerceAtLeast(0f)
        val avgStepSpeed = ((prevSpeedForDelta + currentSpeed) * 0.5f).coerceAtLeast(0f)
        val deltaDistKm = (avgStepSpeed * dtHours).toFloat()

        // Virtual Dyno & longitudinal acceleration estimation
        var instantHp: Float? = null
        var instantTorque: Float? = null
        var accelMs2 = 0f

        if (calibratedSpeedSample != null) {
            val speedDtSec = if (lastSpeedSampleMs in 1L until timestampMs) {
                (timestampMs - lastSpeedSampleMs) / 1000f
            } else 0f
            lastSpeedSampleMs = timestampMs
            lastSpeedKmh = currentSpeed

            if (speedDtSec in 0.05f..3.5f) {
                val vPrevMs = prevSpeedForDelta / 3.6f
                val vCurrMs = currentSpeed / 3.6f
                val vMidMs = (vPrevMs + vCurrMs) * 0.5f
                accelMs2 = (vCurrMs - vPrevMs) / speedDtSec

                if (vMidMs >= 2.0f && accelMs2 > 0.05f) {
                    val massKg = curbWeightKg.coerceIn(600, 6000).toFloat()
                    val forceInertia = massKg * accelMs2
                    // Road load: Cd*A ≈ 0.68 m^2, air density ≈ 1.225 kg/m^3, Crr ≈ 0.013
                    val forceDrag = 0.5f * 1.225f * 0.68f * vMidMs * vMidMs
                    val forceRoll = massKg * 9.81f * 0.013f
                    val totalWatts = (forceInertia + forceDrag + forceRoll) * vMidMs
                    // Metric HP (735.5 W per PS) with ~1.10 drivetrain loss estimate
                    val rawHp = ((totalWatts * 1.10f) / 735.5f).coerceIn(0f, 1500f)
                    smoothedHorsepower = if (smoothedHorsepower <= 0.5f) {
                        rawHp
                    } else {
                        smoothedHorsepower * 0.55f + rawHp * 0.45f
                    }
                    instantHp = smoothedHorsepower

                    val currentRpm = rpm ?: 0f
                    if (currentRpm >= 700f) {
                        val rawTorque = ((smoothedHorsepower * 7127f) / currentRpm).coerceIn(0f, 2500f)
                        smoothedTorqueNm = if (smoothedTorqueNm <= 0.5f) {
                            rawTorque
                        } else {
                            smoothedTorqueNm * 0.55f + rawTorque * 0.45f
                        }
                        instantTorque = smoothedTorqueNm
                    }
                } else {
                    smoothedHorsepower *= 0.6f
                    smoothedTorqueNm *= 0.6f
                    if (vMidMs < 1.5f) {
                        smoothedHorsepower = 0f
                        smoothedTorqueNm = 0f
                    }
                }
            }
        }

        if (dtMs > 0L && currentSpeed < 1.5f && (rpm ?: 0f) > 350f) {
            idleAccumMs += dtMs
        }

        // Driving Style Care / Eco Score tracking
        if (dtMs > 0L) {
            val currentRpm = rpm ?: 0f
            if (coolantTempC != null && coolantTempC < 60f && currentRpm > 3200f) {
                coldRevAccumMs += dtMs
                ecoPenalty += (dtMs / 1000f) * 0.6f
            }
            if (abs(accelMs2) > 4.2f && (timestampMs - lastHarshEventMs) >= 2_500L) {
                lastHarshEventMs = timestampMs
                harshEventsCount++
                ecoPenalty += 3f
            }
            val safeShiftRpm = shiftLightRpm.coerceIn(2500, 9500)
            if (currentRpm > safeShiftRpm && (timestampMs - lastOverRevPenaltyMs) >= 3_000L) {
                lastOverRevPenaltyMs = timestampMs
                ecoPenalty += 2f
            }
        }

        val effectiveAirGramsPerSec = estimateAirFlowGramsPerSec(
            mafGramsPerSec = mafGramsPerSec,
            rpm = rpm,
            intakeMapKpa = intakeMapKpa,
            intakeAirTempC = intakeAirTempC,
            displacementLiters = displacementLiters
        )

        val instantLitersPerHour = effectiveAirGramsPerSec?.let { airGs ->
            ((airGs * 3600f) / fuelType.airGramsPerLiter.coerceAtLeast(5000f)) * safeFuelFactor
        }?.coerceIn(0f, 95f)

        val instantLitersPer100Km = if (instantLitersPerHour != null && currentSpeed >= 4f) {
            ((instantLitersPerHour / currentSpeed) * 100f).coerceIn(0f, 60f)
        } else {
            null
        }

        val deltaFuelLiters = if (instantLitersPerHour != null && dtHours > 0.0) {
            (instantLitersPerHour * dtHours).toFloat()
        } else {
            0f
        }

        val elapsedSec = ((timestampMs - tripStartMs) / 1000L).coerceAtLeast(0L)
        val currentEcoScore = (100 - ecoPenalty.toInt()).coerceIn(0, 100)

        _tripStats.update { prev ->
            val totalDist = prev.distanceKm + deltaDistKm
            val totalFuel = prev.fuelUsedLiters + deltaFuelLiters
            val avgL100 = if (totalDist >= 0.05f && totalFuel > 0f) {
                ((totalFuel / totalDist) * 100f).coerceIn(0f, 60f)
            } else {
                prev.avgLPer100Km
            }
            val movingElapsedHours = elapsedSec.toFloat() / 3600f
            val avgSpeed = if (movingElapsedHours > 0.001f) totalDist / movingElapsedHours else 0f
            val peakCoolant = when {
                coolantTempC == null -> prev.maxCoolantTempC
                prev.maxCoolantTempC == null -> coolantTempC
                else -> maxOf(prev.maxCoolantTempC, coolantTempC)
            }
            val nextPeakHp = maxOf(prev.peakHorsepower, instantHp ?: 0f)
            val nextPeakTorque = maxOf(prev.peakTorqueNm, instantTorque ?: 0f)

            prev.copy(
                distanceKm = totalDist,
                elapsedSec = elapsedSec,
                instantLPer100Km = instantLitersPer100Km,
                instantLPerHour = instantLitersPerHour,
                avgLPer100Km = avgL100,
                fuelUsedLiters = totalFuel,
                estimatedCost = totalFuel * fuelPricePerLiter.coerceAtLeast(0f),
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = maxOf(prev.maxSpeedKmh, currentSpeed),
                maxRpm = maxOf(prev.maxRpm, rpm ?: 0f),
                maxCoolantTempC = peakCoolant,
                idleTimeSec = idleAccumMs / 1000L,
                instantHorsepower = instantHp,
                instantTorqueNm = instantTorque,
                peakHorsepower = nextPeakHp,
                peakTorqueNm = nextPeakTorque,
                ecoScore = currentEcoScore,
                harshEventsCount = harshEventsCount,
                coldRevSeconds = coldRevAccumMs / 1000L
            )
        }

        if (calibratedSpeedSample != null) {
            updateAccelTimerOnSpeedSample(
                timestampMs = timestampMs,
                speedKmh = calibratedSpeedSample,
                rpm = rpm,
                horsepower = instantHp ?: 0f,
                torqueNm = instantTorque ?: 0f,
                onAccelRunCompleted = onAccelRunCompleted
            )
        }

        val shouldSampleCurve = tripCurvePoints.size < 120 && (
            tripCurvePoints.isEmpty() ||
                (timestampMs - lastCurveSampleMs) >= 12_000L ||
                (abs(currentSpeed - lastCurveSampleSpeedKmh) >= 15f && (timestampMs - lastCurveSampleMs) >= 2_500L)
            )
        if (shouldSampleCurve) {
            lastCurveSampleMs = timestampMs
            lastCurveSampleSpeedKmh = currentSpeed
            tripCurvePoints.add(
                String.format(
                    Locale.US,
                    "%d;%.1f;%.0f;%.0f;%.1f",
                    elapsedSec,
                    currentSpeed,
                    rpm ?: 0f,
                    coolantTempC ?: 0f,
                    instantLitersPerHour ?: 0f
                )
            )
        }
    }

    /**
     * Uses direct MAF reading when present; otherwise computes Speed-Density estimate:
     * air_mass_g/s ≈ (MAP_kPa * V_L * RPM * VE) / (120 * R * T_K) * M_air
     * With M_air/R ≈ 3.484 g·K/(kPa·L) and typical VE = 0.82.
     */
    fun estimateAirFlowGramsPerSec(
        mafGramsPerSec: Float?,
        rpm: Float?,
        intakeMapKpa: Float?,
        intakeAirTempC: Float?,
        displacementLiters: Float
    ): Float? {
        if (mafGramsPerSec != null && mafGramsPerSec > 0.1f) {
            return mafGramsPerSec
        }
        val r = rpm ?: return null
        val map = intakeMapKpa ?: return null
        if (r < 250f || map < 10f) return null
        val tempKelvin = ((intakeAirTempC ?: 25f) + 273.15f).coerceIn(220f, 390f)
        val disp = displacementLiters.coerceIn(0.6f, 8.0f)
        val volumetricEfficiency = 0.82f
        val airFlow = (r / 120f) * disp * (map / tempKelvin) * 3.484f * volumetricEfficiency
        return airFlow.coerceIn(0.5f, 250f)
    }

    @Synchronized
    fun selectAccelDiscipline(discipline: AccelDiscipline) {
        _accelTimer.value = AccelTimerState(discipline = discipline)
    }

    @Synchronized
    fun armAccelTimer() {
        val current = _accelTimer.value
        _accelTimer.value = AccelTimerState(
            discipline = current.discipline,
            phase = AccelTimerPhase.ARMED,
            currentSpeedKmh = current.currentSpeedKmh,
            lastResultSec = current.lastResultSec
        )
    }

    @Synchronized
    fun resetAccelTimer() {
        val current = _accelTimer.value
        _accelTimer.value = AccelTimerState(
            discipline = current.discipline,
            phase = AccelTimerPhase.IDLE,
            currentSpeedKmh = current.currentSpeedKmh,
            lastResultSec = current.lastResultSec
        )
    }

    private fun updateAccelTimerOnSpeedSample(
        timestampMs: Long,
        speedKmh: Float,
        rpm: Float?,
        horsepower: Float,
        torqueNm: Float,
        onAccelRunCompleted: ((AccelDiscipline, Float, Float, Float) -> Unit)?
    ) {
        val state = _accelTimer.value
        val d = state.discipline
        when (state.phase) {
            AccelTimerPhase.IDLE, AccelTimerPhase.COMPLETED -> {
                _accelTimer.value = state.copy(currentSpeedKmh = speedKmh)
            }
            AccelTimerPhase.ARMED -> {
                val triggered = if (d.startSpeedKmh <= 1f) {
                    speedKmh >= 2.0f
                } else {
                    speedKmh >= d.startSpeedKmh
                }
                if (triggered) {
                    runStartMs = timestampMs
                    runPrevSampleMs = timestampMs
                    runPrevSpeedKmh = speedKmh
                    val initialSample = AccelSamplePoint(
                        elapsedSec = 0f,
                        speedKmh = speedKmh,
                        rpm = rpm ?: 0f,
                        horsepower = horsepower
                    )
                    _accelTimer.value = state.copy(
                        phase = AccelTimerPhase.RUNNING,
                        elapsedSec = 0f,
                        currentSpeedKmh = speedKmh,
                        coveredMeters = 0f,
                        peakSpeedKmh = speedKmh,
                        peakRpm = rpm ?: 0f,
                        peakHorsepower = horsepower,
                        peakTorqueNm = torqueNm,
                        samples = listOf(initialSample)
                    )
                } else {
                    _accelTimer.value = state.copy(currentSpeedKmh = speedKmh)
                }
            }
            AccelTimerPhase.RUNNING -> {
                // Cancel run if car brakes back to standstill for 0-start disciplines
                if (d.startSpeedKmh <= 1f && speedKmh < 0.8f && (timestampMs - runStartMs) > 1500L) {
                    _accelTimer.value = state.copy(
                        phase = AccelTimerPhase.ARMED,
                        elapsedSec = 0f,
                        currentSpeedKmh = speedKmh,
                        coveredMeters = 0f,
                        peakHorsepower = 0f,
                        peakTorqueNm = 0f,
                        samples = emptyList()
                    )
                    return
                }

                val stepDtSec = ((timestampMs - runPrevSampleMs).coerceIn(0L, 4000L)) / 1000f
                val stepAvgMs = (((runPrevSpeedKmh + speedKmh) * 0.5f) / 3.6f).coerceAtLeast(0f)
                val nextMeters = state.coveredMeters + stepAvgMs * stepDtSec
                val rawElapsedSec = ((timestampMs - runStartMs).coerceAtLeast(1L)) / 1000f
                val nextPeakSpeed = maxOf(state.peakSpeedKmh, speedKmh)
                val nextPeakRpm = maxOf(state.peakRpm, rpm ?: 0f)
                val nextPeakHp = maxOf(state.peakHorsepower, horsepower)
                val nextPeakTorque = maxOf(state.peakTorqueNm, torqueNm)

                val targetSpeed = d.targetSpeedKmh
                val targetDist = d.targetDistanceMeters

                val speedReached = targetSpeed != null && speedKmh >= targetSpeed
                val distReached = targetDist != null && nextMeters >= targetDist

                if (speedReached || distReached) {
                    // Linear interpolation correction to reduce ELM327 polling quantization error
                    var finalSec = rawElapsedSec
                    if (speedReached && targetSpeed != null && speedKmh > runPrevSpeedKmh && stepDtSec > 0f) {
                        val overshootRatio = ((speedKmh - targetSpeed) / (speedKmh - runPrevSpeedKmh)).coerceIn(0f, 1f)
                        finalSec = (rawElapsedSec - overshootRatio * stepDtSec).coerceAtLeast(0.1f)
                    } else if (distReached && targetDist != null && nextMeters > state.coveredMeters && stepDtSec > 0f) {
                        val stepMeters = nextMeters - state.coveredMeters
                        val overshootRatio = ((nextMeters - targetDist) / abs(stepMeters)).coerceIn(0f, 1f)
                        finalSec = (rawElapsedSec - overshootRatio * stepDtSec).coerceAtLeast(0.1f)
                    }

                    val finalSamples = (state.samples + AccelSamplePoint(
                        elapsedSec = finalSec,
                        speedKmh = speedKmh,
                        rpm = rpm ?: 0f,
                        horsepower = horsepower
                    )).takeLast(250)

                    _accelTimer.value = state.copy(
                        phase = AccelTimerPhase.COMPLETED,
                        elapsedSec = finalSec,
                        currentSpeedKmh = speedKmh,
                        coveredMeters = nextMeters,
                        peakSpeedKmh = nextPeakSpeed,
                        peakRpm = nextPeakRpm,
                        peakHorsepower = nextPeakHp,
                        peakTorqueNm = nextPeakTorque,
                        lastResultSec = finalSec,
                        samples = finalSamples
                    )
                    onAccelRunCompleted?.invoke(d, finalSec, nextPeakSpeed, nextPeakRpm)
                } else {
                    runPrevSampleMs = timestampMs
                    runPrevSpeedKmh = speedKmh
                    val nextSamples = (state.samples + AccelSamplePoint(
                        elapsedSec = rawElapsedSec,
                        speedKmh = speedKmh,
                        rpm = rpm ?: 0f,
                        horsepower = horsepower
                    )).takeLast(250)

                    _accelTimer.value = state.copy(
                        elapsedSec = rawElapsedSec,
                        currentSpeedKmh = speedKmh,
                        coveredMeters = nextMeters,
                        peakSpeedKmh = nextPeakSpeed,
                        peakRpm = nextPeakRpm,
                        peakHorsepower = nextPeakHp,
                        peakTorqueNm = nextPeakTorque,
                        samples = nextSamples
                    )
                }
            }
        }
    }
}
