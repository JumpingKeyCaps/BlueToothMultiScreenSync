package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ## RoleViewModel
 *
 * Handles role selection and Bluetooth operations.
 * - Server → starts listening
 * - Client → scans devices and connects on demand
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
    val clientState: StateFlow<ConnectionState> =
        repository.clientState.stateIn(viewModelScope, SharingStarted.Lazily, repository.clientState.value)

    /** The current active client connection, if any */
    val currentConnection get() = repository.currentConnection

    /**
     * Selects a role and launches the corresponding Bluetooth operations.
     */
    @Suppress("MissingPermission")
    fun selectRole(selectedRole: Role) {
        _role.value = selectedRole
        when (selectedRole) {
            Role.Server -> repository.startServer()
            Role.Client -> repository.startScan()
        }
    }

    /** Stops the current role operations (server / scan / disconnect client) */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopRole() {
        when (_role.value) {
            Role.Server -> repository.stopServer()
            Role.Client -> {
                repository.stopScan()
                repository.disconnect()
            }
            else -> {}
        }
    }

    /**
     * Connects to a Bluetooth device as client.
     * Cancels any existing client connection.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        repository.connectToDevice(device)
    }

    /** Server or Client roles */
    enum class Role { Server, Client }
}