package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundViewModel

/**
 * ## PlaygroundScreen
 *
 * Destination après sélection de rôle et connexion Bluetooth.
 * Affiche le rôle courant et l’état des connexions.
 *
 * @param playgroundViewModel Fournit les flux de connexion client et serveur
 */
@Composable
@Suppress("MissingPermission")
fun PlaygroundScreen(
    playgroundViewModel: PlaygroundViewModel
) {
    val serverState by playgroundViewModel.serverState.collectAsState()
    val clientState by playgroundViewModel.clientState.collectAsState()

    // Side effect pour déclencher des actions quand connecté
    LaunchedEffect(serverState, clientState) {
        val connected = (serverState is ServerState.Connected) || (clientState is ConnectionState.Connected)
        if (connected) {
            // TODO: démarrer le canvas ou une animation, timer, etc.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playground Screen", style = MaterialTheme.typography.titleLarge)

        Divider()

        // N'afficher que l'état pertinent
        if (serverState is ServerState.Connected || serverState is ServerState.Listening) {
            // Mode serveur actif
            when (serverState) {
                is ServerState.Listening -> Text("Server is listening...")
                is ServerState.Connected -> {
                    val device = (serverState as ServerState.Connected).device
                    Text("Server connected to: ${device.name ?: "Unknown"} (${device.address})")
                }
                is ServerState.Error -> Text("Server error: ${(serverState as ServerState.Error).throwable.message}")
                else -> {}
            }
        } else if (clientState !is ConnectionState.Disconnected) {
            // Mode client actif
            when (clientState) {
                is ConnectionState.Connecting -> Text("Connecting...")
                is ConnectionState.Connected -> Text("Client connected!")
                is ConnectionState.Error -> Text("Error: ${(clientState as ConnectionState.Error).throwable.message}")
                else -> {}
            }
        }
    }
}
