package com.example.obd.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.example.obd.data.ObdSessionLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * BLE GATT transport compatible with common BLE ELM327 adapters (Vgate iCar Pro BLE,
 * Veepeak BLE, OBDLink CX, Carista, and Nordic UART bridges).
 */
class BleObdTransport(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: String,
    private val logger: ObdSessionLogger? = null
) : ObdTransport {

    companion object {
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val KNOWN_SERVICE_UUIDS = listOf(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        )
    }

    private val ioMutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    @Volatile
    private var connectedFlag = false

    private var connectDeferred: CompletableDeferred<Unit>? = null
    private var responseDeferred: CompletableDeferred<String>? = null
    private val responseBuffer = StringBuilder()

    override val isConnected: Boolean
        get() = connectedFlag && gatt != null && writeChar != null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                logger?.info("BT-BLE", "GATT подключён, поиск сервисов...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedFlag = false
                logger?.warn("BT-BLE", "GATT отключён (status=$status)")
                connectDeferred?.completeExceptionally(IOException("BLE устройство отключено (код $status)"))
                responseDeferred?.completeExceptionally(IOException("BLE соединение разорвано"))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logger?.error("BT-BLE", "Ошибка обнаружения GATT сервисов ($status)")
                connectDeferred?.completeExceptionally(IOException("Ошибка обнаружения BLE сервисов ($status)"))
                return
            }

            val selected = findObdCharacteristics(gatt)
            if (selected == null) {
                logger?.error("BT-BLE", "Не найдены GATT характеристики OBD2 (${gatt.services.size} сервисов)")
                connectDeferred?.completeExceptionally(
                    IOException("Не найдены GATT характеристики OBD2 на устройстве")
                )
                return
            }

            writeChar = selected.first
            notifyChar = selected.second
            logger?.info("BT-BLE", "Выбраны GATT характеристики write=${selected.first.uuid}, notify=${selected.second.uuid}")

            val notify = selected.second
            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                connectedFlag = true
                connectDeferred?.complete(Unit)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectedFlag = true
                connectDeferred?.complete(Unit)
            } else {
                logger?.error("BT-BLE", "Ошибка записи CCCD дескриптора (status=$status)")
                connectDeferred?.completeExceptionally(IOException("Не удалось включить уведомления BLE"))
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingBytes(bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val text = String(bytes, Charsets.US_ASCII)
        synchronized(responseBuffer) {
            responseBuffer.append(text)
            if (responseBuffer.contains('>')) {
                val full = responseBuffer.toString()
                responseBuffer.clear()
                logger?.rx(full)
                responseDeferred?.complete(full)
            }
        }
    }

    private fun findObdCharacteristics(
        gatt: BluetoothGatt
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val orderedServices = KNOWN_SERVICE_UUIDS.mapNotNull { gatt.getService(it) } +
            gatt.services.filter { it.uuid !in KNOWN_SERVICE_UUIDS }

        for (service in orderedServices) {
            var candidateWrite: BluetoothGattCharacteristic? = null
            var candidateNotify: BluetoothGattCharacteristic? = null
            for (ch in service.characteristics) {
                val props = ch.properties
                val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                if (canWrite && candidateWrite == null) candidateWrite = ch
                if (canNotify && candidateNotify == null) candidateNotify = ch
            }
            if (candidateWrite != null && candidateNotify != null) {
                return candidateWrite to candidateNotify
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        logger?.info("BT-BLE", "BLE подключение к $deviceAddress...")
        val deferred = CompletableDeferred<Unit>()
        connectDeferred = deferred

        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        try {
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            logger?.error("BT-BLE", "Нет разрешения Bluetooth для BLE", e)
            throw IOException("Нет разрешения Bluetooth для BLE подключения", e)
        }

        withTimeout(10_000L) {
            deferred.await()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendCommand(command: String, timeoutMs: Long): String =
        ioMutex.withLock {
            val activeGatt = gatt ?: throw IOException("BLE GATT не подключён")
            val ch = writeChar ?: throw IOException("BLE характеристика записи недоступна")

            val deferred = CompletableDeferred<String>()
            synchronized(responseBuffer) {
                responseBuffer.clear()
            }
            responseDeferred = deferred

            logger?.tx(command)
            val payload = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
            val writeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                val result = activeGatt.writeCharacteristic(ch, payload, writeType)
                result == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = payload
                @Suppress("DEPRECATION")
                activeGatt.writeCharacteristic(ch)
            }

            if (!writeOk) {
                logger?.error("BT-BLE", "Не удалось отправить команду BLE ($command)")
                throw IOException("Не удалось отправить команду BLE ($command)")
            }

            withTimeout(timeoutMs) {
                deferred.await()
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectedFlag = false
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        writeChar = null
        notifyChar = null
        connectDeferred?.cancel()
        responseDeferred?.cancel()
        Unit
    }
}
