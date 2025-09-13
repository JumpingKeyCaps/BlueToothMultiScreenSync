package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.annotation.SuppressLint
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
import com.lebaillyapp.bluetoothmultiscreensync.data.service.RealPeerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "BTScannerVMAdapter"

/**
 * ## BTScannerVM
 * A Composable function that acts as the UI and the platform-specific integration layer for the Bluetooth workflow.
 * It observes the [BluetoothViewModel] for state changes and one-time events,
 * and it uses Android APIs to request permissions, enable Bluetooth, and discover nearby devices.
 * This Composable also renders the UI based on the current workflow state and discovered devices.
 *
 * @param activity The [ComponentActivity] context, required for launching system activities and checking permissions.
 * @param viewModel The [BluetoothViewModel] instance that manages the workflow state.
 */
@Composable
fun BTScannerVM(activity: ComponentActivity, viewModel: BluetoothViewModel) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var discovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // --- Launchers ---
    /**
     * A launcher for requesting multiple permissions. It sends the result back to the ViewModel.
     */
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.all { it.value }
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.PermissionsResult(allGranted))
    }

    /**
     * A launcher for requesting the user to enable Bluetooth. It sends the result back to the ViewModel.
     */
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val btEnabled = adapter?.isEnabled == true
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.BluetoothEnableResult(btEnabled))
    }

    /**
     * A launcher for prompting the user to enable location services. It sends the result back to the ViewModel.
     */
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val locationEnabled = isLocationEnabled(activity)
        viewModel.handleWorkflowEvent(BluetoothWorkflowEvent.LocationEnableResult(locationEnabled))
    }

    // --- Bluetooth Scan Receiver ---
    /**
     * A [DisposableEffect] to manage the lifecycle of the [BroadcastReceiver] for Bluetooth discovery.
     * It registers the receiver when the Composable enters the composition and unregisters it when it leaves.
     */
    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }

                        device?.let {
                            if (!devices.contains(it)) {
                                devices.add(it)
                            }
                        }
                    }
                }
            }
        }

        // Register the receiver
        ContextCompat.registerReceiver(
            activity,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Unregister on dispose
        onDispose {
            activity.unregisterReceiver(receiver)
        }
    }

    // --- Collect workflow events ---
    /**
     * A [LaunchedEffect] that collects one-time events from the ViewModel.
     * It triggers platform-specific actions like launching permission or settings screens.
     */
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
    /**
     * A [LaunchedEffect] that periodically checks and restarts Bluetooth discovery.
     * It ensures the scan continues to find new devices without user intervention.
     */
    LaunchedEffect(adapter) {
        while (true) {
            if (adapter != null && !adapter.isDiscovering && hasScanPermission(activity)) {
                adapter.startDiscovery().also { discovering = it }
            }
            delay(10_000)

            // Attendre 10 secondes pour que le scan se termine
            delay(10_000)

            // Envoyer la liste des BluetoothDevice au ViewModel
            // La liste 'devices' est celle qui est remplie par le BroadcastReceiver.
            viewModel.handleScannedDevices(devices.toList())

        }

    }

    // --- UI ---
    /**
     * The main UI layout for the screen.
     */
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
/**
 * A helper function to get the list of required permissions based on the Android API level.
 * @return An array of strings representing the required permissions.
 */
private fun getRequiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/**
 * A helper function to check if location services are enabled on the device.
 * @param context The application context.
 * @return True if a location provider is enabled, false otherwise.
 */
private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

/**
 * A helper function to check if the BLUETOOTH_SCAN permission has been granted.
 * @param context The application context.
 * @return True if the permission is granted, false otherwise.
 */
private fun hasScanPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
}

/**
 * A helper function to start the Bluetooth discovery process.
 * It's annotated with [RequiresPermission] to indicate the necessary permission.
 * @param adapter The [BluetoothAdapter] instance.
 * @param devices The mutable list to add discovered devices to.
 * @param onDiscovering A callback to update the discovering state.
 */
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