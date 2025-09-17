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
 * Classe instanciable pour se connecter à un serveur Bluetooth Classic (SPP/RFCOMM).
 * Expose l'état de connexion et le flux de messages entrants via [BluetoothConnection].
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
    val state: StateFlow<ConnectionState> = _state

    private var _connection: BluetoothConnection? = null
    val connection: BluetoothConnection?
        get() = _connection

    /**
     * Démarre la tentative de connexion au serveur.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        if (connectionJob != null) return // déjà en cours

        _state.value = ConnectionState.Connecting

        connectionJob = scope.launch {
            try {
                socket = device.createRfcommSocketToServiceRecord(serviceUUID)
                try {
                    adapter.cancelDiscovery() // stop discovery avant connect
                } catch (e: SecurityException) {
                    // permission non accordée → ignorer
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
     * Déconnecte proprement.
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