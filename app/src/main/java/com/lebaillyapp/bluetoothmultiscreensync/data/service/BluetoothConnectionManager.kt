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
import kotlin.coroutines.resume

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

    /** Master mode: start server - Expose le service avec notre UUID custom */
    fun startServer(serviceName: String = "BTMultiScreenSync") {
        if (bluetoothAdapter == null) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _errors.tryEmit(SecurityException("BLUETOOTH_CONNECT permission required"))
            return
        }

        stop()

        try {
            // Rendre le device découvrable temporairement
            makeDiscoverable()

            // Créer le service avec notre UUID
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

    /** Rendre le device découvrable pour que les slaves puissent le trouver */
    private fun makeDiscoverable() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return

        try {
            // Le device devient découvrable pendant 120 secondes
            bluetoothAdapter?.let { adapter ->
                if (adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    // Note: En pratique, il faudrait demander à l'utilisateur via une Intent
                    // mais pour la démo, on peut forcer la discoverabilité si possible
                }
            }
        } catch (e: SecurityException) {
            _errors.tryEmit(e)
        }
    }

    /** Slave mode: connect to master using UUID */
    fun connectToServer(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _errors.tryEmit(SecurityException("BLUETOOTH_CONNECT permission required"))
            return
        }

        stop()

        scope.launch {
            try {
                // Se connecter directement avec notre UUID
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

    /** Vérifier si un device expose notre service UUID */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun hasOurService(device: BluetoothDevice): Boolean {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false

        return withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                scope.launch {
                    try {
                        // Essayer de créer une socket avec notre UUID
                        val testSocket = device.createRfcommSocketToServiceRecord(appUuid)
                        try {
                            testSocket.connect()
                            testSocket.close()
                            cont.resume(true)
                        } catch (e: Exception) {
                            cont.resume(false)
                        }
                    } catch (e: Exception) {
                        cont.resume(false)
                    }
                }
            }
        } ?: false
    }

    /** Send message to all connected peers (master or slave) */
    fun sendMessage(message: String) {
        scope.launch {
            val messageWithDelimiter = "$message\n" // Ajouter délimiteur

            // Broadcast to all clients (master)
            clientSockets.removeAll { socket ->
                try {
                    socket.outputStream.write(messageWithDelimiter.toByteArray())
                    socket.outputStream.flush()
                    false
                } catch (e: IOException) {
                    _connectionEvents.tryEmit(ConnectionEvent.ClientDisconnected(socket.remoteDevice))
                    safeClose(socket)
                    true
                }
            }

            // Send to server (slave)
            clientSocket?.let { socket ->
                try {
                    socket.outputStream.write(messageWithDelimiter.toByteArray())
                    socket.outputStream.flush()
                }
                catch (e: IOException) {
                    _errors.tryEmit(e)
                    safeClose(socket)
                    clientSocket = null
                }
            }
        }
    }

    private fun listenForMessages(socket: BluetoothSocket) {
        scope.launch {
            val buffer = ByteArray(1024)
            val messageBuffer = StringBuilder()

            try {
                val inputStream: InputStream = socket.inputStream
                while (isActive) {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        messageBuffer.append(receivedData)

                        // Traiter les messages complets (délimités par \n)
                        while (messageBuffer.contains('\n')) {
                            val lineEnd = messageBuffer.indexOf('\n')
                            val message = messageBuffer.substring(0, lineEnd)
                            messageBuffer.delete(0, lineEnd + 1)

                            if (message.isNotEmpty()) {
                                _incomingMessages.emit(message)
                            }
                        }
                    }
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