package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.app.Activity
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
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothWorkflowEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "BTScannerVMAdapter"
@Composable
fun BTScannerVM(activity: ComponentActivity, viewModel: BluetoothViewModel) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var discovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // --- Launchers ---
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.all { it.value }
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.PermissionsResult(allGranted))
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val btEnabled = adapter?.isEnabled == true
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.BluetoothEnableResult(btEnabled))
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val locationEnabled = isLocationEnabled(activity)
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.LocationEnableResult(locationEnabled))
    }

    // --- Collect workflow events ---
    LaunchedEffect(Unit) {
        viewModel.workflowEvents.collectLatest { event ->
            when (event) {
                is BluetoothWorkflowEvent.RequestPermissions -> {
                    val missing = getRequiredPermissions().filter {
                        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) requestPermsLauncher.launch(missing.toTypedArray())
                    else viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.PermissionsResult(true))
                }
                is BluetoothWorkflowEvent.RequestBluetoothEnable -> enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                is BluetoothWorkflowEvent.RequestLocationEnable -> locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                is BluetoothWorkflowEvent.StartScan -> startBtScanDebug(adapter, devices) { discovering = it }
                is BluetoothWorkflowEvent.WorkflowError -> errorMessage = event.error
                else -> {}
            }
        }
    }

    // --- Retry scan every 10s ---
    LaunchedEffect(adapter) {
        while (true) {
            if (adapter != null && !adapter.isDiscovering && hasScanPermission(activity)) {
                adapter.startDiscovery().also { discovering = it }
            }
            kotlinx.coroutines.delay(10_000)
        }
    }

    // --- UI ---
    Column(modifier = Modifier.padding(16.dp)) {
        Text("=== BT Scanner VM ===", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val workflowState by viewModel.workflowState.collectAsState(initial = null)
        Text("État actuel: ${workflowState?.name ?: "IDLE"}")

        if (errorMessage.isNotEmpty()) Text("Erreur: $errorMessage", color = MaterialTheme.colorScheme.error)

        Spacer(Modifier.height(8.dp))

        val btEnabled = adapter?.isEnabled == true
        val locationEnabled = isLocationEnabled(activity)
        val permsGranted = getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

        Text("✓ Permissions: ${if (permsGranted) "OK" else "NOK"}")
        Text("✓ Bluetooth: ${if (btEnabled) "ON" else "OFF"}")
        Text("✓ Location: ${if (locationEnabled) "ON" else "OFF"}")
        Text("✓ Discovering: $discovering")

        Spacer(Modifier.height(8.dp))
        Text("Devices trouvés: ${devices.size}", style = MaterialTheme.typography.titleSmall)

        devices.forEach { device ->
            val name = try {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                    device.name ?: "Unknown" else "Permission manquante"
            } catch (_: SecurityException) {
                "SecurityException"
            }
            Text("• $name / ${device.address}")
        }
    }
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

private fun hasScanPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
}

@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
private fun startBtScanDebug(
    adapter: BluetoothAdapter?,
    devices: SnapshotStateList<BluetoothDevice>,
    onDiscovering: (Boolean) -> Unit
) {
    if (adapter == null) return
    try {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        adapter.startDiscovery().also { onDiscovering(it) }
    } catch (_: SecurityException) { onDiscovering(false) }
}
