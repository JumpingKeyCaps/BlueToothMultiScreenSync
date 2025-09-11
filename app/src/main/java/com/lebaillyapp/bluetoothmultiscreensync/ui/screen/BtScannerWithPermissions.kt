package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

private const val TAG = "BTScannerRetry"

@Composable
fun BtScannerWithPermissions(activity: ComponentActivity) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }

    var btEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var locationEnabled by remember { mutableStateOf(isLocationEnabled(activity)) }
    var permsGranted by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    // Retry automatique toutes les 5 sec
    val retryRunnable = object : Runnable {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun run() {
            if (adapter != null && btEnabled && locationEnabled && permsGranted && !adapter.isDiscovering) {
                Log.d(TAG, "Retry scan 5s")
                startBtScanDebug(activity, adapter, devices) { isDisc -> discovering = isDisc }
            }
            handler.postDelayed(this, 5000)
        }
    }

    val startScanIfReady: () -> Unit = {
        btEnabled = adapter?.isEnabled == true
        locationEnabled = isLocationEnabled(activity)
        Log.d(TAG, "startScanIfReady: BT=$btEnabled, Loc=$locationEnabled, Perms=$permsGranted")
        if (adapter != null && btEnabled && locationEnabled && permsGranted) {
            Log.d(TAG, "Conditions OK, lancement scan sécurisé.")
            startBtScanDebug(activity, adapter, devices) { isDisc -> discovering = isDisc }
            handler.removeCallbacks(retryRunnable)
            handler.postDelayed(retryRunnable, 5000)
        } else {
            Log.d(TAG, "Scan impossible: conditions non remplies")
        }
    }

    // Launchers
    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btEnabled = adapter?.isEnabled == true
        handler.postDelayed({ startScanIfReady() }, 500)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        locationEnabled = isLocationEnabled(activity)
        handler.postDelayed({ startScanIfReady() }, 500)
    }

    val requestPermsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        permsGranted = perms.all { it.value }
        Log.d(TAG, "Permissions accordées: $permsGranted")
        startScanIfReady()
    }

    LaunchedEffect(Unit) {
        if (adapter == null) {
            Log.d(TAG, "Bluetooth non supporté.")
            return@LaunchedEffect
        }

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val missing = perms.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
        permsGranted = missing.isEmpty()

        when {
            missing.isNotEmpty() -> {
                Log.d(TAG, "Permissions initiales manquantes: ${missing.joinToString()}")
                requestPermsLauncher.launch(missing.toTypedArray())
            }
            !locationEnabled -> locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            !btEnabled -> enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> startScanIfReady()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("=== BT Scanner Retry Debug ===", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Bluetooth: ${if (btEnabled) "ON" else "OFF"}")
        Text("Location: ${if (locationEnabled) "ON" else "OFF"}")
        Text("Permissions: ${if (permsGranted) "GRANTED" else "MISSING"}")
        Text("Discovering: $discovering")
        Spacer(Modifier.height(8.dp))
        Text("Devices found: ${devices.size}", style = MaterialTheme.typography.titleSmall)
        devices.forEach { device ->
            val name = try {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PermissionChecker.PERMISSION_GRANTED)
                    device.name ?: "Unknown"
                else "Permission manquante"
            } catch (_: SecurityException) { "SecurityException" }
            Text("$name / ${device.address}")
        }
    }
}

// Scan sécurisé debug
private fun startBtScanDebug(
    context: Context,
    adapter: BluetoothAdapter,
    devices: SnapshotStateList<BluetoothDevice>,
    onDiscovering: (Boolean) -> Unit
) {
    Log.d(TAG, "=== startBtScanDebug ===")
    Log.d(TAG, "Adapter enabled: ${adapter.isEnabled}")
    Log.d(TAG, "isDiscovering before cancel: ${adapter.isDiscovering}")

    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!devices.any { it.address == device.address }) {
                            val name = try {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PermissionChecker.PERMISSION_GRANTED)
                                    device.name ?: "Unknown"
                                else "Permission manquante"
                            } catch (_: SecurityException) { "SecurityException" }
                            devices.add(device)
                            Log.d(TAG, "Found device: $name / ${device.address}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished")
                    onDiscovering(false)
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }
    }

    try { context.registerReceiver(receiver, filter) } catch (e: Exception) { Log.e(TAG, "Register failed", e) }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PermissionChecker.PERMISSION_GRANTED) {
        try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: SecurityException) {}
        try {
            val started = adapter.startDiscovery()
            Log.d(TAG, "startDiscovery() = $started")
            onDiscovering(started)
        } catch (e: SecurityException) { Log.e(TAG, "startDiscovery SecurityException", e); onDiscovering(false) }
    } else {
        Log.e(TAG, "BLUETOOTH_SCAN manquante")
        onDiscovering(false)
    }
}

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
