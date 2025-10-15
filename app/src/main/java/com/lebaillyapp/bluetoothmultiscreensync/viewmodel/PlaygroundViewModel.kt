package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import androidx.lifecycle.ViewModel
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.DeviceInfo
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ServerState
import kotlinx.coroutines.flow.SharedFlow
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

    /** SharedFlow of all incoming messages from any connected device */
    val incomingMessages: SharedFlow<String> = repository.incomingMessages


    val networkDevices: StateFlow<List<DeviceInfo>> = repository.networkDevices

    /**
     * Sends a message to connected devices
     * - If Server: sends to all connected clients
     * - If Client: sends to the server + reBroadcasting to other clients by the server (excludes sender)
     */
    fun sendMessage(message: String) {
        repository.sendMessage(message)
    }
}
