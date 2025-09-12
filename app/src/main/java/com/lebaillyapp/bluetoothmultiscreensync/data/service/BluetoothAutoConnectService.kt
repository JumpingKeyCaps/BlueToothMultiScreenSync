package com.lebaillyapp.bluetoothmultiscreensync.data.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides automatically whether to start server or connect to a peer.
 * Pure Kotlin, no Android dependencies.
 */
class BluetoothAutoConnectService(
    private val connectionService: BluetoothConnectionService
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow<AutoConnectState>(AutoConnectState.Idle)
    val state: StateFlow<AutoConnectState> = _state

    private val potentialPeers = mutableListOf<BluetoothConnectionService.PeerConnection>()

    // Ready to plug: le VM fournit la liste de peers trouvés via scan Android
    fun startAutoConnect(peers: List<BluetoothConnectionService.PeerConnection>) {
        potentialPeers.clear()
        potentialPeers.addAll(peers)

        if (peers.isEmpty()) startServerMode()
        else connectToServer(peers.first())
    }

    private fun connectToServer(peer: BluetoothConnectionService.PeerConnection) {
        _state.value = AutoConnectState.Connecting(peer)
        connectionService.connectToPeer(peer)
    }

    private fun startServerMode() {
        _state.value = AutoConnectState.ServerMode
        connectionService.startServer()
    }

    fun stop() {
        _state.value = AutoConnectState.Idle
        connectionService.stopAll()
    }

    sealed class AutoConnectState {
        object Idle : AutoConnectState()
        data class Connecting(val peer: BluetoothConnectionService.PeerConnection) : AutoConnectState()
        object ServerMode : AutoConnectState()
        data class Error(val reason: String) : AutoConnectState()
    }
}