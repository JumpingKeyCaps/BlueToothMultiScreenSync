package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.*

/**
 * ## BluetoothClient (robust)
 *
 * Connects to a Bluetooth Classic server (SPP/RFCOMM) and exposes connection state and messages.
 *
 * ### Features:
 * - Connects to a remote server using a [BluetoothDevice] and a UUID.
 * - Exposes connection state via [state].
 * - Exposes a [SharedFlow] of incoming messages via [incomingMessages].
 * - Provides the current active connection via [activeConnection].
 * - Provides [sendMessage] to send data to the server.
 * - Cleans up properly on disconnect or error.
 *
 * ### Lifecycle:
 * 1. Call [connect] to establish a connection.
 * 2. Observe [state] to track connection progress and errors.
 * 3. Observe [incomingMessages] to receive messages from the server.
 * 4. Call [sendMessage] to send a message to the server.
 * 5. Call [disconnect] to safely close the connection.
 *
 * @property adapter The [BluetoothAdapter] used to manage Bluetooth operations.
 * @property device The remote [BluetoothDevice] to connect to.
 * @property serviceUUID The UUID of the remote service.
 */
class BluetoothClient(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val serviceUUID: UUID
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    /** Current connection state (Disconnected, Connecting, Connected, Error) */
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _activeConnection = MutableStateFlow<BluetoothConnection?>(null)
    /** Current active connection, null if disconnected */
    val activeConnection: StateFlow<BluetoothConnection?> = _activeConnection.asStateFlow()

    private var connection: BluetoothConnection? = null

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Emits incoming messages from the server */
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /**
     * ## Initiates a connection to the remote server.
     *
     * Requires [Manifest.permission.BLUETOOTH_CONNECT].
     * Idempotent: calling while already connecting has no effect.
     *
     * Updates [state] to reflect progress.
     * On success, sets [activeConnection] and starts collecting messages.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        if (connectionJob != null) return // already connecting

        _state.value = ConnectionState.Connecting

        connectionJob = scope.launch {
            try {
                socket = device.createRfcommSocketToServiceRecord(serviceUUID)
                try { adapter.cancelDiscovery() } catch (_: SecurityException) {}

                socket?.connect()

                val btConnection = BluetoothConnection(socket!!, scope)
                connection = btConnection
                _activeConnection.value = btConnection
                _state.value = ConnectionState.Connected

                launch {
                    try {
                        btConnection.incomingMessages.collect { msg ->
                            _incomingMessages.emit(msg)
                        }
                    } catch (_: Exception) {
                        disconnect()
                        _state.value = ConnectionState.Error(IOException("Connection lost"))
                    }
                }
            } catch (e: IOException) {
                _state.value = ConnectionState.Error(e)
                try { socket?.close() } catch (_: IOException) {}
            }
        }
    }

    /**
     * ## Sends a [message] to the server.
     *
     * Throws IOException if the connection fails.
     * If sending fails, the client disconnects and updates [state] to [ConnectionState.Error].
     *
     * @param message The string message to send.
     */
    fun sendMessage(message: String) {
        try {
            connection?.sendMessage(message)
        } catch (e: IOException) {
            disconnect()
            _state.value = ConnectionState.Error(e)
        }
    }

    /**
     * ## Disconnects from the server
     * closes all resources, and resets [state] and [activeConnection].
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null

        try { socket?.close() } catch (_: IOException) {}
        socket = null

        connection?.close()
        connection = null
        _activeConnection.value = null

        _state.value = ConnectionState.Disconnected
    }
}
