package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel

/**
 * Composable screen that allows the user to select their Bluetooth role
 * (**Server** or **Client**) and displays the corresponding state.
 *
 * ### Main features:
 * - **Role selection**:
 *   - Server button: triggers the system intent `ACTION_REQUEST_DISCOVERABLE`
 *     to make the device discoverable for 120 seconds.
 *     If the user accepts, the [RoleViewModel] switches to server mode,
 *     and the underlying repository starts listening via a [BluetoothServerSocket].
 *   - Client button: switches to client mode and starts scanning for nearby devices.
 *
 * - **Server state display**:
 *   - `Listening` → the device is waiting for incoming connections.
 *   - `Error` → an error occurred (e.g., socket failure).
 *   - `Stopped` → server is stopped.
 *
 * - **Client scanned devices display**:
 *   - Dynamically shows discovered devices.
 *   - Each row is clickable to trigger a future connection (TODO).
 *
 *
 * ### Implementation notes:
 * - Bluetooth logic (scanning, listening, connecting) is handled in [RoleViewModel]
 *   and its dependencies (Repository + Services).
 * - This screen only handles the UI and user interactions.
 * - [rememberLauncherForActivityResult] is used to wrap the discoverability system intent.
 *
 * @param navController Jetpack Navigation controller for screen navigation.
 * @param roleViewModel ViewModel exposing the current role, server state, and scanned devices.
 */
@Composable
@Suppress("MissingPermission")
fun RoleSelectionScreen(
    navController: NavController,
    roleViewModel: RoleViewModel
) {
    val selectedRole by roleViewModel.role.collectAsState()
    val scannedDevices by roleViewModel.scannedDevices.collectAsState()
    val serverState by roleViewModel.serverState.collectAsState()

    val discoverabilityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            // User accepted → start server role
            roleViewModel.selectRole(RoleViewModel.Role.Server)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Select your role",
            style = MaterialTheme.typography.titleMedium
        )

        // Role selection buttons
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                    }
                    discoverabilityLauncher.launch(intent)
                },
                enabled = selectedRole != RoleViewModel.Role.Server
            ) {
                Text("Server")
            }

            Button(
                onClick = { roleViewModel.selectRole(RoleViewModel.Role.Client) },
                enabled = selectedRole != RoleViewModel.Role.Client
            ) {
                Text("Client")
            }
        }

        // Server state display
        if (selectedRole == RoleViewModel.Role.Server) {
            when (serverState) {
                is ServerState.Listening -> Text("Server is listening for incoming connections...")
                is ServerState.Error -> Text("Error: ${(serverState as ServerState.Error).throwable.message}")
                is ServerState.Stopped -> Text("Server is stopped!")
            }
        }

        // Client scanned devices list
        if (selectedRole == RoleViewModel.Role.Client) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (scannedDevices.isEmpty()) {
                    item { Text("No devices found yet...") }
                } else {
                    items(scannedDevices, key = { it.address }) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // TODO: trigger connection to the device
                                    // e.g.: roleViewModel.connectToDevice(device)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${device.name ?: "Unknown"}")
                            Text(device.address)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation to the Canvas screen TO REMOVE
        if (selectedRole != null) {
            Button(
                onClick = { navController.navigate("canvas") }
            ) {
                Text("Go to Canvas")
            }
        }
    }
}