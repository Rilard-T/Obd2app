package com.example.obd.protocol

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.ObdApplication

/**
 * Listens for Bluetooth ACL connections (e.g. car head-unit / hands-free pairing) and triggers
 * hands-free auto-connect to the user's preferred OBD2 adapter when enabled in settings.
 */
class BluetoothAutoConnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val app = context.applicationContext as? ObdApplication ?: return

        val hasConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        var deviceName: String? = null
        var deviceAddress: String? = null
        if (hasConnectPerm) {
            runCatching {
                @Suppress("DEPRECATION")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                deviceName = device?.name
                deviceAddress = device?.address
            }
        }

        app.telemetryRepository.handleBluetoothAclConnected(
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }
}
