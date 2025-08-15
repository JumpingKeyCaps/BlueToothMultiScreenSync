package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.content.Context
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothAutoConnector
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

    /** Manages raw Bluetooth connections and message I/O */
    private val connectionManager = BluetoothConnectionManager(context)

    /** Handles automatic discovery and connection logic */
    private val autoConnector = BluetoothAutoConnector(context, connectionManager)

    /** JSON serializer for [BluetoothMessage] objects */
    private val json = Json { ignoreUnknownKeys = true }

    /** Coroutine scope for sending messages asynchronously */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** SharedFlow of incoming messages from connected peers */
    val messages: SharedFlow<String> = connectionManager.incomingMessages

    /** SharedFlow of connection events (clients connect/disconnect, server started, etc.) */
    val connectionEvents: SharedFlow<BluetoothConnectionManager.ConnectionEvent> = connectionManager.connectionEvents

    /** SharedFlow of errors encountered during Bluetooth operations */
    val errors: SharedFlow<Throwable> = connectionManager.errors

    /** StateFlow representing the current auto-connect state */
    val autoConnectState = autoConnector.state

    /** Starts the automatic connection process via [BluetoothAutoConnector] */
    fun startAutoConnect() = autoConnector.startAutoConnect()

    /**
     * Sends a string message to all connected peers.
     *
     * @param message The text message to send.
     */
    fun sendMessage(message: String) {
        scope.launch { connectionManager.sendMessage(message) }
    }

    /**
     * Sends a [BluetoothMessage] object to all connected peers.
     *
     * The object is serialized to JSON before sending.
     *
     * @param message The [BluetoothMessage] to send.
     */
    fun sendMessage(message: BluetoothMessage) {
        scope.launch { connectionManager.sendMessage(json.encodeToString(message)) }
    }

    /**
     * Returns the number of currently connected clients (only relevant in server mode).
     *
     * @return Number of connected client devices.
     */
    fun getConnectedClientsCount(): Int = connectionManager.getConnectedClientsCount()

    /**
     * Stops all Bluetooth activities.
     *
     * Cancels auto-connect, closes connections, and cancels internal coroutines.
     */
    fun stopAll() {
        autoConnector.stop()
        scope.cancel()
    }
}

