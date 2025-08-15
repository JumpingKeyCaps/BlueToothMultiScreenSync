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

/**
 * Manages Bluetooth connections in master (server) or slave (client) mode.
 *
 * Handles:
 *  - Starting a server to accept incoming connections
 *  - Connecting to another device as a client
 *  - Discovering nearby devices
 *  - Sending and receiving messages
 *  - Managing connection lifecycle and errors
 *
 * @param context Android context used for Bluetooth operations and starting intents.
 */
class BluetoothConnectionManager(private val context: Context) {

    /** Android Bluetooth adapter, null if device does not support Bluetooth */
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /** Coroutine scope for async operations (IO dispatcher + SupervisorJob) */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Server socket used when acting as a master */
    private var serverSocket: BluetoothServerSocket? = null

    /** List of connected client sockets (master mode) */
    private val clientSockets = ConcurrentLinkedQueue<BluetoothSocket>()

    /** Single client socket used when connected as a slave */
    private var clientSocket: BluetoothSocket? = null

    /** Flow emitting incoming messages from all connections */
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Flow emitting connection events such as new client or server started */
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)

    /** Flow emitting errors encountered during Bluetooth operations */
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)

    /** Public access to incoming messages */
    val incomingMessages: SharedFlow<String> = _incomingMessages

    /** Public access to connection events */
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    /** Public access to errors */
    val errors: SharedFlow<Throwable> = _errors

    /** Custom UUID used to identify our Bluetooth service */
    private val appUuid: UUID = UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666")

    /**
     * Represents events that occur during connection lifecycle.
     */
    sealed class ConnectionEvent {
        /** A client connected to the server */
        data class ClientConnected(val device: BluetoothDevice) : ConnectionEvent()

        /** A client disconnected from the server */
        data class ClientDisconnected(val device: BluetoothDevice) : ConnectionEvent()

        /** Server has started and is ready to accept connections */
        object ServerStarted : ConnectionEvent()

        /** Connected to a remote server (slave mode) */
        object ConnectedToServer : ConnectionEvent()
    }

    /**
     * Checks if the given permission is granted.
     *
     * @param permission The Android permission to check.
     * @return true if granted, false otherwise.
     */
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Start the server (master mode) and expose our custom UUID service.
     *
     * @param serviceName Optional name of the Bluetooth service.
     */
    fun startServer(serviceName: String = "BTMultiScreenSync") {
        if (bluetoothAdapter == null) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _errors.tryEmit(SecurityException("BLUETOOTH_CONNECT permission required"))
            return
        }

        stop()

        try {
            makeDiscoverable()
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

    /**
     * Make the device discoverable so that clients (slaves) can find it.
     *
     * Automatically launches an intent for 120 seconds discoverability.
     */
    private fun makeDiscoverable() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return

        bluetoothAdapter?.let { adapter ->
            try {
                if (adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    val discoverableIntent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(discoverableIntent)
                }
            } catch (e: SecurityException) {
                _errors.tryEmit(e)
            }
        }
    }

    /**
     * Connects to a remote server (slave mode) using the custom UUID.
     *
     * @param device The remote Bluetooth device to connect to.
     */
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

    /**
     * Checks if a remote device exposes our service UUID.
     *
     * @param device The remote Bluetooth device to test.
     * @return true if the service is available, false otherwise.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun hasOurService(device: BluetoothDevice): Boolean {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false

        return withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                scope.launch {
                    try {
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

    /**
     * Sends a text message to all connected peers (clients or server).
     *
     * @param message The message string to send.
     */
    fun sendMessage(message: String) {
        scope.launch {
            val messageWithDelimiter = "$message\n"

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

    /**
     * Listens for incoming messages on the given socket.
     *
     * Messages are expected to be delimited by '\n'.
     *
     * @param socket The Bluetooth socket to read from.
     */
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

    /**
     * Handles disconnection of a socket and emits corresponding events.
     *
     * @param socket The socket that got disconnected.
     */
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

    /**
     * Safely closes a closeable resource, emitting errors if any occur.
     *
     * @param closeable The resource to close.
     */
    private fun safeClose(closeable: AutoCloseable?) {
        try { closeable?.close() } catch (e: Exception) { _errors.tryEmit(e) }
    }

    /**
     * Returns the current number of connected clients (master mode).
     *
     * @return Count of connected client sockets.
     */
    fun getConnectedClientsCount(): Int = clientSockets.size

    /**
     * Stops all connections and clears sockets.
     */
    fun stop() {
        clientSockets.forEach { safeClose(it) }
        clientSockets.clear()
        safeClose(clientSocket)
        clientSocket = null
        safeClose(serverSocket)
        serverSocket = null
    }

    /**
     * Cleans up resources and cancels internal coroutine scope.
     */
    fun onDestroy() {
        stop()
        scope.cancel()
    }
}
