package com.lebaillyapp.bluetoothmultiscreensync.data.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages Bluetooth connections abstractly (server/client)
 */
class BluetoothConnectionService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    private val peers = ConcurrentLinkedQueue<PeerConnection>()
    private var serverMode = false
    val isServer: Boolean get() = serverMode

    fun startServer() {
        serverMode = true
        _connectionEvents.tryEmit(ConnectionEvent.ServerStarted)
    }

    fun connectToPeer(peer: PeerConnection) {
        serverMode = false
        peers.add(peer)
        _connectionEvents.tryEmit(ConnectionEvent.ConnectedToServer)
        listenForMessages(peer)
    }

    fun sendMessage(message: String, excludeId: String? = null) {
        scope.launch {
            peers.forEach { peer ->
                if (peer.id != excludeId) peer.send(message)
            }
        }
    }

    private fun listenForMessages(peer: PeerConnection) {
        scope.launch {
            peer.messages.collect { msg ->
                _incomingMessages.emit(msg)
                if (isServer) sendMessage(msg, excludeId = peer.id)
            }
        }
    }

    fun stopAll() {
        peers.forEach { it.close() }
        peers.clear()
        serverMode = false
    }

    abstract class PeerConnection(
        val id: String,
        val messages: SharedFlow<String>
    ) {
        abstract suspend fun send(message: String)
        abstract fun close()
    }

    sealed class ConnectionEvent {
        object ServerStarted : ConnectionEvent()
        object ConnectedToServer : ConnectionEvent()
        data class ClientDisconnected(val peerId: String) : ConnectionEvent()
    }
}