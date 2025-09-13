package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.UUID

/**
 * ## BluetoothAutoConnectService
 * A service for automatically managing Bluetooth connection attempts.
 * This class decides whether to act as a server or a client based on the availability of
 * nearby peers, and it manages the connection state.
 *
 * @property connectionService An instance of [BluetoothConnectionService] used for
 * handling the actual Bluetooth communication.
 */
class BluetoothAutoConnectService(
    private val connectionService: BluetoothConnectionService
) {

    /**
     * The coroutine scope for managing background tasks like connection attempts.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /**
     * A [MutableStateFlow] representing the current state of the auto-connect process.
     * It provides a stream of state updates that can be observed.
     */
    private val _state = MutableStateFlow<AutoConnectState>(AutoConnectState.Idle)
    /**
     * A [StateFlow] to publicly expose the current state of the service.
     */
    val state: StateFlow<AutoConnectState> = _state

    /**
     * A mutable list to store the Bluetooth devices discovered during a scan.
     */
    private val potentialDevices = mutableListOf<BluetoothDevice>()

    /**
     * ## startAutoConnect
     * Initiates the auto-connection process.
     * This function is the entry point, typically called after a Bluetooth scan.
     * It decides whether to connect to an existing peer or start in server mode.
     *
     * @param devices The list of discovered Bluetooth devices.
     */
    fun startAutoConnect(devices: List<BluetoothDevice>) {
        potentialDevices.clear()
        potentialDevices.addAll(devices)

        // If no peers are found, start in server mode to allow others to connect.
        // Otherwise, attempt to connect to the first available peer.
        if (devices.isEmpty()) {
            startServerMode()
        } else {
            // C'est ici que la conversion a lieu, au moment précis où on a besoin du PeerConnection
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //
                //todo filtrage des server ici !
                val deviceToConnect = devices.first() // to remove

                // Création du socket et du RealPeerConnection
                val socket = deviceToConnect.createRfcommSocketToServiceRecord(uuid)
                val peer = RealPeerConnection(socket)

                // Appel à la méthode de connexion avec l'objet PeerConnection
                connectToServer(peer)
            } catch (e: IOException) {
                _state.value = AutoConnectState.Error("Failed to create socket or connect: ${e.message}")
                stop() // Arrêter la tentative en cas d'erreur
            }
        }
    }

    /**
     * ## ConnectToServer
     * A private function to attempt a connection to a specific peer.
     * It updates the state to [AutoConnectState.Connecting] and delegates the
     * connection logic to the [connectionService].
     *
     * @param peer The peer to connect to.
     */
    private fun connectToServer(peer: BluetoothConnectionService.PeerConnection) {
        _state.value = AutoConnectState.Connecting(peer)
        connectionService.connectToPeer(peer)
    }

    /**
     * ## StartServerMode
     * A private function to switch the service into server mode.
     * It updates the state to [AutoConnectState.ServerMode] and starts the server
     * via the [connectionService].
     */
    private fun startServerMode() {
        _state.value = AutoConnectState.ServerMode
        connectionService.startServer()
    }

    /**
     * ## Stop
     * Stops the auto-connection process and all active connections.
     * It resets the state to [AutoConnectState.Idle] and calls the stop method on the
     * underlying [connectionService].
     */
    fun stop() {
        _state.value = AutoConnectState.Idle
        connectionService.stopAll()
    }




    /**
     * # AutoConnectState
     * A sealed class representing the different states of the auto-connection process.
     * This makes it easy to handle each state explicitly in the UI or business logic.
     */
    sealed class AutoConnectState {
        /**
         * The initial state, when no connection process is active.
         */
        object Idle : AutoConnectState()
        /**
         * The service is currently attempting to connect to a specific peer.
         *
         * @param peer The peer being connected to.
         */
        data class Connecting(val peer: BluetoothConnectionService.PeerConnection) : AutoConnectState()
        /**
         * The service is in server mode, waiting for a client to connect.
         */
        object ServerMode : AutoConnectState()
        /**
         * An error occurred during the auto-connection process.
         *
         * @param reason A descriptive string of the error.
         */
        data class Error(val reason: String) : AutoConnectState()
    }
}