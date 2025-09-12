package com.lebaillyapp.bluetoothmultiscreensync.data.repository

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
 * Repository that bridges Bluetooth services to the ViewModel.
 * Does NOT access Android framework.
 */
class BluetoothRepository(
    private val connectionService: BluetoothConnectionService,
    private val autoConnectService: BluetoothAutoConnectService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Messages ---
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages

    // --- Connection events ---
    private val _connectionEvents = MutableSharedFlow<BluetoothConnectionService.ConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<BluetoothConnectionService.ConnectionEvent> = _connectionEvents

    // --- AutoConnect state ---
    val autoConnectState: StateFlow<BluetoothAutoConnectService.AutoConnectState> = autoConnectService.state

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
    fun startServer() = connectionService.startServer()

    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) {
        connectionService.connectToPeer(peer)
    }

    fun sendMessage(msg: String, excludeId: String? = null) {
        connectionService.sendMessage(msg, excludeId)
    }

    fun startAutoConnect(peers: List<BluetoothConnectionService.PeerConnection>) {
        autoConnectService.startAutoConnect(peers)
    }

    fun stopAll() {
        // Stop auto-connect first to prevent new connections
        autoConnectService.stop()
        connectionService.stopAll()
    }

    fun cleanup() {
        stopAll()
        scope.cancel()
    }
}

