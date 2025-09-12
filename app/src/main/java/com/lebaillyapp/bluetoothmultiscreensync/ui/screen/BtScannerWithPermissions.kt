package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
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
import android.util.Log.e
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private const val TAG = "BTScannerSequential"

// États possibles de notre machine d'états
enum class BtSetupState {
    IDLE,
    CHECKING_PERMISSIONS,
    REQUESTING_PERMISSIONS,
    CHECKING_BLUETOOTH,
    REQUESTING_BLUETOOTH,
    CHECKING_LOCATION,
    REQUESTING_LOCATION,
    READY_TO_SCAN,
    SCANNING,
    ERROR
}

@Composable
fun BtScannerWithPermissions(activity: ComponentActivity) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }

    var currentState by remember { mutableStateOf(BtSetupState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }
    var discovering by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    // Retry automatique toutes les 10 sec si on est en scanning
    val retryRunnable = object : Runnable {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun run() {
            if (currentState == BtSetupState.SCANNING &&
                adapter != null &&
                !adapter.isDiscovering) {
                Log.d(TAG, "Retry scan automatique")
                startBtScanDebug(activity, adapter, devices) { isDisc -> discovering = isDisc }
            }
            handler.postDelayed(this, 10000)
        }
    }

    // Fonction pour avancer dans la séquence
    val proceedToNextStep: () -> Unit = {
        when (currentState) {
            BtSetupState.IDLE -> {
                currentState = BtSetupState.CHECKING_PERMISSIONS
            }
            BtSetupState.CHECKING_PERMISSIONS -> {
                val requiredPerms = getRequiredPermissions()
                val missingPerms = requiredPerms.filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPerms.isNotEmpty()) {
                    Log.d(TAG, "Permissions manquantes: ${missingPerms.joinToString()}")
                    currentState = BtSetupState.REQUESTING_PERMISSIONS
                } else {
                    currentState = BtSetupState.CHECKING_BLUETOOTH
                }
            }
            BtSetupState.CHECKING_BLUETOOTH -> {
                if (adapter == null) {
                    currentState = BtSetupState.ERROR
                    errorMessage = "Bluetooth non supporté"
                } else if (!adapter.isEnabled) {
                    Log.d(TAG, "Bluetooth désactivé, demande d'activation")
                    currentState = BtSetupState.REQUESTING_BLUETOOTH
                } else {
                    currentState = BtSetupState.CHECKING_LOCATION
                }
            }
            BtSetupState.CHECKING_LOCATION -> {
                if (!isLocationEnabled(activity)) {
                    Log.d(TAG, "Localisation désactivée, demande d'activation")
                    currentState = BtSetupState.REQUESTING_LOCATION
                } else {
                    currentState = BtSetupState.READY_TO_SCAN
                }
            }
            BtSetupState.READY_TO_SCAN -> {
                Log.d(TAG, "Toutes les conditions réunies, démarrage du scan")
                currentState = BtSetupState.SCANNING
                if (adapter != null) {
                    startBtScanDebug(activity, adapter, devices) { isDisc -> discovering = isDisc }
                    handler.postDelayed(retryRunnable, 10000)
                }
            }
            else -> {
                Log.d(TAG, "État inattendu: $currentState")
            }
        }
    }

    // Launchers pour les différentes étapes
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.all { it.value }
        Log.d(TAG, "Résultat permissions: $allGranted")

        if (allGranted) {
            currentState = BtSetupState.CHECKING_BLUETOOTH
            // Petit délai pour laisser le système se stabiliser
            handler.postDelayed({ proceedToNextStep() }, 300)
        } else {
            currentState = BtSetupState.ERROR
            errorMessage = "Permissions refusées"
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val btEnabled = adapter?.isEnabled == true
        Log.d(TAG, "Résultat activation BT: $btEnabled")

        if (btEnabled) {
            currentState = BtSetupState.CHECKING_LOCATION
            handler.postDelayed({ proceedToNextStep() }, 300)
        } else {
            currentState = BtSetupState.ERROR
            errorMessage = "Bluetooth refusé"
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // On ne peut pas vraiment savoir le résultat, on re-vérifie
        currentState = BtSetupState.CHECKING_LOCATION
        handler.postDelayed({ proceedToNextStep() }, 300)
    }

    // Gestion du refresh lors du retour à l'app
    val lifecycleOwner: LifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Si on était en train de demander la localisation, on re-vérifie
                if (currentState == BtSetupState.REQUESTING_LOCATION) {
                    handler.postDelayed({
                        currentState = BtSetupState.CHECKING_LOCATION
                        proceedToNextStep()
                    }, 2000)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            handler.removeCallbacks(retryRunnable)
        }
    }

    // Effect pour gérer les transitions d'états
    LaunchedEffect(currentState) {
        when (currentState) {
            BtSetupState.IDLE -> proceedToNextStep()
            BtSetupState.CHECKING_PERMISSIONS -> proceedToNextStep()
            BtSetupState.REQUESTING_PERMISSIONS -> {
                val requiredPerms = getRequiredPermissions()
                val missingPerms = requiredPerms.filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }
                requestPermsLauncher.launch(missingPerms.toTypedArray())
            }
            BtSetupState.CHECKING_BLUETOOTH -> proceedToNextStep()
            BtSetupState.REQUESTING_BLUETOOTH -> {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            BtSetupState.CHECKING_LOCATION -> proceedToNextStep()
            BtSetupState.REQUESTING_LOCATION -> {
                locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            BtSetupState.READY_TO_SCAN -> proceedToNextStep()
            else -> { /* États terminaux */ }
        }
    }

    // UI
    Column(modifier = Modifier.padding(16.dp)) {
        Text("=== BT Scanner Séquentiel ===", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Text("État actuel: ${currentState.name}")

        if (errorMessage.isNotEmpty()) {
            Text("Erreur: $errorMessage", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))

        // Statuts détaillés
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
}

// Helpers
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun startBtScanDebug(
    context: Context,
    adapter: BluetoothAdapter,
    devices: SnapshotStateList<BluetoothDevice>,
    onDiscovering: (Boolean) -> Unit
) {
    Log.d(TAG, "=== startBtScanDebug ===")
    Log.d(TAG, "Adapter enabled: ${adapter.isEnabled}")
    Log.d(TAG, "isDiscovering before: ${adapter.isDiscovering}")

    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!devices.any { it.address == device.address }) {
                            val name = try {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PermissionChecker.PERMISSION_GRANTED
                                ) {
                                    device.name ?: "Unknown"
                                } else "Permission manquante"
                            } catch (_: SecurityException) {
                                "SecurityException"
                            }
                            devices.add(device)
                            Log.d(TAG, "Device trouvé: $name / ${device.address}")
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery terminée")
                    onDiscovering(false)
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    try {
        context.registerReceiver(receiver, filter)
    } catch (e: Exception) {
        Log.e(TAG, "Erreur register receiver", e)
        onDiscovering(false)
        return
    }

    // Vérification des permissions avant de lancer le scan
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        != PermissionChecker.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_SCAN manquante")
        onDiscovering(false)
        return
    }

    try {
        // Arrêt du scan en cours si nécessaire
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
            // Petit délai pour laisser le système se stabiliser
            Handler(Looper.getMainLooper()).postDelayed({
                val started = adapter.startDiscovery()
                Log.d(TAG, "startDiscovery() après cancel = $started")
                onDiscovering(started)
            }, 200)
        } else {
            val started = adapter.startDiscovery()
            Log.d(TAG, "startDiscovery() direct = $started")
            onDiscovering(started)
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException lors du startDiscovery", e)
        onDiscovering(false)
    }
}

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}