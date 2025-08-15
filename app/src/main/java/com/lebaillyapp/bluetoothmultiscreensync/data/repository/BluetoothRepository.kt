package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionManager
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BluetoothRepository(context: Context) {

    private val connectionManager = BluetoothConnectionManager(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val messages: SharedFlow<String> = connectionManager.incomingMessages
    val connectionEvents: SharedFlow<BluetoothConnectionManager.ConnectionEvent> = connectionManager.connectionEvents
    val errors: SharedFlow<Throwable> = connectionManager.errors

    fun startServer() = connectionManager.startServer()

    fun connectToServer(device: BluetoothDevice) = connectionManager.connectToServer(device)

    /** Envoie un message texte simple */
    fun sendMessage(message: String) {
        scope.launch { connectionManager.sendMessage(message) }
    }

    /** Envoie un message sérialisé JSON */
    fun sendMessage(message: BluetoothMessage) {
        scope.launch { connectionManager.sendMessage(json.encodeToString(message)) }
    }

    fun getConnectedClientsCount(): Int = connectionManager.getConnectedClientsCount()

    fun stopAll() {
        connectionManager.onDestroy()
        scope.cancel()
    }
}