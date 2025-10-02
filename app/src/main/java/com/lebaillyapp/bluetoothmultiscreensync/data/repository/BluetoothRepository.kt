package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.*
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.*

/**
 * ## BluetoothRepository
 *
 * High-level repository exposing Bluetooth Classic features (scan, server, client) to the app.
 *
 * ### Responsibilities:
 * - Scanning for nearby devices ([startScan], [stopScan]).
 * - Running a Bluetooth server ([startServer], [stopServer]).
 * - Connecting as a client to a remote server ([connectToDevice], [disconnect]).
 * - Sending messages to connected peers ([sendMessage]).
 * - Aggregating connection states and incoming messages from both server and client.
 *
 * ### Exposed Flows:
 * - [scanResults]: list of discovered devices during scan.
 * - [serverState]: current server state.
 * - [incomingConnections]: connections accepted by the server.
 * - [incomingMessages]: all messages received from any client or server connection.
 * - [clientState]: current client connection state.
 * - [currentConnection]: active client connection (if any).
 *
 * All operations are coroutine-safe and run on [Dispatchers.IO].
 *
 * @property context Application [Context], needed for Bluetooth scanning.
 * @property adapter The [BluetoothAdapter] used for managing Bluetooth operations.
 * @property serviceUUID The UUID used for server-client connections (SPP/RFCOMM).
 */
class BluetoothRepository(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val serviceUUID: UUID
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Scanner ---
    private var scanner: BluetoothScanner? = null
    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()

    /**
     * ### Starts scanning for nearby Bluetooth devices.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (scanner == null) {
            scanner = BluetoothScanner(context, adapter)
            scope.launch {
                scanner!!.devices.collect { _scanResults.value = it }
            }
        }
        scanner!!.startScan()
    }

    /**
     * ### Stops scanning for nearby Bluetooth devices.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner?.stopScan()
    }

    // --- Server ---
    private var server: BluetoothServer? = null
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState

    private val _incomingConnections = MutableSharedFlow<BluetoothConnection>(extraBufferCapacity = 16)
    val incomingConnections: SharedFlow<BluetoothConnection> = _incomingConnections

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    /**
     * ### Starts a Bluetooth server to accept incoming client connections.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer() {
        if (server != null) return

        server = BluetoothServer(adapter, serviceUUID = serviceUUID)

        // Collect server flows safely
        scope.launch { server!!.state.collect { _serverState.value = it } }
        scope.launch { server!!.incomingConnections.collect { _incomingConnections.emit(it) } }
        scope.launch { server!!.incomingMessages.collect { _incomingMessages.emit(it) } }

        server!!.start()
    }

    /**
     * ### Stops the Bluetooth server and clears state.
     */
    fun stopServer() {
        server?.stop()
        server = null
        _serverState.value = ServerState.Stopped
    }

    // --- Client ---
    private var client: BluetoothClient? = null
    private val _clientState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val clientState: StateFlow<ConnectionState> = _clientState

    private var _currentConnection: BluetoothConnection? = null
    val currentConnection: BluetoothConnection? get() = _currentConnection

    /**
     * ### Connects to a remote server using a [BluetoothDevice].
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        disconnect() // disconnect previous

        client = BluetoothClient(adapter, device, serviceUUID)

        // Collect client state
        scope.launch { client!!.state.collect { _clientState.value = it } }
        // Collect client messages
        scope.launch { client!!.incomingMessages.collect { _incomingMessages.emit(it) } }
        // Update current active connection
        scope.launch { client!!.activeConnection.collect { conn -> _currentConnection = conn } }

        client!!.connect()
    }

    /**
     * ### Sends a message to all connected server clients
     * and to the active client connection if present.
     */
    fun sendMessage(message: String) {
        scope.launch {
            server?.getConnectionsSnapshot()?.forEach { conn ->
                try { conn.sendMessage(message) } catch (_: IOException) {}
            }

            try { client?.sendMessage(message) } catch (_: IOException) {}
        }
    }

    /**
     * ### Disconnects the client and clears state.
     */
    fun disconnect() {
        client?.disconnect()
        client = null
        _currentConnection = null
        _clientState.value = ConnectionState.Disconnected
    }
}
