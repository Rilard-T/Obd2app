package com.example.obd.protocol

import com.example.obd.data.ObdSessionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiObdTransport(
    private val host: String,
    private val port: Int,
    private val logger: ObdSessionLogger
) : ObdTransport {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } == true

    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        logger.info("WIFI", "Подключение к Wi-Fi ELM327 $host:$port...")
        val tcpSocket = Socket()
        tcpSocket.tcpNoDelay = true
        tcpSocket.connect(InetSocketAddress(host, port), 6000)
        tcpSocket.soTimeout = 1500
        socket = tcpSocket
        inputStream = tcpSocket.getInputStream()
        outputStream = tcpSocket.getOutputStream()
        logger.info("WIFI", "TCP-канал установлен ($host:$port)")
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IllegalStateException("Wi-Fi OBD socket is not connected")
        val inp = inputStream ?: throw IllegalStateException("Wi-Fi OBD input stream closed")

        val cleanCmd = command.trim()
        logger.tx(cleanCmd)
        out.write("$cleanCmd\r".toByteArray(Charsets.US_ASCII))
        out.flush()

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(1024)

        while (System.currentTimeMillis() < deadline) {
            val available = inp.available()
            if (available > 0) {
                val readCount = inp.read(buffer, 0, minOf(available, buffer.size))
                if (readCount > 0) {
                    val chunk = String(buffer, 0, readCount, Charsets.US_ASCII)
                    sb.append(chunk)
                    if (sb.contains('>')) break
                }
            } else {
                delay(18)
            }
        }

        val response = sb.toString()
        logger.rx(response)
        response
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        inputStream = null
        outputStream = null
        socket = null
    }
}
