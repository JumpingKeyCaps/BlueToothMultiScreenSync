package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.*
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ## BluetoothRepository
 *
 * Centralizes all Bluetooth Classic operations: scanning, client connections, and server.
 * Exposes unified [StateFlow] and [SharedFlow] for UI/ViewModel consumption.
 *
 * ### Features:
 * - Scan for nearby devices using [BluetoothScanner].
 * - Act as a server to accept incoming connections via [BluetoothServer].
 * - Connect to a remote device as a client via [BluetoothClient].
 * - Provides unified state flows for server, client, and scan results.
 *
 *
 * @property context Safe [Context] reference (application context recommended).
 * @property adapter [BluetoothAdapter] to perform classic Bluetooth operations.
 * @property serviceUUID The RFCOMM service UUID used for client/server connections.
 */
class BluetoothRepository(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val serviceUUID: UUID
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    // --- Scanner ---
    private var scanner: BluetoothScanner? = null
    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    /** StateFlow representing the current list of discovered devices. */
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()

    /** Starts Bluetooth device discovery. Updates [scanResults] in real-time. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (scanner == null) {
            scanner = BluetoothScanner(context, adapter)
            scope.launch {
                scanner!!.devices.collect { devices ->
                    _scanResults.value = devices
                }
            }
        }
        scanner!!.startScan()
    }

    /** Stops ongoing Bluetooth scanning. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner?.stopScan()
    }

    // --- Server ---
    private var server: BluetoothServer? = null
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)

    /** StateFlow representing the current server state: [Stopped], [Listening], or [Error]. */
    val serverState: StateFlow<ServerState> = _serverState

    private val _incomingConnections = MutableSharedFlow<BluetoothConnection>(extraBufferCapacity = 16)

    /** SharedFlow of incoming connections accepted by the server. */
    val incomingConnections: SharedFlow<BluetoothConnection> = _incomingConnections

    /** Starts the Bluetooth server to listen for incoming connections. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer() {
        if (server != null) return

        server = BluetoothServer(adapter, serviceUUID = serviceUUID)
        _serverState.value = ServerState.Listening

        scope.launch {
            server!!.state.collect { state -> _serverState.value = state }
        }
        scope.launch {
            server!!.incomingConnections.collect { conn -> _incomingConnections.emit(conn) }
        }

        server!!.start()
    }

    /** Stops the Bluetooth server and clears state. */
    fun stopServer() {
        server?.stop()
        server = null
        _serverState.value = ServerState.Stopped
    }

    // --- Client ---
    private var client: BluetoothClient? = null
    private val _clientState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /** StateFlow representing the current client connection state. */
    val clientState: StateFlow<ConnectionState> = _clientState

    private var _currentConnection: BluetoothConnection? = null

    /** The current active client connection, if any. */
    val currentConnection: BluetoothConnection? get() = _currentConnection

    /**
     * Connects to the specified [device] as a Bluetooth client.
     * Cancels any previous connection.
     *
     * @param device The target Bluetooth device to connect to.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        disconnect() // disconnect if already connected

        client = BluetoothClient(adapter, device, serviceUUID)
        scope.launch {
            client!!.state.collect { state -> _clientState.value = state }
        }

        client!!.connect()
        _currentConnection = client!!.connection
    }

    /** Disconnects the current client connection and resets state. */
    fun disconnect() {
        client?.disconnect()
        client = null
        _currentConnection = null
        _clientState.value = ConnectionState.Disconnected
    }
}
