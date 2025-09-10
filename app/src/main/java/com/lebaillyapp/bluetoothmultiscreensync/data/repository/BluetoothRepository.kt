package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.content.Context
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothAutoConnector
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionManager
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository providing a high-level interface for Bluetooth operations.
 *
 * Encapsulates both [BluetoothConnectionManager] and [BluetoothAutoConnector],
 * allowing clients to send/receive messages, observe connection events,
 * manage auto-connect state, and handle errors.
 *
 * @param context Android context for initializing Bluetooth components.
 */
class BluetoothRepository(context: Context) {

    private val connectionManager = BluetoothConnectionManager(context)
    private val autoConnector = BluetoothAutoConnector(context, connectionManager)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Flow interne pour les messages décodés */
    private val _messages = MutableSharedFlow<BluetoothMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<BluetoothMessage> = _messages

    init {
        // Observe le flux string brut et le transforme en BluetoothMessage
        scope.launch {
            connectionManager.incomingMessages.collect { raw ->
                try {
                    val msg = json.decodeFromString<BluetoothMessage>(raw)
                    // Rebroadcast uniquement si serveur, exclude l'origine
                    if (connectionManager.isServer) {
                        connectionManager.sendMessage(raw, exclude = msg.id)
                    }
                    // Émission du message décodé dans le flow
                    _messages.emit(msg)
                } catch (_: Exception) {
                    // ignore les messages mal formés
                }
            }
        }
    }

    val connectionEvents: SharedFlow<BluetoothConnectionManager.ConnectionEvent> = connectionManager.connectionEvents
    val errors: SharedFlow<Throwable> = connectionManager.errors
    val autoConnectState = autoConnector.state

    fun startAutoConnect() = autoConnector.startAutoConnect()

    fun sendMessage(message: String) {
        scope.launch { connectionManager.sendMessage(message) }
    }

    fun sendMessage(message: BluetoothMessage) {
        scope.launch {
            val raw = json.encodeToString(message)
            connectionManager.sendMessage(raw)
        }
    }

    fun getConnectedClientsCount(): Int = connectionManager.getConnectedClientsCount()

    fun stopAll() {
        autoConnector.stop()
        scope.cancel()
    }
}

