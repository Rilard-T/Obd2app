package com.example.obd.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothClassicObdTransport(
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: String
) : ObdTransport {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val ioMutex = Mutex()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (_: SecurityException) {
        }

        val btSocket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: SecurityException) {
            throw IOException("Нет разрешения Bluetooth для подключения к адаптеру", e)
        }

        try {
            btSocket.connect()
            socket = btSocket
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream
        } catch (e: Exception) {
            try {
                btSocket.close()
            } catch (_: Exception) {
            }
            throw IOException("Не удалось подключиться по SPP к $deviceAddress: ${e.message}", e)
        }
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String =
        ioMutex.withLock {
            withContext(Dispatchers.IO) {
                val out = outputStream ?: throw IOException("OBD2 сокет не подключён")
                val input = inputStream ?: throw IOException("OBD2 поток ввода недоступен")

                // Drain stale bytes if any
                while (input.available() > 0) {
                    input.read()
                }

                val payload = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
                out.write(payload)
                out.flush()

                withTimeout(timeoutMs) {
                    val sb = StringBuilder()
                    val buffer = ByteArray(128)
                    while (true) {
                        if (input.available() > 0) {
                            val readCount = input.read(buffer)
                            if (readCount == -1) {
                                throw IOException("Соединение с OBD2 адаптером разорвано")
                            }
                            val chunk = String(buffer, 0, readCount, Charsets.US_ASCII)
                            sb.append(chunk)
                            if (sb.contains('>')) {
                                break
                            }
                        } else {
                            kotlinx.coroutines.delay(15)
                        }
                    }
                    sb.toString()
                }
            }
        }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        inputStream = null
        outputStream = null
        socket = null
    }
}
