package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ServerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.*

/**
 * ## BluetoothServer (multi-device, robust)
 *
 * Manages multiple incoming Bluetooth Classic connections (SPP/RFCOMM) safely with coroutines.
 *
 * ### Features:
 * - Listens for multiple clients using a [BluetoothServerSocket].
 * - Creates one [BluetoothConnection] per client and stores them in memory.
 * - Exposes each accepted connection via [incomingConnections].
 * - Exposes all incoming messages via [incomingMessages].
 * - Broadcasts messages received from one client to all other connected clients.
 * - Provides server lifecycle state via [state].
 * - Handles cleanup properly when stopped or when clients disconnect.
 *
 * ### Lifecycle:
 * 1. Call [start] to begin listening for incoming connections.
 * 2. Observe [incomingConnections] to get new clients.
 * 3. Observe [incomingMessages] for messages from clients.
 * 4. Use [stop] to close the server and all connections.
 *
 * @property adapter The [BluetoothAdapter] used to manage Bluetooth operations.
 * @property serviceName The human-readable name for the Bluetooth service (default `"BTMultiScreenSync"`).
 * @property serviceUUID The UUID representing the service exposed by the server.
 */
class BluetoothServer(
    private val adapter: BluetoothAdapter,
    private val serviceName: String = "BTMultiScreenSync",
    private val serviceUUID: UUID
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var listeningJob: Job? = null

    private val connections = mutableListOf<BluetoothConnection>()

    private val _incomingConnections = MutableSharedFlow<BluetoothConnection>(extraBufferCapacity = 16)
    /** Emits each new [BluetoothConnection] when a client connects. */
    val incomingConnections: SharedFlow<BluetoothConnection> = _incomingConnections.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Emits all incoming messages from any connected client. */
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    /** Current state of the server (Stopped, Listening, Connected, Error). */
    val state: StateFlow<ServerState> = _state.asStateFlow()

    /**
     * ## Starts listening for incoming Bluetooth connections.
     *
     * Requires [Manifest.permission.BLUETOOTH_CONNECT].
     * Idempotent: calling while already listening has no effect.
     *
     * For each accepted client:
     * - Wraps it in a [BluetoothConnection].
     * - Adds it to [connections].
     * - Emits the connection to [incomingConnections].
     * - Listens for messages and broadcasts them to other clients.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun start() {
        if (listeningJob != null) return // already listening

        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(serviceName, serviceUUID)
            _state.tryEmit(ServerState.Listening)

            listeningJob = scope.launch {
                while (isActive) {
                    try {
                        val socket: BluetoothSocket = serverSocket?.accept() ?: break
                        val connection = BluetoothConnection(socket, scope)

                        addConnection(connection)
                        _state.emit(ServerState.Connected(socket.remoteDevice))
                        _incomingConnections.emit(connection)

                        launch {
                            try {
                                connection.incomingMessages.collect { message ->
                                    _incomingMessages.emit(message)
                                    broadcastMessage(message, exclude = connection)
                                }
                            } catch (_: Exception) {
                                removeConnection(connection)
                            }
                        }
                    } catch (e: IOException) {
                        _state.emit(ServerState.Error(e))
                        break
                    }
                }
            }
        } catch (e: IOException) {
            _state.tryEmit(ServerState.Error(e))
        }
    }

    /**
     * ## Broadcasts a message to all connected clients
     * optionally excluding the sender.
     */
    private  fun broadcastMessage(message: String, exclude: BluetoothConnection? = null) {
        val snapshot = getConnectionsSnapshot()
        snapshot.forEach { conn ->
            if (conn != exclude) {
                try {
                    conn.sendMessage(message)
                } catch (_: IOException) {
                    removeConnection(conn)
                }
            }
        }
    }

    private fun addConnection(connection: BluetoothConnection) {
        synchronized(connections) { connections.add(connection) }
    }

    private fun removeConnection(connection: BluetoothConnection) {
        synchronized(connections) { connections.remove(connection) }
        connection.close()
        if (connections.isEmpty()) _state.tryEmit(ServerState.Listening)
    }

    /**
     * ## Stops the server and disconnects all clients.
     * Cancels listening job, closes server socket, clears connections.
     */
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null

        synchronized(connections) {
            connections.forEach { it.close() }
            connections.clear()
        }

        _state.tryEmit(ServerState.Stopped)
    }

    /**
     * ## Returns a snapshot of currently active connections.
     */
    fun getConnectionsSnapshot(): List<BluetoothConnection> =
        synchronized(connections) { connections.toList() }
}
