package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
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
    val state = _state.asStateFlow()

    private var foundDevice: BluetoothDevice? = null
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
            scanForServerWithTimeout(5000)

            foundDevice?.let { device ->
                connectToServer(device) // plus d’erreur, on gère la permission à l’intérieur
            } ?: startServerMode()
        }
    }




    private suspend fun scanForServerWithTimeout(timeoutMs: Long) = suspendCancellableCoroutine<Unit> { cont ->
        foundDevice = null
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    // Accès sécurisé à device.name
                    val deviceName: String? = try {
                        if (hasBluetoothConnectPermission()) device?.name else null
                    } catch (e: SecurityException) {
                        null
                    }

                    if (device != null && deviceName?.startsWith("BTMultiScreenSync") == true) {
                        foundDevice = device
                        stopDiscoverySafe()
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

        scope.launch {
            delay(timeoutMs)
            stopDiscoverySafe()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun connectToServer(device: BluetoothDevice) {
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
        scope.cancel()
        connectionManager.onDestroy()
        _state.value = AutoConnectState.Idle
    }

    sealed class AutoConnectState {
        object Idle : AutoConnectState()
        object Scanning : AutoConnectState()
        data class Connecting(val device: BluetoothDevice) : AutoConnectState()
        object ServerMode : AutoConnectState()
        data class Error(val reason: String) : AutoConnectState()
    }
}
