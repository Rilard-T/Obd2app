package com.example.obd.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.obd.model.GpsSpeedState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Optional on-demand GPS speed monitor for verifying OBD2 speedometer calibration.
 * Only started explicitly by user action after ACCESS_FINE_LOCATION permission is granted.
 */
class GpsSpeedMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val _state = MutableStateFlow(GpsSpeedState(hasPermission = hasLocationPermission()))
    val state: StateFlow<GpsSpeedState> = _state.asStateFlow()

    @Volatile
    private var latestObdRawSpeedKmh: Float? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val gpsKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).coerceAtLeast(0f) else null
            val accM = if (loc.hasAccuracy()) loc.accuracy else null
            val obdRaw = latestObdRawSpeedKmh
            val suggested = if (gpsKmh != null && gpsKmh >= 20f && obdRaw != null && obdRaw >= 20f) {
                (gpsKmh / obdRaw).coerceIn(0.85f, 1.15f)
            } else {
                _state.value.suggestedSpeedFactor
            }
            _state.update {
                it.copy(
                    gpsSpeedKmh = gpsKmh,
                    obdRawSpeedKmh = obdRaw,
                    suggestedSpeedFactor = suggested,
                    accuracyMeters = accM,
                    errorMessage = null
                )
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun updateObdRawSpeed(rawSpeedKmh: Float?) {
        latestObdRawSpeedKmh = rawSpeedKmh
        if (_state.value.isTracking) {
            _state.update { prev ->
                val gps = prev.gpsSpeedKmh
                val suggested = if (gps != null && gps >= 20f && rawSpeedKmh != null && rawSpeedKmh >= 20f) {
                    (gps / rawSpeedKmh).coerceIn(0.85f, 1.15f)
                } else {
                    prev.suggestedSpeedFactor
                }
                prev.copy(obdRawSpeedKmh = rawSpeedKmh, suggestedSpeedFactor = suggested)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(isEnglish: Boolean) {
        val permitted = hasLocationPermission()
        _state.update { it.copy(hasPermission = permitted) }
        if (!permitted) {
            _state.update {
                it.copy(
                    isTracking = false,
                    errorMessage = if (isEnglish) "Location permission required for GPS speed check"
                    else "Требуется разрешение геолокации для сверки GPS"
                )
            }
            return
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        runCatching {
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
            _state.update {
                it.copy(isTracking = true, hasPermission = true, errorMessage = null)
            }
        }.onFailure { err ->
            _state.update {
                it.copy(
                    isTracking = false,
                    errorMessage = err.message ?: if (isEnglish) "GPS unavailable" else "GPS недоступен"
                )
            }
        }
    }

    fun stopTracking() {
        runCatching {
            fusedClient.removeLocationUpdates(locationCallback)
        }
        _state.update { it.copy(isTracking = false) }
    }
}
