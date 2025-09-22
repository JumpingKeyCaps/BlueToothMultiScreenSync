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
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel

/**
 * Screen to select Bluetooth role (**Server** or **Client**) and manage corresponding state.
 *
 * ### Features:
 * - **Server mode**:
 *   - Launches system discoverability intent (120s).
 *   - Starts server in ViewModel if user accepts.
 *   - Displays server state: Listening / Error / Stopped.
 *
 * - **Client mode**:
 *   - Starts scanning automatically.
 *   - Shows dynamically discovered devices.
 *   - Clicking a device triggers connection via ViewModel.
 *   - Shows client connection state (Connecting / Connected / Disconnected / Error).
 *
 * ### Notes:
 * - All Bluetooth logic is handled in [RoleViewModel] and its repository.
 * - This composable only handles UI and user interactions.
 * - [rememberLauncherForActivityResult] wraps the discoverability system intent.
 *
 * @param navController Navigation controller for screen transitions.
 * @param roleViewModel ViewModel exposing role, server state, scanned devices, and client state.
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
    val clientState by roleViewModel.clientState.collectAsState()

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
        Text("Select your role", style = MaterialTheme.typography.titleMedium)

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
            ) { Text("Server") }

            Button(
                onClick = { roleViewModel.selectRole(RoleViewModel.Role.Client) },
                enabled = selectedRole != RoleViewModel.Role.Client
            ) { Text("Client") }
        }

        // Server state
        if (selectedRole == RoleViewModel.Role.Server) {
            when (serverState) {
                is ServerState.Listening -> Text("Server is listening for incoming connections...")
                is ServerState.Error -> Text("Error: ${(serverState as ServerState.Error).throwable.message}")
                is ServerState.Stopped -> Text("Server is stopped!")
            }
        }

        // Client devices list & state
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
                                .clickable { roleViewModel.connectToDevice(device) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(device.name ?: "Unknown")
                            Text(device.address)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            when (clientState) {
                is ConnectionState.Connecting -> Text("Connecting to device...")
                is ConnectionState.Connected -> Text("Connected!")
                is ConnectionState.Disconnected -> Text("Disconnected")
                is ConnectionState.Error -> Text("Error: ${(clientState as ConnectionState.Error).throwable.message}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mock navigation
        if (selectedRole != null) {
            Button(onClick = { navController.navigate("canvas") }) {
                Text("Go to Canvas")
            }
        }
    }
}
