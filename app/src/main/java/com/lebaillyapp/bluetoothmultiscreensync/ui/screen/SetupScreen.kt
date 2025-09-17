package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onReady: () -> Unit
) {
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsState()
    val locationEnabled by viewModel.locationEnabled.collectAsState()
    val ready by viewModel.readyToProceed.collectAsState()

    // Launcher pour demander les permissions runtime
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.updatePermissions(results.all { it.value })
    }

    // Launcher pour activer le Bluetooth
    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStates() // update bluetooth state après retour
    }

    // Launcher pour ouvrir les paramètres de localisation
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStates() // update location state après retour
    }

    LaunchedEffect(ready) {
        if (ready) onReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Préparation du Bluetooth et des Permissions",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            },
            enabled = !permissionsGranted
        ) {
            Text(if (permissionsGranted) "Permissions OK" else "Demander Permissions")
        }

        Button(
            onClick = {
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            enabled = permissionsGranted && !bluetoothEnabled
        ) {
            Text(if (bluetoothEnabled) "Bluetooth OK" else "Activer Bluetooth")
        }

        Button(
            onClick = {
                locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            enabled = permissionsGranted && !locationEnabled
        ) {
            Text(if (locationEnabled) "Location OK" else "Activer Location")
        }

        if (ready) {
            Text(
                "Tout est prêt !",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
