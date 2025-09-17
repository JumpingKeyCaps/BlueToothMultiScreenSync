package com.lebaillyapp.bluetoothmultiscreensync.data.repository
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.BluetoothClient
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.BluetoothConnection
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.BluetoothScanner
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.BluetoothServer
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ## BluetoothRepository
 * Centralise le serveur, le client et le scanner Bluetooth Classic.
 * Expose un état uniforme pour la VM et la UI.
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
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        disconnect() // déconnecte si déjà connecté

        client = BluetoothClient(adapter, device, serviceUUID)
        scope.launch { client!!.state.collect { state -> _clientState.value = state } }

        client!!.connect()
        _currentConnection = client!!.connection
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _currentConnection = null
        _clientState.value = ConnectionState.Disconnected
    }
}