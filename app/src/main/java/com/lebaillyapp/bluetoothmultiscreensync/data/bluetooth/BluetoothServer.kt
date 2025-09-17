package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.*

/**
 * ## BluetoothServer
 *
 * A server class for accepting incoming Bluetooth Classic connections (SPP/RFCOMM).
 * This class listens for client connections and exposes both the connection events
 * and the server's state via [SharedFlow] and [StateFlow].
 *
 * ### Features:
 * - Listens for incoming connections using a [BluetoothServerSocket].
 * - Emits new connections as [BluetoothConnection] objects.
 * - Exposes the current server state via [StateFlow] of [ServerState].
 * - Handles proper cleanup and shutdown of the server socket.
 *
 * @property adapter The [BluetoothAdapter] used to manage Bluetooth operations.
 * @property serviceName A human-readable name for the Bluetooth service (default `"BTMultiScreenSync"`).
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

    private val _incomingConnections = MutableSharedFlow<BluetoothConnection>(extraBufferCapacity = 16)
    /**
     * A [SharedFlow] emitting new [BluetoothConnection] objects when a client connects.
     * Use this to handle incoming messages or manage active connections.
     */
    val incomingConnections: SharedFlow<BluetoothConnection> = _incomingConnections

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    /**
     * A [StateFlow] representing the current state of the server.
     * - [ServerState.Stopped] → server is not running
     * - [ServerState.Listening] → server is ready and listening for connections
     * - [ServerState.Error] → an error occurred while listening
     */
    val state: StateFlow<ServerState> = _state

    /**
     * Starts listening for incoming Bluetooth connections.
     *
     * Requires [Manifest.permission.BLUETOOTH_CONNECT] at runtime.
     *
     * Launches a coroutine on [Dispatchers.IO] that continuously calls [BluetoothServerSocket.accept]
     * to accept new connections. Each accepted connection is wrapped in a [BluetoothConnection]
     * and emitted through [incomingConnections].
     *
     * Idempotent: calling this method while already listening has no effect.
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
                        _incomingConnections.emit(connection)
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
     * Stops listening for connections and shuts down the server.
     *
     * Cancels the listening coroutine, closes the server socket, and updates the
     * [state] to [ServerState.Stopped].
     */
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {}
        serverSocket = null
        _state.tryEmit(ServerState.Stopped)
    }
}
