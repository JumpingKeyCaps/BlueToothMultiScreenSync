package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lebaillyapp.bluetoothmultiscreensync.R
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ServerState
import com.lebaillyapp.bluetoothmultiscreensync.utils.FractalVisualizer
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateInt
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow

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

    val subtitle = when (selectedRole) {
        RoleViewModel.Role.Server -> "Ready to host a Playground !"
        RoleViewModel.Role.Client -> "Select a playground device to connect"
        else -> "No role selected"
    }

    val titleMain = when (selectedRole) {
        RoleViewModel.Role.Server -> "Bluetooth server mode"
        RoleViewModel.Role.Client -> "Bluetooth client mode"
        else -> "Bluetooth Role Selection"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(120.dp),
                title = {
                    Column {
                        Text(modifier = Modifier.padding(top = 26.dp),text = titleMain, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                    }
                },
                actions = {
                    val isServer = selectedRole == RoleViewModel.Role.Server
                    IconSwitch(
                        modifier = Modifier.padding(top = 36.dp,end = 26.dp),
                        checked = isServer,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                }
                                discoverabilityLauncher.launch(intent)
                            } else {
                                roleViewModel.selectRole(RoleViewModel.Role.Client)
                            }
                        }
                    )
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp),
                contentAlignment = Alignment.TopCenter
            ){

            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {


            // GRID pour clients
            if (selectedRole == RoleViewModel.Role.Client) {

                Box(modifier = Modifier.align(Alignment.Center)) {
                    FractalVisualizer(
                        modifier = Modifier
                            .padding(0.dp)
                            .fillMaxSize(),
                        glowColor = Color(0xFF57617E),
                        maxRadius = 380.dp,
                        pointCount = 60,
                        spiralFrequency = 3f,
                        internalOscillationFreq = 34800f / 100,
                        rotationSpeed = 1.0f,
                        animationDurationMs = 100000,
                        isAnimating = true
                    )
                }


                if (scannedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Scanning around for devices...")

                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(scannedDevices, key = { it.address }) { device ->
                            // Interaction source pour l'effet lift
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val elevation by animateDpAsState(if (isPressed) 12.dp else 4.dp)

                            Card(
                                onClick = { roleViewModel.connectToDevice(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                interactionSource = interactionSource
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                ) {
                                    // Cercle bond status
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp, top = 6.dp)
                                            .size(6.dp)
                                            .background(
                                                color = if (device.bondState == BluetoothDevice.BOND_BONDED) Color(0xFF19FFC4)
                                                else Color(0xFFFF145E),
                                                shape = CircleShape
                                            )
                                            .align(Alignment.TopEnd)
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 6.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Top
                                    ) {
                                        Text(
                                            device.name ?: "Unknown",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            // Icon sous l'adresse
                                            Icon(
                                                painter = painterResource(id = R.drawable.bluetooth_ico),
                                                contentDescription = "Device icon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Infos à côté de l'icône
                                            Column {
                                                Text("Type: ${deviceTypeString(device.type)}", style = MaterialTheme.typography.labelSmall)
                                                Text("Bond: ${bondStateString(device.bondState)}", style = MaterialTheme.typography.labelSmall)

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Server connected details
            if (selectedRole == RoleViewModel.Role.Server && serverState is ServerState.Connected) {
                val device = (serverState as ServerState.Connected).device
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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

            // Infinite progress indicator when Server is listening
            if (selectedRole == RoleViewModel.Role.Server && serverState is ServerState.Listening) {

               Column(modifier = Modifier.fillMaxWidth()
                   .padding(16.dp)
                   .align(Alignment.BottomCenter),
                   horizontalAlignment = Alignment.CenterHorizontally){
                   Text(text = "Listening for incoming connections...",
                       style = MaterialTheme.typography.labelLarge,
                       color = MaterialTheme.colorScheme.onSurfaceVariant)
                   LinearProgressIndicator(
                       modifier = Modifier
                           .width(150.dp)
                           .padding(start = 5.dp, end = 5.dp, bottom = 80.dp,top = 20.dp)
                           .height(2.dp),
                       color = Color(0xFF2979FF)
                   )

               }



                Box(modifier = Modifier.align(Alignment.Center)) {
                    FractalVisualizer(
                        modifier = Modifier
                            .padding(0.dp)
                            .fillMaxSize(),
                        glowColor = Color(0xFF2979FF),
                        maxRadius = 380.dp,
                        pointCount = 80,
                        spiralFrequency = 8f,
                        internalOscillationFreq = 19800f / 100,
                        rotationSpeed = 2.0f,
                        animationDurationMs = 100000,
                        isAnimating = true
                    )
                }
            }
        }
    }
}

@Composable
fun IconSwitch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val transition = updateTransition(targetState = checked, label = "switchTransition")

    val thumbOffset by transition.animateDp(label = "thumbOffset") { if (it) 22.dp else 0.dp }
    val trackColor by transition.animateColor(label = "trackColor") { if (it) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant }
    val thumbColor by transition.animateColor(label = "thumbColor") { if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary }
    val iconRes by transition.animateInt(label = "iconRes") { if (it) R.drawable.server_ico else R.drawable.client_ico }

    Box(
        modifier = modifier
            .width(50.dp)
            .height(30.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackColor, shape = RoundedCornerShape(15.dp))
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .background(thumbColor, CircleShape)
                .indication(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Utils
fun deviceTypeString(type: Int): String = when (type) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
    BluetoothDevice.DEVICE_TYPE_LE -> "LE"
    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
    else -> "Unknown"
}

fun bondStateString(state: Int): String = when (state) {
    BluetoothDevice.BOND_NONE -> "None"
    BluetoothDevice.BOND_BONDING -> "Bonding"
    BluetoothDevice.BOND_BONDED -> "Bonded"
    else -> "Unknown"
}
