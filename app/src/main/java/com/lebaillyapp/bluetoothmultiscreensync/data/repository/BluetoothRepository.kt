package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth.*
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.DeviceInfo
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ServerState
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
 * - Managing network device list synchronization between master and slaves.
 *
 * ### Exposed Flows:
 * - [scanResults]: list of discovered devices during scan.
 * - [serverState]: current server state.
 * - [incomingConnections]: connections accepted by the server.
 * - [incomingMessages]: all messages received from any client or server connection.
 * - [clientState]: current client connection state.
 * - [currentConnection]: active client connection (if any).
 * - [networkDevices]: synchronized list of all devices in the network (master + slaves).
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

    // --- Network Devices ---
    private val _networkDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val networkDevices: StateFlow<List<DeviceInfo>> = _networkDevices.asStateFlow()

    init {
        // Observer les connexions entrantes pour mettre à jour la liste réseau (MASTER)
        scope.launch {
            incomingConnections.collect {
                observeConnections()
              //  updateNetworkList()
            }
        }

        // Observer les messages pour recevoir les updates réseau (SLAVE)
        scope.launch {
            incomingMessages.collect { message ->
                parseNetworkUpdate(message)
            }
        }
    }

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
        observeConnections()
    }

    /**
     * ### Stops the Bluetooth server and clears state.
     */
    fun stopServer() {
        server?.stop()
        server = null
        _serverState.value = ServerState.Stopped
        _networkDevices.value = emptyList()
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
        // Ne pas envoyer si c'est un message système
        if (message.startsWith("NETWORK_UPDATE|")) return

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
        _networkDevices.value = emptyList()
    }

    /**
     * ### Updates the network device list and broadcasts it to all slaves (MASTER only).
     * Called automatically when a new connection is established.
     */
    @SuppressLint("MissingPermission")
    private fun updateNetworkList() {
        val currentServer = server ?: return  // smart cast OK, immuable maintenant

        val devices = mutableListOf<DeviceInfo>()

        // Ajouter le master (moi-même)
        devices.add(DeviceInfo(adapter.name ?: "Master", adapter.address, isMaster = true))

        // Ajouter les slaves uniques
        currentServer.getConnectionsSnapshot().forEach { conn ->
            if (devices.none { it.address == conn.remoteDevice.address }) {
                devices.add(
                    DeviceInfo(
                        name = conn.remoteDevice.name ?: "Unknown",
                        address = conn.remoteDevice.address,
                        isMaster = false
                    )
                )
            }
        }

        _networkDevices.value = devices

        // Broadcast network update
        val networkMsg = "NETWORK_UPDATE|" + devices.joinToString("|") {
            "${it.name},${it.address},${it.isMaster}"
        }

        scope.launch {
            currentServer.getConnectionsSnapshot().forEach { conn ->
                try { conn.sendMessage(networkMsg) } catch (_: IOException) {}
            }
        }
    }

    private fun observeConnections() {
        server?.getConnectionsSnapshot()?.forEach { conn ->
            scope.launch {
                conn.connectionState.collect { state ->
                    if (state == ConnectionState.Disconnected || state is ConnectionState.Error) {
                        updateNetworkList() // recalculer la liste sans ce device
                    }
                }
            }
        }
    }



    /**
     * ### Parses incoming network update messages (SLAVE only).
     * Updates the local network device list when receiving updates from the master.
     */
    private fun parseNetworkUpdate(message: String) {
        if (!message.startsWith("NETWORK_UPDATE|")) return

        try {
            val parts = message.removePrefix("NETWORK_UPDATE|").split("|")
            val devices = parts.map { part ->
                val (name, addr, isMaster) = part.split(",")
                DeviceInfo(name, addr, isMaster.toBoolean())
            }
            _networkDevices.value = devices
        } catch (_: Exception) {
            // Pas un message réseau valide, ignorer
        }
    }
}