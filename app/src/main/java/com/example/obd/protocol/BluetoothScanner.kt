package com.example.obd.protocol

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.BluetoothTransportType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BluetoothScanner(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceItem> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasBluetoothPermissions()) return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                val deviceName = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth устройство"
                val transport = if (device.type == android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE) {
                    BluetoothTransportType.BLE
                } else {
                    BluetoothTransportType.CLASSIC_SPP
                }
                BluetoothDeviceItem(
                    name = deviceName,
                    address = device.address,
                    transport = transport,
                    isPaired = true,
                    isLikelyObd = isLikelyObdName(deviceName)
                )
            }.sortedByDescending { it.isLikelyObd }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun scanBleDevices(): Flow<BluetoothDeviceItem> = callbackFlow {
        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null || !hasBluetoothPermissions()) {
            close()
            return@callbackFlow
        }

        val seenAddresses = mutableSetOf<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                if (!seenAddresses.add(address)) return
                val name = try {
                    device.name?.takeIf { it.isNotBlank() }
                        ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                        ?: "BLE Устройство"
                } catch (_: SecurityException) {
                    "BLE Устройство"
                }
                trySend(
                    BluetoothDeviceItem(
                        name = name,
                        address = address,
                        transport = BluetoothTransportType.BLE,
                        isPaired = false,
                        isLikelyObd = isLikelyObdName(name),
                        rssi = result.rssi
                    )
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            close()
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
        }
    }

    private fun isLikelyObdName(name: String): Boolean {
        val upper = name.uppercase()
        return OBD_KEYWORDS.any { upper.contains(it) }
    }

    companion object {
        private val OBD_KEYWORDS = listOf(
            "OBD", "ELM", "VGATE", "ICAR", "VEEPEAK", "CARISTA", "KONNWEI",
            "OBDLINK", "SCANNER", "DIAG", "BAFX", "NEXLINK", "TONWON", "ANCEL"
        )
    }
}
