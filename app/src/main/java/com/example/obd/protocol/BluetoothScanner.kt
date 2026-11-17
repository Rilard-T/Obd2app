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

    fun allStartupPermissions(): Array<String> {
        val list = mutableListOf<String>()
        list.addAll(requiredPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.distinct().toTypedArray()
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
                val deviceName = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth OBD"
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
                        ?: "BLE Device"
                } catch (_: SecurityException) {
                    "BLE Device"
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

    @SuppressLint("MissingPermission")
    fun scanClassicDevices(): Flow<BluetoothDeviceItem> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled || !hasBluetoothPermissions()) {
            close()
            return@callbackFlow
        }

        val seenAddresses = mutableSetOf<String>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                when (intent.action) {
                    android.bluetooth.BluetoothDevice.ACTION_FOUND -> {
                        val device: android.bluetooth.BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    android.bluetooth.BluetoothDevice.EXTRA_DEVICE,
                                    android.bluetooth.BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                            }
                        val address = device?.address ?: return
                        if (!seenAddresses.add(address)) return
                        val name = try {
                            device.name?.takeIf { it.isNotBlank() } ?: "BT Device"
                        } catch (_: SecurityException) {
                            "BT Device"
                        }
                        val isPaired = try {
                            device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
                        } catch (_: SecurityException) {
                            false
                        }
                        val rssi = intent.getShortExtra(
                            android.bluetooth.BluetoothDevice.EXTRA_RSSI,
                            Short.MIN_VALUE
                        ).takeIf { it != Short.MIN_VALUE }?.toInt()
                        trySend(
                            BluetoothDeviceItem(
                                name = name,
                                address = address,
                                transport = BluetoothTransportType.CLASSIC_SPP,
                                isPaired = isPaired,
                                isLikelyObd = isLikelyObdName(name),
                                rssi = rssi
                            )
                        )
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        close()
                    }
                }
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            close()
        }

        awaitClose {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (_: Exception) {
            }
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    fun findPreferredPairedObdDevice(): BluetoothDeviceItem? {
        val paired = getPairedDevices()
        return paired.firstOrNull { it.isLikelyObd } ?: paired.firstOrNull()
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
