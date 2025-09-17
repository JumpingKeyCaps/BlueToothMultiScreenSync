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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ## BluetoothScanner
 * Gère la découverte des devices Bluetooth Classic (SPP/RFCOMM).
 * Expose un Flow<List<BluetoothDevice>> mis à jour en temps réel.
 */
class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                    if (discoveredDevices.add(device)) {
                        _devices.value = discoveredDevices.toList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // redémarre la découverte si besoin
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        stopScan() // stop si déjà en cours

        discoveredDevices.clear()
        _devices.value = emptyList()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        adapter.startDiscovery()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun stopScan() {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}