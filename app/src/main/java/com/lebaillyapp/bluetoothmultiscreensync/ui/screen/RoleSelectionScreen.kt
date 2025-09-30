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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
@OptIn(ExperimentalMaterial3Api::class)
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
            roleViewModel.selectRole(RoleViewModel.Role.Server)
        }
    }

    LaunchedEffect(selectedRole, serverState, clientState) {
        val connected = when (selectedRole) {
            RoleViewModel.Role.Server -> serverState is ServerState.Connected
            RoleViewModel.Role.Client -> clientState is ConnectionState.Connected
            else -> false
        }
        if (connected) {
            navController.navigate("playground") {
                popUpTo("role_selection") { inclusive = true }
            }
        }
    }

    // Build subtitle text = Role + State
    val subtitle = when (selectedRole) {
        RoleViewModel.Role.Server -> {
            "Server - " + when (serverState) {
                is ServerState.Listening -> "Listening..."
                is ServerState.Connected -> "Connected"
                is ServerState.Error -> "Error"
                is ServerState.Stopped -> "Stopped"
            }
        }
        RoleViewModel.Role.Client -> {
            "Client - " + when (clientState) {
                is ConnectionState.Connecting -> "Connecting..."
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.Error -> "Error"
            }
        }
        else -> "No role selected"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bluetooth mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp),
                contentAlignment = Alignment.Center
            ) {
                val buttonText = when (selectedRole) {
                    RoleViewModel.Role.Server -> "Switch to Client"
                    RoleViewModel.Role.Client -> "Switch to Server"
                    else -> "Choose Role"
                }

                ElevatedButton(
                    modifier = Modifier.fillMaxWidth()
                        .height(54.dp),
                    onClick = {
                        if (selectedRole == RoleViewModel.Role.Client) {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                            }
                            discoverabilityLauncher.launch(intent)
                        } else {
                            roleViewModel.selectRole(RoleViewModel.Role.Client)
                        }
                    }
                ) {
                    Text(buttonText)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server connected details
                if (selectedRole == RoleViewModel.Role.Server && serverState is ServerState.Connected) {
                    item {
                        val device = (serverState as ServerState.Connected).device
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Connected to: ${device.name ?: "Unknown"}")
                                Text(device.address, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Client devices
                if (selectedRole == RoleViewModel.Role.Client) {
                    if (scannedDevices.isEmpty()) {
                        item { Text("No devices found yet...") }
                    } else {
                        items(scannedDevices, key = { it.address }) { device ->
                            Card(
                                onClick = { roleViewModel.connectToDevice(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 26.dp, end = 26.dp),
                                shape = RoundedCornerShape(35.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp, end = 36.dp, top = 12.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(device.name ?: "Unknown")
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Infinite progress indicator when Server is listening
            if (selectedRole == RoleViewModel.Role.Server && serverState is ServerState.Listening) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}




