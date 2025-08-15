package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class BluetoothConnectionManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private val clientSockets = ConcurrentLinkedQueue<BluetoothSocket>()
    private var clientSocket: BluetoothSocket? = null

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)

    val incomingMessages: SharedFlow<String> = _incomingMessages
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents
    val errors: SharedFlow<Throwable> = _errors

    private val appUuid: UUID = UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666")

    sealed class ConnectionEvent {
        data class ClientConnected(val device: BluetoothDevice) : ConnectionEvent()
        data class ClientDisconnected(val device: BluetoothDevice) : ConnectionEvent()
        object ServerStarted : ConnectionEvent()
        object ConnectedToServer : ConnectionEvent()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Master mode: start server */
    fun startServer(serviceName: String = "BTMultiScreenSync") {
        if (bluetoothAdapter == null) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _errors.tryEmit(SecurityException("BLUETOOTH_CONNECT permission required"))
            return
        }

        stop()

        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(serviceName, appUuid)
        } catch (se: SecurityException) {
            _errors.tryEmit(se)
            return
        }

        scope.launch {
            _connectionEvents.emit(ConnectionEvent.ServerStarted)
            try {
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    clientSockets.add(socket)
                    _connectionEvents.tryEmit(ConnectionEvent.ClientConnected(socket.remoteDevice))
                    listenForMessages(socket)
                }
            } catch (e: Exception) {
                _errors.tryEmit(e)
            }
        }
    }

    /** Slave mode: connect to master */
    fun connectToServer(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _errors.tryEmit(SecurityException("BLUETOOTH_CONNECT permission required"))
            return
        }

        stop()

        scope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(appUuid)
                try { bluetoothAdapter?.cancelDiscovery() } catch (se: SecurityException) { _errors.tryEmit(se) }

                socket.connect()
                clientSocket = socket
                _connectionEvents.tryEmit(ConnectionEvent.ConnectedToServer)
                listenForMessages(socket)
            } catch (e: Exception) {
                _errors.tryEmit(e)
            }
        }
    }

    /** Send message to all connected peers (master or slave) */
    fun sendMessage(message: String) {
        scope.launch {
            // Broadcast to all clients (master)
            clientSockets.removeAll { socket ->
                try {
                    socket.outputStream.write(message.toByteArray())
                    false
                } catch (e: IOException) {
                    _connectionEvents.tryEmit(ConnectionEvent.ClientDisconnected(socket.remoteDevice))
                    safeClose(socket)
                    true
                }
            }

            // Send to server (slave)
            clientSocket?.let { socket ->
                try { socket.outputStream.write(message.toByteArray()) }
                catch (e: IOException) { _errors.tryEmit(e); safeClose(socket); clientSocket = null }
            }
        }
    }

    private fun listenForMessages(socket: BluetoothSocket) {
        scope.launch {
            val buffer = ByteArray(1024)
            try {
                val inputStream: InputStream = socket.inputStream
                while (isActive) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) _incomingMessages.emit(String(buffer, 0, bytes))
                }
            } catch (e: IOException) {
                handleDisconnection(socket)
            } catch (e: Exception) {
                _errors.tryEmit(e)
            }
        }
    }

    private fun handleDisconnection(socket: BluetoothSocket) {
        if (clientSockets.contains(socket)) {
            clientSockets.remove(socket)
            scope.launch { _connectionEvents.emit(ConnectionEvent.ClientDisconnected(socket.remoteDevice)) }
        } else if (socket == clientSocket) {
            clientSocket = null
            _errors.tryEmit(IOException("Disconnected from server"))
        }
        safeClose(socket)
    }

    private fun safeClose(closeable: AutoCloseable?) {
        try { closeable?.close() } catch (e: Exception) { _errors.tryEmit(e) }
    }

    fun getConnectedClientsCount(): Int = clientSockets.size

    fun stop() {
        clientSockets.forEach { safeClose(it) }
        clientSockets.clear()
        safeClose(clientSocket)
        clientSocket = null
        safeClose(serverSocket)
        serverSocket = null
    }

    fun onDestroy() {
        stop()
        scope.cancel()
    }
}

