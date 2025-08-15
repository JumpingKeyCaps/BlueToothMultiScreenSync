package com.lebaillyapp.bluetoothmultiscreensync.data.service.old

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

/**
 * Bluetooth server for accepting connections from multiple clients in the BlueToothMultiScreenSync project.
 *
 * This server listens for incoming connections from clients, handles message reception,
 * and can broadcast messages to all connected clients.
 *
 * Permissions required: BLUETOOTH_CONNECT (Android 12+)
 *
 * @param context Android context used to check permissions.
 */
class BluetoothServer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The active server socket listening for incoming connections */
    private var serverSocket: BluetoothServerSocket? = null

    /** Local Bluetooth adapter */
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /** List of currently connected client sockets */
    private val connectedSockets = mutableListOf<BluetoothSocket>()

    /** Flow emitting incoming messages from clients */
    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    companion object {
        /** Standard SPP UUID used for RFCOMM connections */
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Service name for Bluetooth SDP registration */
        const val SERVICE_NAME = "BlueToothMultiScreenSyncServer"
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
     * Starts the Bluetooth server and listens for incoming client connections.
     *
     * Each accepted client is stored in [connectedSockets] and starts listening for messages.
     *
     * @throws SecurityException if BLUETOOTH_CONNECT permission is missing
     */
    fun startServer() {
        if (!hasBluetoothConnectPermission()) {
            // TODO: Request permission via ActivityResultLauncher if needed
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

    /**
     * Listens continuously to a connected client socket for incoming messages.
     *
     * Incoming messages are emitted via [incomingMessages].
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
                connectedSockets.remove(socket)
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends a message to all currently connected clients.
     *
     * Any socket that fails to send is removed from [connectedSockets].
     *
     * @param message The string message to broadcast
     */
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

    /**
     * Stops the server, closes all client connections and cancels active coroutines.
     */
    fun stopServer() {
        scope.cancel()
        connectedSockets.forEach { try { it.close() } catch (_: IOException) {} }
        try { serverSocket?.close() } catch (_: IOException) {}
    }
}
