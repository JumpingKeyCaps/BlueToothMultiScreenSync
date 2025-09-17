package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.*

/**
 * ## BluetoothClient
 *
 * A client class for connecting to a Bluetooth Classic server (SPP/RFCOMM).
 * This class handles establishing a connection to a remote Bluetooth device,
 * maintaining connection state, and exposing the underlying [BluetoothConnection]
 * for sending and receiving messages.
 *
 * ### Features:
 * - Connects to a Bluetooth server using a provided device and UUID.
 * - Exposes the connection state via a [StateFlow] of [ConnectionState].
 * - Provides access to the [BluetoothConnection] for message exchange.
 * - Handles proper cleanup and disconnection.
 *
 * @property adapter The [BluetoothAdapter] used to manage Bluetooth operations.
 * @property device The remote [BluetoothDevice] to connect to.
 * @property serviceUUID The UUID representing the service on the server side.
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
    /**
     * A [StateFlow] representing the current connection state.
     * - [ConnectionState.Disconnected] → not connected
     * - [ConnectionState.Connecting] → attempting to connect
     * - [ConnectionState.Connected] → successfully connected
     * - [ConnectionState.Error] → connection failed or lost
     */
    val state: StateFlow<ConnectionState> = _state

    private var _connection: BluetoothConnection? = null
    /**
     * The active [BluetoothConnection] instance if connected; null otherwise.
     * Use this object to send and receive messages.
     */
    val connection: BluetoothConnection?
        get() = _connection

    /**
     * Starts an attempt to connect to the remote Bluetooth server.
     *
     * Requires [Manifest.permission.BLUETOOTH_CONNECT] at runtime.
     *
     * Launches a coroutine on [Dispatchers.IO] to handle the connection asynchronously.
     * If the connection succeeds, updates [state] to [ConnectionState.Connected]
     * and initializes [connection]. If it fails, updates [state] to [ConnectionState.Error].
     *
     * This method is idempotent: calling it while a connection attempt is in progress
     * has no effect.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        if (connectionJob != null) return // already connecting

        _state.value = ConnectionState.Connecting

        connectionJob = scope.launch {
            try {
                socket = device.createRfcommSocketToServiceRecord(serviceUUID)
                try {
                    adapter.cancelDiscovery() // Stop discovery before connecting
                } catch (e: SecurityException) {
                    // Ignore if permission is not granted
                }

                socket?.connect()

                val btConnection = BluetoothConnection(socket!!, scope)
                _connection = btConnection
                _state.value = ConnectionState.Connected
            } catch (e: IOException) {
                _state.value = ConnectionState.Error(e)
                try { socket?.close() } catch (_: IOException) {}
            }
        }
    }

    /**
     * Disconnects the client cleanly.
     *
     * - Cancels any ongoing connection attempt.
     * - Closes the Bluetooth socket.
     * - Nullifies the [connection] reference.
     * - Updates [state] to [ConnectionState.Disconnected].
     * - Cancels the internal coroutine scope to release resources.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null

        try { socket?.close() } catch (_: IOException) {}
        socket = null
        _connection = null
        _state.value = ConnectionState.Disconnected
        scope.cancel()
    }
}