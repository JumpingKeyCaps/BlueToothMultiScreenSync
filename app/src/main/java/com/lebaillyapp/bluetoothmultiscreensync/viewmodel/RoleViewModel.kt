package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
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
 * Handles role selection and Bluetooth Classic operations.
 *
 * ### Responsibilities
 * - **Server role** → starts listening for incoming client connections.
 * - **Client role** → scans for nearby devices and attempts connection on demand.
 * - Maintains observable state for:
 *   - Current role (Server/Client)
 *   - Discovered devices (when scanning as Client)
 *   - Server listening state
 *   - Client connection state
 *
 * ### Pairing Requirement
 * Before any RFCOMM (SPP) connection can be established,
 * devices **must be bonded (paired)**.
 *
 * - If the target device is not already bonded:
 *   - `connectToDevice()` automatically triggers the pairing process
 *     by invoking the hidden `BluetoothDevice.createBond()` API via reflection.
 *   - The ViewModel then waits until pairing completes (`BOND_BONDED`)
 *     or times out (~10s).
 * - If pairing fails or times out, the connection attempt is aborted.
 *
 *  Note: Since `createBond()` is not part of the public API,
 * behavior may vary by manufacturer. As a fallback, the user may need
 * to manually pair devices via system settings.
 *
 * ### Connection Flow
 * 1. **Role selection** → start server (listen) or start scan.
 * 2. **Device discovery (Client)** → list available devices.
 * 3. **Connection attempt**:
 *    - Ensure device is paired (auto-trigger pairing if needed).
 *    - Open RFCOMM socket via repository.
 * 4. **Stop role** → cleanly stop server, scan, or client connection.
 *
 * Designed to keep UI reactive by exposing all relevant state
 * as `StateFlow`, while delegating Bluetooth socket work
 * to the [BluetoothRepository].
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

    init {
        selectRole(Role.Client)// client by dflt
    }
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
        viewModelScope.launch {
            try {
                Log.d("RoleViewModel", "Initial bond state: ${device.bondState}")

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    // Déclencher pairing first
                    val pairingStarted = triggerPairing(device)
                    Log.d("RoleViewModel", "Pairing started: $pairingStarted")

                    if (!pairingStarted) {
                        Log.e("RoleViewModel", "Failed to start pairing")
                        return@launch
                    }

                    // Attendre que le pairing se termine
                    waitForPairingComplete(device)
                    Log.d("RoleViewModel", "Final bond state: ${device.bondState}")
                }

                Log.d("RoleViewModel", "Starting connection to ${device.address}")
                repository.connectToDevice(device)
            } catch (e: Exception) {
                Log.e("RoleViewModel", "Connection failed: ${e.message}")
            }
        }
    }

    /**
     * Triggers the Bluetooth device pairing process.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun triggerPairing(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("createBond")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Waits for the Bluetooth device pairing process to complete.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun waitForPairingComplete(device: BluetoothDevice) {
        var attempts = 0
        while (device.bondState == BluetoothDevice.BOND_BONDING && attempts < 20) { // 10 secondes max
            kotlinx.coroutines.delay(500)
            attempts++
        }

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            throw Exception("Pairing failed or timeout")
        }
    }

    /** Server or Client roles */
    enum class Role { Server, Client }
}