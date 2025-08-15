package com.lebaillyapp.bluetoothmultiscreensync.data.service

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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

class BluetoothAutoConnector(
    private val context: Context,
    private val connectionManager: BluetoothConnectionManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _state = MutableStateFlow<AutoConnectState>(AutoConnectState.Idle)
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    val state = _state.asStateFlow()
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val potentialServers = mutableListOf<BluetoothDevice>()
    private var scanJob: Job? = null

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

            // 1. Scan des devices découvrables
            scanForDevices(8000) // 8 secondes

            // 2. Test des services sur les devices trouvés
            val serverDevice = findDeviceWithOurService()

            if (serverDevice != null) {
                connectToServer(serverDevice)
            } else {
                startServerMode()
            }
        }
    }

    /** Scan général pour découvrir tous les devices */
    private suspend fun scanForDevices(timeoutMs: Long) = suspendCancellableCoroutine<Unit> { cont ->
        potentialServers.clear()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let { potentialServers.add(it) }
                        _discoveredDevices.value = potentialServers.toList()
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, filter)
            adapter?.startDiscovery()
        } catch (e: SecurityException) {
            _state.value = AutoConnectState.Error("Permission denied during discovery")
            if (cont.isActive) cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        // Timeout de sécurité
        scope.launch {
            delay(timeoutMs)
            stopDiscoverySafe()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            if (cont.isActive) cont.resume(Unit)
        }
    }

    /** Tester chaque device découvert pour voir s'il expose notre service */
    private suspend fun findDeviceWithOurService(): BluetoothDevice? {
        _state.value = AutoConnectState.TestingServices

        for (device in potentialServers) {
            try {
                if (connectionManager.hasOurService(device)) {
                    return device
                }
            } catch (e: Exception) {
                // Device non accessible, continuer
                continue
            }
            // Petit délai entre les tests
            delay(500)
        }
        return null
    }

    /** Se connecter à un serveur spécifique */
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

    /** Mode serveur : ce device devient le Master */
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

    /** Connexion manuelle à un device spécifique (pour l'UI) */
    fun connectToDevice(device: BluetoothDevice) {
        connectToServer(device)
    }

    /** Forcer le mode serveur manuellement */
    fun forceServerMode() {
        startServerMode()
    }

    private fun stopDiscoverySafe() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
    }

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

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true

    fun stop() {
        scanJob?.cancel()
        stopDiscoverySafe()
        scope.cancel()
        connectionManager.onDestroy()
        _state.value = AutoConnectState.Idle
        _discoveredDevices.value = emptyList()
    }

    sealed class AutoConnectState {
        object Idle : AutoConnectState()
        object Scanning : AutoConnectState()
        object TestingServices : AutoConnectState()
        data class Connecting(val device: BluetoothDevice) : AutoConnectState()
        object ServerMode : AutoConnectState()
        data class Error(val reason: String) : AutoConnectState()
    }
}