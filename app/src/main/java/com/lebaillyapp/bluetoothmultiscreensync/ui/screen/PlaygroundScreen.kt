package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundViewModel


/**
 * ## PlaygroundScreen
 *
 * Screen displayed after role selection and successful Bluetooth connection.
 * Shows the current role, connection status, and provides a test interface for message exchange.
 *
 * ### Features:
 * - Displays connection status (Server or Client mode)
 * - "Send Test Message" button to validate bidirectional communication
 * - Real-time display of incoming messages from connected devices
 * - Auto-scrolling list of received messages with timestamps
 *
 * @param playgroundViewModel Provides server and client connection state flows and message handling
 */
@Composable
@Suppress("MissingPermission")
fun PlaygroundScreen(
    playgroundViewModel: PlaygroundViewModel
) {
    val serverState by playgroundViewModel.serverState.collectAsState()
    val clientState by playgroundViewModel.clientState.collectAsState()

    // Liste des messages reçus
    val receivedMessages = remember { mutableStateListOf<String>() }

    // Collecter les messages entrants
    LaunchedEffect(Unit) {
        playgroundViewModel.incomingMessages.collect { message ->
            receivedMessages.add(message)
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

        // Affichage de l'état de connexion
        if (serverState is ServerState.Connected || serverState is ServerState.Listening) {
            when (serverState) {
                is ServerState.Listening -> Text("Server is listening...")
                is ServerState.Connected -> {
                    val device = (serverState as ServerState.Connected).device
                    Text("Server connected to: ${device.name ?: "Unknown"}")
                }
                is ServerState.Error -> Text("Server error: ${(serverState as ServerState.Error).throwable.message}")
                else -> {}
            }
        } else if (clientState !is ConnectionState.Disconnected) {
            when (clientState) {
                is ConnectionState.Connecting -> Text("Connecting...")
                is ConnectionState.Connected -> Text("Client connected!")
                is ConnectionState.Error -> Text("Error: ${(clientState as ConnectionState.Error).throwable.message}")
                else -> {}
            }
        }

        Divider()

        // Bouton d'envoi de message test
        val isConnected = (serverState is ServerState.Connected) || (clientState is ConnectionState.Connected)
        val role = if (serverState is ServerState.Connected) "Server" else "Client"

        Button(
            onClick = {
                playgroundViewModel.sendMessage("Hello from $role at ${System.currentTimeMillis()}")
            },
            enabled = isConnected
        ) {
            Text("Send Test Message")
        }

        Divider()

        // Affichage des messages reçus
        Text("Received Messages:", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (receivedMessages.isEmpty()) {
                item { Text("No messages yet...", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(receivedMessages) { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}