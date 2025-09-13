package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import android.bluetooth.BluetoothDevice
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothAutoConnectService
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ## BluetoothRepository
 * A repository class that acts as a single source of truth for Bluetooth operations.
 * It coordinates with [BluetoothConnectionService] and [BluetoothAutoConnectService]
 * to provide a simplified API for the ViewModel layer, abstracting away the low-level
 * service logic. It also exposes messages and connection events as Kotlin Flows.
 *
 * @property connectionService The service responsible for managing the Bluetooth connection.
 * @property autoConnectService The service for handling the automatic connection logic.
 */
class BluetoothRepository(
    private val connectionService: BluetoothConnectionService,
    private val autoConnectService: BluetoothAutoConnectService
) {

    /**
     * A coroutine scope for managing the repository's background tasks, primarily
     * for forwarding data streams from the services.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Messages ---
    /**
     * A [MutableSharedFlow] to broadcast incoming messages to observers.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /**
     * A [SharedFlow] to expose the stream of incoming messages publicly.
     */
    val messages: SharedFlow<String> = _messages

    // --- Connection events ---
    /**
     * A [MutableSharedFlow] to broadcast connection-related events.
     */
    private val _connectionEvents = MutableSharedFlow<BluetoothConnectionService.ConnectionEvent>(extraBufferCapacity = 16)
    /**
     * A [SharedFlow] to expose the stream of connection events publicly.
     */
    val connectionEvents: SharedFlow<BluetoothConnectionService.ConnectionEvent> = _connectionEvents

    // --- AutoConnect state ---
    /**
     * Exposes the current state of the auto-connect process as a [StateFlow].
     * This flow is directly sourced from the `autoConnectService`.
     */
    val autoConnectState: StateFlow<BluetoothAutoConnectService.AutoConnectState> = autoConnectService.state

    /**
     * ## init
     * An initializer block to set up the data forwarding from the underlying services.
     * It launches coroutines to collect messages and events from the services and
     * re-emit them through the repository's own flows.
     */
    init {
        // Forward incoming messages from connection service
        scope.launch {
            connectionService.incomingMessages.collect { msg ->
                _messages.emit(msg)
            }
        }

        // Forward connection events from connection service
        scope.launch {
            connectionService.connectionEvents.collect { event ->
                _connectionEvents.emit(event)
            }
        }
    }

    // --- Actions exposed to VM ---
    /**
     * ## startServer
     * Starts the Bluetooth service in server mode.
     */
    fun startServer() = connectionService.startServer()

    /**
     * ## connectToPeer
     * Initiates a connection to a specific Bluetooth peer.
     *
     * @param peer The peer to connect to.
     */
    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) {
        connectionService.connectToPeer(peer)
    }

    /**
     * ## sendMessage
     * Sends a message to all connected peers.
     *
     * @param msg The message string to send.
     * @param excludeId The ID of a peer to exclude from receiving the message.
     */
    fun sendMessage(msg: String, excludeId: String? = null) {
        connectionService.sendMessage(msg, excludeId)
    }

    /**
     * ## startAutoConnect
     * Starts the auto-connection process, providing a list of potential peers.
     *
     * @param peers The list of peers discovered during a Bluetooth scan.
     */
    fun startAutoConnect(peers: List<BluetoothDevice>) {
        autoConnectService.startAutoConnect(peers)
    }

    /**
     * ## stopAll
     * Stops all active Bluetooth connections and the auto-connect process.
     * This ensures a clean shutdown of all communication.
     */
    fun stopAll() {
        // Stop auto-connect first to prevent new connections
        autoConnectService.stop()
        connectionService.stopAll()
    }

    /**
     * ## cleanup
     * Performs a final cleanup, stopping all services and cancelling the repository's coroutine scope.
     * This should be called when the application component (e.g., ViewModel) is no longer needed.
     */
    fun cleanup() {
        stopAll()
        scope.cancel()
    }
}