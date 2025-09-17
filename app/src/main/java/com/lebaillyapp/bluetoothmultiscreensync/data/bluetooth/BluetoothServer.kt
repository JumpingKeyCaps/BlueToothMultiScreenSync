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
 * Classe instanciable pour écouter les connexions entrantes via Bluetooth Classic (SPP/RFCOMM).
 * Expose un Flow de connexions entrantes (`BluetoothConnection`) et l'état du serveur.
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
    val incomingConnections: SharedFlow<BluetoothConnection> = _incomingConnections

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state

    /**
     * Démarre l'écoute pour les connexions entrantes.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun start() {
        if (listeningJob != null) return // déjà en écoute

        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(serviceName, serviceUUID)
            _state.tryEmit(ServerState.Listening)
            // ou _state.value = ServerState.Listening

            listeningJob = scope.launch {
                while (isActive) {
                    try {
                        val socket: BluetoothSocket = serverSocket?.accept() ?: break
                        // Wrap dans notre BluetoothConnection
                        val connection = BluetoothConnection(socket, scope)
                        _incomingConnections.emit(connection)
                    } catch (e: IOException) {
                        _state.emit(ServerState.Error(e))
                        // ou _state.value = ServerState.Error(e)
                        break
                    }
                }
            }
        } catch (e: IOException) {
            _state.tryEmit(ServerState.Error(e))
         //ou   _state.value = ServerState.Error(e)
        }
    }

    /**
     * Arrête l'écoute et ferme le serveur.
     */
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {}
        serverSocket = null
        _state.tryEmit(ServerState.Stopped)
      // ou  _state.value = ServerState.Stopped
    }


}