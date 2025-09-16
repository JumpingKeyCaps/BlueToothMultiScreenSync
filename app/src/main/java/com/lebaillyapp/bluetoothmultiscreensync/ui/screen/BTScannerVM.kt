package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothWorkflowEvent
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel

private const val TAG = "BTScannerComposable"

@Composable
fun BTScannerVM(activity: ComponentActivity, viewModel: BluetoothViewModel) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var discovering by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val handler = remember { Handler(Looper.getMainLooper()) }

    // --- Collect workflow state properly ---
    val workflowState by viewModel.workflowState.collectAsState()

    // --- Retry scan every 10 sec ---
    val retryRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (workflowState == com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothWorkflowState.SCANNING &&
                adapter != null && !adapter.isDiscovering
            ) {
                startBtScanDebug(activity, adapter, devices) { discovering = it }
            }
            handler.postDelayed(this, 10000)
        }
    }

    // --- Launchers ---
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.all { it.value }
        viewModel.handleWorkflowEvent(
            BluetoothWorkflowEvent.PermissionsResult(allGranted)
        )
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.handleWorkflowEvent(
            BluetoothWorkflowEvent.BluetoothEnableResult(
                adapter?.isEnabled == true
            )
        )
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.handleWorkflowEvent(
            BluetoothWorkflowEvent.LocationEnableResult(
                isLocationEnabled(activity)
            )
        )
    }

    // --- Workflow events collector ---
    LaunchedEffect(Unit) {
        viewModel.workflowEvents.collect { event ->
            when (event) {
                is BluetoothWorkflowEvent.RequestPermissions -> {
                    val missing = getRequiredPermissions().filter {
                        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) requestPermsLauncher.launch(missing.toTypedArray())
                }
                is BluetoothWorkflowEvent.RequestBluetoothEnable -> {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                is BluetoothWorkflowEvent.RequestLocationEnable -> {
                    locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                is BluetoothWorkflowEvent.RequestModeSelection -> {
                    showModeDialog = true
                }
                is BluetoothWorkflowEvent.StartScan -> {
                    startBtScanDebug(activity, adapter, devices) { discovering = it }
                    handler.postDelayed(retryRunnable, 10000)
                }
                is BluetoothWorkflowEvent.WorkflowError -> {
                    errorMessage = event.error
                }
                else -> {}
            }
        }
    }

    // --- UI ---
    Column(modifier = Modifier.padding(16.dp)) {
        Text("=== BT Scanner ===", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Text("État: $workflowState")
        if (errorMessage.isNotEmpty()) Text("Erreur: $errorMessage", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        Text("✓ Permissions: ${if (getRequiredPermissions().all { ContextCompat.checkSelfPermission(activity,it)==PackageManager.PERMISSION_GRANTED }) "OK" else "NOK"}")
        Text("✓ Bluetooth: ${if (adapter?.isEnabled == true) "ON" else "OFF"}")
        Text("✓ Location: ${if (isLocationEnabled(activity)) "ON" else "OFF"}")
        Text("✓ Discovering: $discovering")
        Spacer(Modifier.height(8.dp))

        Text("Devices (${devices.size}):", style = MaterialTheme.typography.titleSmall)
        devices.forEach { device ->
            val name = try {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    device.name ?: "Unknown"
                } else "Permission manquante"
            } catch (_: SecurityException) {
                "SecurityException"
            }
            Text("• $name / ${device.address}")
        }
    }

    // --- Mode dialog ---
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mode Bluetooth") },
            text = { Text("Choisissez le mode de fonctionnement") },
            confirmButton = { TextButton(onClick = {
                viewModel.handleWorkflowEvent(
                    BluetoothWorkflowEvent.ModeSelected(true)
                )
                showModeDialog=false
            }) { Text("Serveur") } },
            dismissButton = { TextButton(onClick = {
                viewModel.handleWorkflowEvent(
                    BluetoothWorkflowEvent.ModeSelected(false)
                )
                showModeDialog=false
            }) { Text("Client") } }
        )
    }

    // --- Receiver discovery ---
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when(intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            if (!devices.any { it.address == device.address }) devices.add(it)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        discovering = false
                    }
                }
            }
        }
        activity.registerReceiver(receiver, filter)
        onDispose {
            activity.unregisterReceiver(receiver)
            handler.removeCallbacks(retryRunnable)
        }
    }

    // --- Start workflow ---
    LaunchedEffect(Unit) { viewModel.startSequentialWorkflow() }
}


// --- Helpers ---
private fun getRequiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@SuppressLint("MissingPermission")
private fun startBtScanDebug(
    context: Context,
    adapter: BluetoothAdapter?,
    devices: SnapshotStateList<BluetoothDevice>,
    onDiscovering: (Boolean) -> Unit
) {
    if(adapter==null) return

    try {
        if(adapter.isDiscovering) adapter.cancelDiscovery()
        adapter.startDiscovery().also { onDiscovering(it) }
    } catch(_: SecurityException){
        onDiscovering(false)
    }
}
