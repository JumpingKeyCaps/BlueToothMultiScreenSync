package com.lebaillyapp.bluetoothmultiscreensync.data.service.old

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/**
 * Handles automatic Bluetooth connection logic.
 *
 * Scans for nearby devices, tests for our custom service,
 * and decides whether to connect as a client or start server mode.
 *
 * @param context Android context used for Bluetooth operations.
 * @param connectionManager Instance of [BluetoothConnectionManager] used for actual connections.
 */
class BluetoothAutoConnector(
    private val context: Context,
    private val connectionManager: BluetoothConnectionManager
) {
    /** Coroutine scope for async operations */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Bluetooth adapter retrieved from system service */
    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    /** Internal state flow representing auto-connect state */
    private val _state = MutableStateFlow<AutoConnectState>(AutoConnectState.Idle)

    /** Internal state flow of currently discovered devices */
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    /** Public read-only state of auto-connect state */
    val state = _state.asStateFlow()

    /** Public read-only state of discovered devices */
    val discoveredDevices = _discoveredDevices.asStateFlow()

    /** List of devices that might expose our service */
    private val potentialServers = mutableListOf<BluetoothDevice>()

    /** Job reference for ongoing scan */
    private var scanJob: Job? = null

    /**
     * Start the automatic connection process.
     *
     * Scans for nearby devices, tests them for our custom service,
     * and connects as a client or starts server mode if no server is found.
     */
    fun startAutoConnect() {
        if (!hasBluetoothPermissions()) {
            _state.value = AutoConnectState.Error("Missing Bluetooth permissions")
            return
        }
        if (adapter == null || adapter?.isEnabled != true) {
            _state.value = AutoConnectState.Error("Bluetooth disabled or not available")
            return
        }

        scope.launch {
            _state.value = AutoConnectState.Scanning
            potentialServers.clear()

            // Scan for devices
            scanForDevices(15000)

            // Check for devices exposing our service
            val serverDevice = findDeviceWithOurService()

            if (serverDevice != null) {
                connectToServer(serverDevice)
            } else {
                startServerMode()
            }
        }
    }

    /**
     * Scan for discoverable Bluetooth devices.
     *
     * @param timeoutMs Timeout in milliseconds to stop discovery automatically.
     */
    private suspend fun scanForDevices(timeoutMs: Long) = suspendCancellableCoroutine<Unit> { cont ->
        potentialServers.clear()
        Log.d("BTScan", "=== scanForDevices started (timeout=$timeoutMs) ===")

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        val receiver = object : BroadcastReceiver() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        if (device != null) {
                            Log.d( "BTScan","FOUND device: name=${device.name}, addr=${device.address}, bondState=${device.bondState}")
                            potentialServers.add(device)
                            _discoveredDevices.value = potentialServers.toList()
                        } else {
                            Log.w("BTScan", "FOUND null device (WTF)")
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d("BTScan", "=== Discovery finished, found=${potentialServers.size} devices ===")
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }

        try {
            Log.d("BTScan", "Registering receiver + starting discovery…")
            context.registerReceiver(receiver, filter)
            val started = adapter?.startDiscovery() ?: false
            Log.d("BTScan", "startDiscovery() returned $started")
        } catch (e: SecurityException) {
            Log.e("BTScan", "Permission denied during discovery: ${e.message}", e)
            _state.value = AutoConnectState.Error("Permission denied during discovery")
            if (cont.isActive) cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        // Safety timeout
        scope.launch {
            delay(timeoutMs)
            Log.w("BTScan", "Timeout ($timeoutMs ms) reached, stopping discovery manually")
            stopDiscoverySafe()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            if (cont.isActive) cont.resume(Unit)
        }
    }

    /**
     * Tests each discovered device to see if it exposes our custom service.
     *
     * @return The first device exposing our service, or null if none found.
     */
    private suspend fun findDeviceWithOurService(): BluetoothDevice? {
        _state.value = AutoConnectState.TestingServices

        for (device in potentialServers) {
            try {
                if (connectionManager.hasOurService(device)) {
                    return device
                }
            } catch (e: Exception) {
                continue
            }
            delay(500)
        }
        return null
    }

    /**
     * Connects to a specific server device.
     *
     * Updates state accordingly and falls back to server mode if permission is missing.
     *
     * @param device The Bluetooth device to connect to.
     */
    fun connectToServer(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) {
            _state.value = AutoConnectState.Error("Missing BLUETOOTH_CONNECT permission")
            startServerMode()
            return
        }

        _state.value = AutoConnectState.Connecting(device)
        scope.launch {
            try {
                connectionManager.connectToServer(device)
            } catch (e: SecurityException) {
                _state.value = AutoConnectState.Error("Permission denied during connect")
                startServerMode()
            }
        }
    }

    /** Starts server mode, making this device the master. */
    private fun startServerMode() {
        _state.value = AutoConnectState.ServerMode
        try {
            if (hasBluetoothConnectPermission()) {
                connectionManager.startServer()
            } else {
                _state.value = AutoConnectState.Error("Missing BLUETOOTH_CONNECT permission")
            }
        } catch (e: SecurityException) {
            _state.value = AutoConnectState.Error("Permission denied starting server")
        }
    }

    /** Connect manually to a specific device (UI use). */
    fun connectToDevice(device: BluetoothDevice) {
        connectToServer(device)
    }

    /** Force server mode manually. */
    fun forceServerMode() {
        startServerMode()
    }

    /** Stops ongoing discovery safely. */
    private fun stopDiscoverySafe() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
    }

    /** Checks required Bluetooth permissions for scanning and connecting. */
    private fun hasBluetoothPermissions(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            perms.clear()
            perms.add(Manifest.permission.BLUETOOTH)
            perms.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    /** Checks BLUETOOTH_CONNECT permission specifically. */
    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true

    /** Stops auto-connect, cancels scan and cleans up resources. */
    fun stop() {
        scanJob?.cancel()
        stopDiscoverySafe()
        scope.cancel()
        connectionManager.onDestroy()
        _state.value = AutoConnectState.Idle
        _discoveredDevices.value = emptyList()
    }

    /**
     * Represents the current state of the auto-connect process.
     */
    sealed class AutoConnectState {
        /** Idle state, no active operation */
        object Idle : AutoConnectState()

        /** Currently scanning for devices */
        object Scanning : AutoConnectState()

        /** Testing discovered devices for our service */
        object TestingServices : AutoConnectState()

        /** Connecting to a specific device */
        data class Connecting(val device: BluetoothDevice) : AutoConnectState()

        /** Acting as server/master */
        object ServerMode : AutoConnectState()

        /** Error occurred with description */
        data class Error(val reason: String) : AutoConnectState()
    }
}
