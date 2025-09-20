package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel

@Composable
@Suppress("MissingPermission")
fun RoleSelectionScreen(
    navController: NavController,
    roleViewModel: RoleViewModel
) {
    val selectedRole by roleViewModel.role.collectAsState()
    val scannedDevices by roleViewModel.scannedDevices.collectAsState()
    val serverState by roleViewModel.serverState.collectAsState()

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

        // Buttons for role selection
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { roleViewModel.selectRole(RoleViewModel.Role.Server) },
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
            Text("Server State: $serverState")
        }

        // Client scan results
        if (selectedRole == RoleViewModel.Role.Client) {
            Text("Scanning nearby devices:")
            if (scannedDevices.isEmpty()) {
                Text("No devices found yet...")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scannedDevices.forEach { device ->
                        Button(
                            onClick = {
                                // Connect to device when clicked
                                roleViewModel.selectRole(RoleViewModel.Role.Client)
                                // Optionally: implement connection flow here
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${device.name ?: "Unknown"} (${device.address})")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation to next screen once role is selected (mock)
        if (selectedRole != null) {
            Button(
                onClick = { navController.navigate("canvas") }
            ) {
                Text("Go to Canvas")
            }
        }
    }
}