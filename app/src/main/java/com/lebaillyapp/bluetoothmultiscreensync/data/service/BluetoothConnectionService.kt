package com.lebaillyapp.bluetoothmultiscreensync.data.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ## BluetoothConnectionService
 * A service class for managing Bluetooth connections, allowing for peer-to-peer communication
 * in either a client or server role. It handles incoming and outgoing messages and
 * connection events using Kotlin Flow for a reactive data stream.
 */
class BluetoothConnectionService {

    /**
     * The coroutine scope for managing all coroutines within this service.
     * It uses an IO dispatcher for network operations and a SupervisorJob
     * to ensure child coroutines don't cancel others upon failure.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * A [MutableSharedFlow] to emit incoming messages from connected peers.
     * It has an extra buffer capacity of 64 to handle bursts of messages.
     */
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /**
     * A [SharedFlow] to expose incoming messages publicly.
     * External components can collect from this flow to receive messages.
     */
    val incomingMessages: SharedFlow<String> = _incomingMessages

    /**
     * A [MutableSharedFlow] to emit connection-related events.
     * It has an extra buffer capacity of 16 for managing connection status updates.
     */
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    /**
     * A [SharedFlow] to expose connection events publicly.
     * External components can collect from this flow to react to connection status changes.
     */
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    /**
     * A thread-safe queue to store all active [PeerConnection] objects.
     */
    private val peers = ConcurrentLinkedQueue<PeerConnection>()
    /**
     * A boolean flag indicating whether the service is operating in server mode.
     */
    private var serverMode = false
    /**
     * Public getter to check the current mode of the service.
     */
    val isServer: Boolean get() = serverMode

    /**
     * ## startServer
     * Starts the service in server mode. This allows other devices to connect as clients.
     * A [ConnectionEvent.ServerStarted] event is emitted to notify listeners.
     */
    fun startServer() {
        serverMode = true
        _connectionEvents.tryEmit(ConnectionEvent.ServerStarted)
    }

    /**
     * ## connectToPeer
     * Connects to a peer, setting the service to client mode.
     * The new peer is added to the list, a [ConnectionEvent.ConnectedToServer] event is emitted,
     * and a coroutine is launched to listen for incoming messages from this peer.
     *
     * @param peer The [PeerConnection] object representing the remote device.
     */
    fun connectToPeer(peer: PeerConnection) {
        serverMode = false
        peers.add(peer)
        _connectionEvents.tryEmit(ConnectionEvent.ConnectedToServer)
        listenForMessages(peer)
    }

    /**
     * ## sendMessage
     * Sends a message to all connected peers. This operation is asynchronous.
     *
     * @param message The string message to be sent.
     * @param excludeId The ID of a peer to exclude from receiving this message.
     * This is useful for server-side message forwarding.
     */
    fun sendMessage(message: String, excludeId: String? = null) {
        scope.launch {
            peers.forEach { peer ->
                if (peer.id != excludeId) peer.send(message)
            }
        }
    }

    /**
     * ## listenForMessages
     * A private function that starts a coroutine to continuously listen for messages
     * from a specific peer.
     *
     * @param peer The [PeerConnection] to listen to.
     */
    private fun listenForMessages(peer: PeerConnection) {
        scope.launch {
            peer.messages.collect { msg ->
                _incomingMessages.emit(msg)
                // If in server mode, forward the message to all other connected clients
                if (isServer) sendMessage(msg, excludeId = peer.id)
            }
        }
    }


    /**
     * ## removePeer
     * Removes a peer from the list of active peers.
     */
    fun removePeer(peer: PeerConnection) {
        peer.close()
        peers.remove(peer)
        _connectionEvents.tryEmit(ConnectionEvent.ClientDisconnected(peer.id))
    }



    /**
     * ## stopAll
     * Stops all active connections by closing each peer connection and clearing the list.
     * This resets the service's state.
     */
    fun stopAll() {
        peers.forEach { removePeer(it) }
        peers.clear()
        serverMode = false
    }




    /**
     * # PeerConnection
     * An abstract base class representing a single peer connection.
     * Concrete implementations will handle the specific communication protocol (e.g., Bluetooth socket).
     *
     * @param id A unique identifier for the peer.
     * @param messages A [SharedFlow] that emits incoming messages from this specific peer.
     */
    abstract class PeerConnection(
        val id: String,
        val messages: SharedFlow<String>
    ) {
        /**
         * ## send
         * Suspends the current coroutine to send a message to the peer.
         * Must be implemented by subclasses.
         *
         * @param message The string message to send.
         */
        abstract suspend fun send(message: String)
        /**
         * ## close
         * Closes the connection to the peer.
         * Must be implemented by subclasses.
         */
        abstract fun close()
    }



    /**
     * # ConnectionEvent
     * A sealed class representing different types of connection events.
     * This provides a type-safe way to handle various state changes.
     */
    sealed class ConnectionEvent {
        /**
         * Indicates that the service has successfully started in server mode.
         */
        object ServerStarted : ConnectionEvent()
        /**
         * Indicates that the service has successfully connected to a server as a client.
         */
        object ConnectedToServer : ConnectionEvent()
        /**
         * Indicates that a client has disconnected from the server.
         *
         * @param peerId The ID of the peer that disconnected.
         */
        data class ClientDisconnected(val peerId: String) : ConnectionEvent()
    }
}