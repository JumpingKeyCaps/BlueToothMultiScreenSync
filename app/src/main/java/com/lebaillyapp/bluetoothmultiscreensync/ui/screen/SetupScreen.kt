package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
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

/**
 * ## SetupScreen
 *
 * Composable UI step that ensures all prerequisites for Bluetooth communication
 * are satisfied before entering the rest of the app.
 *
 * ### Responsibilities
 * - Requests **runtime permissions** required for Bluetooth Classic.
 *   - Android 12+ (API 31+): `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION`
 *   - Below Android 12: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`
 * - Ensures **Bluetooth is enabled** on the device by launching the system intent
 *   `BluetoothAdapter.ACTION_REQUEST_ENABLE`.
 * - Ensures **Location Services** are enabled, since scanning requires it.
 *   Opens system location settings (`Settings.ACTION_LOCATION_SOURCE_SETTINGS`).
 * - Observes [SetupViewModel] state (`permissionsGranted`, `bluetoothEnabled`,
 *   `locationEnabled`, `readyToProceed`).
 * - When all conditions are met, triggers the [onReady] callback to allow navigation
 *   to the next screen.
 *
 * ### UI Behavior
 * - Displays a column of action buttons:
 *   - **Request Permissions**
 *   - **Enable Bluetooth**
 *   - **Enable Location**
 * - Each button becomes enabled only if the prerequisite condition is not yet satisfied.
 * - Shows a success message `"Tout est prêt !"` once all requirements are fulfilled.
 *
 *
 * @param viewModel [SetupViewModel] providing state and update logic for setup flow.
 * @param onReady Callback invoked once all prerequisites are satisfied.
 */
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
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
                permissionLauncher.launch(perms)
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
