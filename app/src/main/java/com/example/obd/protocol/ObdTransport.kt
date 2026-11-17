package com.example.obd.protocol

interface ObdTransport {
    val isConnected: Boolean
    suspend fun connect()
    suspend fun sendCommand(command: String, timeoutMs: Long = 2500L): String
    suspend fun disconnect()
}
