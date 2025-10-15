package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundViewModel


/**
 * ## PlaygroundScreen
 *
 * Screen displayed after role selection and successful Bluetooth connection.
 * Shows the current role, connection status, network devices, and provides a test interface for message exchange.
 *
 * ### Features:
 * - Full screen layout (edge to edge)
 * - Compact network device list displayed at top-left corner
 * - "Send Test Message" button to validate bidirectional communication
 * - Real-time display of incoming messages from connected devices
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
    val networkDevices by playgroundViewModel.networkDevices.collectAsState()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Dimensions de l'écran
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val densityDpi = configuration.densityDpi
    val densityScale = density.density

    // Liste des messages reçus
    val receivedMessages = remember { mutableStateListOf<String>() }

    // Collecter les messages entrants (filtrer les messages système)
    LaunchedEffect(Unit) {
        playgroundViewModel.incomingMessages.collect { message ->
            if (!message.startsWith("NETWORK_UPDATE|")) {
                receivedMessages.add(message)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets(0.dp)) // Ignore tous les insets système
    ) {

        Column( modifier = Modifier
            .align(Alignment.TopStart)) {
            // Liste des devices en haut à gauche
            Column(
                modifier = Modifier

                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                Spacer(modifier = Modifier.height(54.dp))
                Text(
                    text = "Network (${networkDevices.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (networkDevices.isEmpty()) {
                    Text(
                        text = "No devices",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    networkDevices.forEach { device ->
                        val status = clientState.toString()
                        val role = if (device.isMaster) "Master" else "Slave"

                        Text(
                            text = "${device.name} | $role | ${device.address} | $status ",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.sp,
                            color = if (device.isMaster)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

        }


    }
}