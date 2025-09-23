package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import androidx.lifecycle.ViewModel
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.flow.StateFlow

/**
 * ## PlaygroundViewModel
 *
 * ViewModel for the Playground screen.
 * Provides access to the current Bluetooth server and client states, as well as incoming connections.
 *
 * This ViewModel **reuses the existing [BluetoothRepository]** so that ongoing connections
 * are preserved when navigating from the RoleSelection screen.
 *
 * @property repository The shared [BluetoothRepository] handling Bluetooth Classic operations.
 */
class PlaygroundViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {

    /** Current server state (Master) */
    val serverState: StateFlow<ServerState> = repository.serverState

    /** Current client state (Slave) */
    val clientState: StateFlow<ConnectionState> = repository.clientState

    /** SharedFlow of incoming connections from the server */
    val incomingConnections = repository.incomingConnections
}
