package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ## BluetoothScanner
 *
 * Handles discovery of nearby Bluetooth Classic devices (SPP/RFCOMM).
 * Provides a [StateFlow] of [BluetoothDevice] that is updated in real time as devices are found.
 *
 * ### Features:
 * - Starts and stops Bluetooth device discovery.
 * - Collects unique discovered devices and exposes them via [devices].
 * - Automatically updates the [StateFlow] as new devices are found.
 * - Uses a [BroadcastReceiver] to listen for discovery events.
 *
 *
 * @property context Context used to register/unregister the [BroadcastReceiver].
 * @property adapter The [BluetoothAdapter] used for discovery operations.
 */
class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    /**
     * [StateFlow] exposing the list of discovered Bluetooth devices in real time.
     * Each device is unique; duplicates are automatically ignored.
     */
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    if (discoveredDevices.add(device)) {
                        _devices.value = discoveredDevices.toList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Discovery finished – optionally restart if needed
                }
            }
        }
    }

    /**
     * Starts scanning for nearby Bluetooth devices.
     *
     * Requires [Manifest.permission.BLUETOOTH_SCAN] at runtime.
     * Clears previous results before starting and updates [devices] as new devices are found.
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        stopScan() // stop if already scanning

        discoveredDevices.clear()
        _devices.value = emptyList()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        adapter.startDiscovery()
    }

    /**
     * Stops the ongoing Bluetooth discovery.
     *
     * Cancels the adapter discovery and unregisters the internal [BroadcastReceiver].
     * Safe to call even if scanning was not active.
     */
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun stopScan() {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // receiver not registered – ignore
        }
    }
}
