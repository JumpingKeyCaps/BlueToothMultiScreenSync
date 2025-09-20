package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ## RoleViewModel
 *
 * Manages the role selection screen logic: Client or Server.
 * Exposes flows for server state, scanned devices, and the currently selected role.
 *
 * ### Responsibilities:
 * - Handle role selection (Server / Client)
 * - Start / stop the Bluetooth server or scan accordingly
 * - Expose discovered devices for client
 */
class RoleViewModel(
    private val repository: BluetoothRepository,
    private val bluetoothAdapter: BluetoothAdapter
) : ViewModel() {

    /** Selected role: null = not chosen yet, Server or Client */
    private val _role = MutableStateFlow<Role?>(null)
    val role: StateFlow<Role?> = _role.asStateFlow()

    /** Devices discovered when acting as a client */
    val scannedDevices: StateFlow<List<BluetoothDevice>> = repository.scanResults

    /** Current server state when acting as a server */
    val serverState: StateFlow<ServerState> = repository.serverState

    /** Current client connection state */
    val clientState: StateFlow<com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState> =
        repository.clientState.stateIn(viewModelScope, SharingStarted.Lazily, repository.clientState.value)

    /**
     * Selects a role and launches the corresponding Bluetooth operations.
     *
     * - Server → start server
     * - Client → start scanning
     */
    @Suppress("MissingPermission")
    fun selectRole(selectedRole: Role) {
        _role.value = selectedRole
        when (selectedRole) {
            Role.Server -> repository.startServer()
            Role.Client -> repository.startScan()
        }
    }

    /** Stops the current role operations (server / scan) */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopRole() {
        when (_role.value) {
            Role.Server -> repository.stopServer()
            Role.Client -> repository.stopScan()
            else -> {}
        }
    }

    /** Server or Client roles */
    enum class Role { Server, Client }
}