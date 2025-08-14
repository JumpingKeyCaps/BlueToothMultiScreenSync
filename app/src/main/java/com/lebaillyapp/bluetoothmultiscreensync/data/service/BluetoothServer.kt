package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothServer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: BluetoothServerSocket? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val connectedSockets = mutableListOf<BluetoothSocket>()

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    companion object {
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val SERVICE_NAME = "BlueToothMultiScreenSyncServer"
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startServer() {
        if (!hasBluetoothConnectPermission()) {
            // TODO: lancer la demande de permission via ActivityResultLauncher
            throw SecurityException("Missing BLUETOOTH_CONNECT permission")
        }

        scope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    BT_UUID
                )
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    connectedSockets.add(socket)
                    listenToSocket(socket)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (se: SecurityException) {
                se.printStackTrace()
            }
        }
    }

    private fun listenToSocket(socket: BluetoothSocket) {
        scope.launch {
            try {
                val input: InputStream = socket.inputStream
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (true) {
                    bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        val message = String(buffer, 0, bytesRead)
                        _incomingMessages.emit(message)
                    }
                }
            } catch (e: IOException) {
                connectedSockets.remove(socket)
                e.printStackTrace()
            }
        }
    }

    fun sendMessageToAll(message: String) {
        scope.launch {
            val iterator = connectedSockets.iterator()
            while (iterator.hasNext()) {
                val socket = iterator.next()
                try {
                    val output: OutputStream = socket.outputStream
                    output.write(message.toByteArray())
                } catch (e: IOException) {
                    iterator.remove()
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopServer() {
        scope.cancel()
        connectedSockets.forEach { try { it.close() } catch (_: IOException) {} }
        try { serverSocket?.close() } catch (_: IOException) {}
    }
}