package com.example.car

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.example.obd.data.ObdTelemetryRepository
import com.example.obd.model.ObdPid
import com.example.obd.model.TelemetrySensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class CockpitSurfaceController(
    private val repository: ObdTelemetryRepository
) : SurfaceCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val surfaceLock = Any()

    private var surfaceContainer: SurfaceContainer? = null
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null

    @Volatile
    private var renderActive: Boolean = false

    private var collectorJob: Job? = null
    private var animatorJob: Job? = null

    private val smoothedFractions = mutableMapOf<String, Float>()
    private val targetFractions = mutableMapOf<String, Float>()

    fun startObserving() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            combine(
                repository.readings,
                repository.sensors,
                repository.settings,
                repository.connectionState
            ) { readings, sensors, settings, _ ->
                Triple(readings, sensors, settings)
            }.collectLatest { (readings, sensors, settings) ->
                val dialSensors = resolveDialSensors(sensors, settings.fullscreenDialSensorIds)
                synchronized(surfaceLock) {
                    dialSensors.forEach { sensor ->
                        val spec = AnalogDialScale.buildSpec(
                            sensor = sensor,
                            reading = readings[sensor.id],
                            useFahrenheit = settings.useFahrenheit,
                            pressureUnit = settings.pressureUnit,
                            isEnglish = settings.isEnglish
                        )
                        targetFractions[sensor.id] = spec.needleFraction
                        smoothedFractions.putIfAbsent(sensor.id, spec.needleFraction)
                    }
                }
                if (renderActive) {
                    scheduleAnimationLoop()
                }
            }
        }
    }

    fun setRenderActive(active: Boolean) {
        renderActive = active
        if (active) {
            scheduleAnimationLoop()
        } else {
            animatorJob?.cancel()
            animatorJob = null
        }
    }

    fun release() {
        renderActive = false
        animatorJob?.cancel()
        collectorJob?.cancel()
        synchronized(surfaceLock) {
            surfaceContainer = null
            visibleArea = null
            stableArea = null
        }
        scope.cancel()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        repository.sessionLogger.info(
            "AA_SURF",
            "Android Auto Surface доступен (${surfaceContainer.width}x${surfaceContainer.height}, dpi=${surfaceContainer.dpi})"
        )
        synchronized(surfaceLock) {
            this.surfaceContainer = surfaceContainer
        }
        if (renderActive) {
            scheduleAnimationLoop()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        synchronized(surfaceLock) {
            this.visibleArea = Rect(visibleArea)
        }
        if (renderActive) {
            scheduleAnimationLoop()
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        synchronized(surfaceLock) {
            this.stableArea = Rect(stableArea)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        repository.sessionLogger.info("AA_SURF", "Android Auto Surface уничтожен")
        synchronized(surfaceLock) {
            if (this.surfaceContainer?.surface == surfaceContainer.surface) {
                this.surfaceContainer = null
            }
        }
        animatorJob?.cancel()
        animatorJob = null
    }

    private fun scheduleAnimationLoop() {
        if (animatorJob?.isActive == true) return
        animatorJob = scope.launch {
            while (isActive && renderActive) {
                val stillMoving = renderSingleFrame()
                if (!stillMoving) break
                delay(33L)
            }
        }
    }

    private fun renderSingleFrame(): Boolean {
        val sc: SurfaceContainer?
        val vis: Rect?
        val stab: Rect?
        val dialSensors: List<TelemetrySensor>
        val stripSensors: List<TelemetrySensor>
        val readings = repository.readings.value
        val sensors = repository.sensors.value
        val settings = repository.settings.value
        val connState = repository.connectionState.value

        dialSensors = resolveDialSensors(sensors, settings.fullscreenDialSensorIds)
        stripSensors = resolveStripSensors(sensors, settings.fullscreenStripSensorIds)

        var anyMoving = false
        val needleOverrides = HashMap<String, Float>(dialSensors.size)

        synchronized(surfaceLock) {
            sc = surfaceContainer
            vis = visibleArea
            stab = stableArea

            for (sensor in dialSensors) {
                val spec = AnalogDialScale.buildSpec(
                    sensor = sensor,
                    reading = readings[sensor.id],
                    useFahrenheit = settings.useFahrenheit,
                    pressureUnit = settings.pressureUnit,
                    isEnglish = settings.isEnglish
                )
                val target = targetFractions[sensor.id] ?: spec.needleFraction
                val current = smoothedFractions[sensor.id] ?: target
                val delta = target - current
                val next = if (abs(delta) < 0.002f) {
                    target
                } else {
                    anyMoving = true
                    current + delta * 0.30f
                }
                smoothedFractions[sensor.id] = next
                needleOverrides[sensor.id] = next
            }
        }

        val surface = sc?.surface ?: return false
        if (!surface.isValid) return false

        val rpmValue = readings[ObdPid.ENGINE_RPM.modeAndPid]?.value ?: 0f
        val shiftActive = settings.shiftLightEnabled && rpmValue >= settings.shiftLightRpm

        val frame = CockpitRenderFrame(
            dialSensors = dialSensors,
            stripSensors = stripSensors,
            readings = readings,
            dialNeedleOverrides = needleOverrides,
            connectionState = connState,
            useFahrenheit = settings.useFahrenheit,
            pressureUnit = settings.pressureUnit,
            isEnglish = settings.isEnglish,
            dialTheme = settings.dialTheme,
            shiftLightActive = shiftActive
        )

        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas != null) {
                val drawArea = computeSafeVisibleRect(
                    canvasWidth = canvas.width.toFloat(),
                    canvasHeight = canvas.height.toFloat(),
                    visible = vis,
                    stable = stab
                )
                AnalogCockpitRenderer.render(
                    canvas = canvas,
                    width = canvas.width.toFloat(),
                    height = canvas.height.toFloat(),
                    visibleArea = drawArea,
                    frame = frame
                )
            }
        } catch (t: Throwable) {
            repository.sessionLogger.warn("AA_SURF", "Сбой отрисовки кадра Surface: ${t.message}")
            return false
        } finally {
            if (canvas != null) {
                runCatching { surface.unlockCanvasAndPost(canvas) }
            }
        }
        return anyMoving
    }

    private fun computeSafeVisibleRect(
        canvasWidth: Float,
        canvasHeight: Float,
        visible: Rect?,
        stable: Rect?
    ): RectF {
        val base = visible ?: stable ?: return RectF(0f, 0f, canvasWidth, canvasHeight)
        val rect = RectF(base)
        if (rect.width() < 120f || rect.height() < 120f) {
            return RectF(0f, 0f, canvasWidth, canvasHeight)
        }
        return rect
    }

    private fun resolveDialSensors(
        allSensors: List<TelemetrySensor>,
        ids: List<String>
    ): List<TelemetrySensor> {
        val defaults = listOf(
            ObdPid.ENGINE_RPM.modeAndPid,
            ObdPid.VEHICLE_SPEED.modeAndPid,
            ObdPid.COOLANT_TEMP.modeAndPid
        )
        return (0 until 3).map { index ->
            val targetId = ids.getOrNull(index) ?: defaults[index]
            allSensors.find { it.id == targetId }
                ?: allSensors.find { it.id == defaults[index] }
                ?: ObdPid.entries[index].toTelemetrySensor()
        }
    }

    private fun resolveStripSensors(
        allSensors: List<TelemetrySensor>,
        ids: List<String>
    ): List<TelemetrySensor> {
        val defaults = listOf(
            ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid,
            ObdPid.ENGINE_LOAD.modeAndPid,
            ObdPid.OIL_TEMP.modeAndPid,
            ObdPid.INTAKE_MAP.modeAndPid
        )
        return (0 until 4).map { index ->
            val targetId = ids.getOrNull(index) ?: defaults[index]
            allSensors.find { it.id == targetId }
                ?: allSensors.find { it.id == defaults[index] }
                ?: ObdPid.entries[(index + 3) % ObdPid.entries.size].toTelemetrySensor()
        }
    }
}
