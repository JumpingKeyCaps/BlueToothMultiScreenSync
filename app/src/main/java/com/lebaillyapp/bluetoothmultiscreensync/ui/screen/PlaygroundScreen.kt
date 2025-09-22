package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel

@Composable
@Suppress("MissingPermission")
fun PlaygroundScreen(
    roleViewModel: RoleViewModel
) {
    val selectedRole by roleViewModel.role.collectAsState()
    val serverState by roleViewModel.serverState.collectAsState()
    val clientState by roleViewModel.clientState.collectAsState()

    // Side effect si besoin : démarrer quelque chose quand connecté
    LaunchedEffect(selectedRole, serverState, clientState) {
        val connected = when (selectedRole) {
            RoleViewModel.Role.Server -> serverState is ServerState.Connected
            RoleViewModel.Role.Client -> clientState is ConnectionState.Connected
            else -> false
        }
        if (connected) {
            // lancer un event, démarrer un timer, ou autre
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Role Selection", style = MaterialTheme.typography.titleLarge)
        Text("Role: ${selectedRole?.name ?: "Unknown"}", style = MaterialTheme.typography.titleMedium)

        Divider()

        // Info connection
        if (selectedRole == RoleViewModel.Role.Server) {
            when (serverState) {
                is ServerState.Listening -> Text("Server is listening...")
                is ServerState.Connected -> {
                    val device = (serverState as ServerState.Connected).device
                    Text("Connected to: ${device.name ?: "Unknown"} (${device.address})")
                }
                is ServerState.Stopped -> Text("Server stopped")
                is ServerState.Error -> Text("Server error: ${(serverState as ServerState.Error).throwable.message}")
            }
        }

        if (selectedRole == RoleViewModel.Role.Client) {
            when (clientState) {
                is ConnectionState.Connecting -> Text("Connecting...")
                is ConnectionState.Connected -> Text("Connected!")
                is ConnectionState.Disconnected -> Text("Disconnected")
                is ConnectionState.Error -> Text("Error: ${(clientState as ConnectionState.Error).throwable.message}")
            }
        }
    }
}