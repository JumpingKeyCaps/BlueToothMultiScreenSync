package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.UUID

/**
 * Bluetooth client for connecting to a master device (server) in the BlueToothMultiScreenSync project.
 *
 * This client handles a single connection to a server, sending messages and
 * emitting incoming messages via [incomingMessages] flow.
 *
 * Permissions required: BLUETOOTH_CONNECT (Android 12+)
 *
 * @param context Android context used to check permissions.
 */
class BluetoothClient(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The active Bluetooth socket connected to the server */
    private var socket: BluetoothSocket? = null

    /** Local Bluetooth adapter */
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /** Flow emitting incoming messages from the server */
    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    companion object {
        /** Standard SPP UUID used for RFCOMM connections */
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    /**
     * Checks if the app has BLUETOOTH_CONNECT permission (Android 12+)
     * @return true if permission is granted, false otherwise
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Connects to a master (server) Bluetooth device.
     *
     * On successful connection, it starts listening for incoming messages.
     *
     * @param device The BluetoothDevice to connect to
     * @throws SecurityException if BLUETOOTH_CONNECT permission is missing
     */
    fun connectToServer(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) throw SecurityException("Missing BLUETOOTH_CONNECT permission")

        scope.launch {
            try {
                socket = device.createRfcommSocketToServiceRecord(BT_UUID)

                if (hasBluetoothConnectPermission()) {
                    bluetoothAdapter?.cancelDiscovery()
                }

                socket?.connect()
                socket?.let { listenToSocket(it) }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (se: SecurityException) {
                se.printStackTrace()
            }
        }
    }

    /**
     * Continuously listens to the connected socket for incoming messages.
     *
     * Each message received is emitted via [incomingMessages].
     *
     * @param socket The connected BluetoothSocket to listen to
     */
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
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends a string message to the connected server.
     *
     * @param message The string message to send
     */
    fun sendMessage(message: String) {
        scope.launch {
            try {
                socket?.outputStream?.write(message.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Disconnects from the server and cancels any active listening coroutines.
     */
    fun disconnect() {
        scope.cancel()
        try { socket?.close() } catch (_: IOException) {}
    }
}
