package com.example.obd.protocol

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.obd.data.ObdSessionLogger
import com.example.obd.model.UsbSerialDeviceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

object UsbObdHelper {
    const val ACTION_USB_PERMISSION = "com.aistudio.obd2auto.vkmrtx.USB_PERMISSION"

    private const val VID_FTDI = 0x0403
    private const val VID_CH340 = 0x1A86
    private const val VID_CP210X = 0x10C4
    private const val VID_PROLIFIC = 0x067B

    fun enumerateAdapters(context: Context): List<UsbSerialDeviceItem> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return manager.deviceList.values.mapNotNull { device ->
            val family = identifyChipFamily(device) ?: return@mapNotNull null
            UsbSerialDeviceItem(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                productName = device.productName?.takeIf { it.isNotBlank() } ?: "USB ELM327 ($family)",
                vendorId = device.vendorId,
                productId = device.productId,
                chipFamily = family,
                hasPermission = manager.hasPermission(device)
            )
        }
    }

    fun findDeviceById(context: Context, deviceId: Int): UsbDevice? {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return manager.deviceList.values.firstOrNull { it.deviceId == deviceId }
    }

    fun requestUsbPermission(context: Context, device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        manager.requestPermission(device, pi)
    }

    fun identifyChipFamily(device: UsbDevice): String? {
        return when (device.vendorId) {
            VID_FTDI -> "FTDI FT232"
            VID_CH340 -> "WCH CH340/341"
            VID_CP210X -> "Silicon Labs CP2102"
            VID_PROLIFIC -> "Prolific PL2303"
            else -> {
                var isSerialClass = device.deviceClass == UsbConstants.USB_CLASS_COMM ||
                    device.deviceClass == UsbConstants.USB_CLASS_CDC_DATA
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                        intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                        intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                    ) {
                        isSerialClass = true
                    }
                }
                if (isSerialClass) "USB CDC/Serial" else null
            }
        }
    }
}

class UsbObdTransport(
    private val context: Context,
    private val device: UsbDevice,
    private val logger: ObdSessionLogger,
    private val baudRate: Int = 38400
) : ObdTransport {

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    @Volatile
    private var connected: Boolean = false

    override val isConnected: Boolean
        get() = connected && connection != null

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw IOException("USB Host service unavailable")

        if (!manager.hasPermission(device)) {
            throw SecurityException("USB permission not granted for ${device.deviceName}")
        }

        val conn = manager.openDevice(device)
            ?: throw IOException("Unable to open USB device ${device.deviceName}")

        // Find data interface containing both BULK IN and BULK OUT endpoints
        var dataIntf: UsbInterface? = null
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var foundIn: UsbEndpoint? = null
            var foundOut: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
                }
            }
            if (foundIn != null && foundOut != null) {
                dataIntf = intf
                epIn = foundIn
                epOut = foundOut
                break
            }
        }

        if (dataIntf == null || epIn == null || epOut == null) {
            conn.close()
            throw IOException("No USB serial BULK IN/OUT endpoints found")
        }

        if (!conn.claimInterface(dataIntf, true)) {
            conn.close()
            throw IOException("Failed to claim USB serial interface")
        }

        connection = conn
        claimedInterface = dataIntf
        endpointIn = epIn
        endpointOut = epOut

        configureSerialLine(conn, baudRate)
        connected = true
        logger.info("USB", "Открыт USB Serial порт (${UsbObdHelper.identifyChipFamily(device)}, $baudRate бод)")

        // Drain initial buffer
        delay(120)
        val trash = ByteArray(128)
        conn.bulkTransfer(epIn, trash, trash.size, 80)
        Unit
    }

    private fun configureSerialLine(conn: UsbDeviceConnection, baud: Int) {
        when (device.vendorId) {
            0x0403 -> { // FTDI
                conn.controlTransfer(0x40, 0x00, 0x00, 0, null, 0, 500) // reset
                val divisor = 3_000_000 / baud
                conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500)
                conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500) // 8N1
            }
            0x10C4 -> { // CP210x
                conn.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500) // IFC_ENABLE
                val baudBytes = byteArrayOf(
                    (baud and 0xFF).toByte(),
                    ((baud shr 8) and 0xFF).toByte(),
                    ((baud shr 16) and 0xFF).toByte(),
                    ((baud shr 24) and 0xFF).toByte()
                )
                conn.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, 4, 500)
            }
            0x1A86 -> { // CH340
                conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                val factor = when (baud) {
                    115200 -> 0xCC03
                    else -> 0xB202 // 38400 bps
                }
                conn.controlTransfer(0x40, 0x9A, 0x1312, factor, null, 0, 500)
                conn.controlTransfer(0x40, 0x9A, 0x0F2C, 0x0008, null, 0, 500)
            }
            else -> { // Generic USB CDC ACM SET_LINE_CODING
                val lineCoding = byteArrayOf(
                    (baud and 0xFF).toByte(),
                    ((baud shr 8) and 0xFF).toByte(),
                    ((baud shr 16) and 0xFF).toByte(),
                    ((baud shr 24) and 0xFF).toByte(),
                    0x00, // 1 stop bit
                    0x00, // no parity
                    0x08  // 8 data bits
                )
                conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500) // DTR + RTS
            }
        }
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IOException("USB adapter not connected")
        val epIn = endpointIn ?: throw IOException("USB endpoint missing")
        val epOut = endpointOut ?: throw IOException("USB endpoint missing")

        val payload = "${command.trim()}\r".toByteArray(Charsets.US_ASCII)
        logger.tx("USB -> $command")

        val written = conn.bulkTransfer(epOut, payload, payload.size, 1000)
        if (written < 0) {
            connected = false
            throw IOException("USB write failed for command $command")
        }

        val buffer = ByteArray(256)
        val result = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceIn(50, 400)
            val count = conn.bulkTransfer(epIn, buffer, buffer.size, remaining)
            if (count > 0) {
                val startIdx = if (device.vendorId == 0x0403 && count >= 2) 2 else 0 // FTDI 2-byte modem status
                if (count > startIdx) {
                    val chunk = String(buffer, startIdx, count - startIdx, Charsets.US_ASCII)
                    result.append(chunk)
                    if (result.contains('>')) break
                }
            } else {
                delay(15)
            }
        }

        val raw = result.toString()
        logger.rx("USB <- ${raw.replace("\r", " ").trim()}")
        raw
    }

    override suspend fun disconnect() {
        connected = false
        try {
            claimedInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {
        } finally {
            claimedInterface = null
            connection = null
            endpointIn = null
            endpointOut = null
        }
    }
}
