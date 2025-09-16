package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel
import com.lebaillyapp.bluetoothmultiscreensync.data.service.old.BluetoothAutoConnector
import kotlin.math.PI
import kotlin.math.cos

private const val TAG = "BTStatusPulseScreen"

/**
 * Composable full-screen minimaliste avec gradient pulsant et overlay d’état Bluetooth.
 *
 * - Orange : Scanning
 * - Vert : Slave connected
 * - Bleu : Master / serveur
 * - Rouge : Erreur
 *
 * Le gradient pulse doucement entre nuances proches pour effet respirant.
 * L’overlay en haut à gauche montre AutoConnect state + clients connectés.
 * Status bar et nav bar suivent le gradient.
 */
@Composable
fun BTStatusPulseScreen(){

    /**

    val context = LocalContext.current
    val view = LocalView.current
    val activity = view.context as? Activity
    val repository = remember { BluetoothRepository(context) }
    val viewModel = remember { BluetoothViewModel(repository) }
    val autoState by viewModel.autoConnectState.collectAsState()
    val clientsCount = viewModel.getConnectedClientsCount()

    // Auto-start auto-connect
    LaunchedEffect(Unit) {
        Log.d(TAG, "AutoConnect auto-start")
        viewModel.startAutoConnect()
    }

    // Écoute changements Bluetooth
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    DisposableEffect(bluetoothAdapter) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "Bluetooth activé, relance AutoConnect")
                        viewModel.startAutoConnect()
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- Extraire un état lisible et couleur associée ---
    val (stateText, targetColor) = when (autoState) {
        is BluetoothAutoConnector.AutoConnectState.Idle -> "Idle" to Color(0xFF666666)
        is BluetoothAutoConnector.AutoConnectState.Scanning -> "Scanning" to Color(0xFFFFA500)
        is BluetoothAutoConnector.AutoConnectState.TestingServices -> "Testing" to Color(0xFFFFC870)
        is BluetoothAutoConnector.AutoConnectState.Connecting -> {
            val device = (autoState as BluetoothAutoConnector.AutoConnectState.Connecting).device
            val devName = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) "unknown"
                else device.name ?: "unknown"
            } catch (_: SecurityException) {
                "unknown"
            }
            "Connecting to $devName" to Color(0xFF00C0FF)
        }
        is BluetoothAutoConnector.AutoConnectState.ServerMode -> "Server mode" to Color(0xFF601EEF)
        is BluetoothAutoConnector.AutoConnectState.Error -> {
            val reason = (autoState as BluetoothAutoConnector.AutoConnectState.Error).reason
            "Error: $reason" to Color(0xFFFF1744)
        }
    }

    fun pulseShade(color: Color, factor: Float = 0.15f) = Color(
        red = (color.red + factor).coerceIn(0f, 1f),
        green = (color.green + factor).coerceIn(0f, 1f),
        blue = (color.blue + factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )

    // Animation de pulse smooth
    val infiniteTransition = rememberInfiniteTransition()
    val animatedFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val smoothFraction = (cos(animatedFraction * PI).toFloat() + 1f) / 2f
    val pulseColor = lerp(targetColor, pulseShade(targetColor, 0.15f), smoothFraction)

    // Status bar & navigation bar follow gradient
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            window.statusBarColor = pulseColor.toArgb()
            window.navigationBarColor = pulseColor.toArgb()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(pulseColor.copy(alpha = 0.85f), pulseColor)
                )
            )
    ) {
        // Overlay info
        Column(
            modifier = Modifier
                .padding(56.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "State: $stateText",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp
            )
            Text(
                text = "Clients: $clientsCount",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }

    // Log debug
    LaunchedEffect(animatedFraction, autoState, clientsCount) {
        Log.d(TAG, "State=$stateText, clients=$clientsCount, pulseFraction=$smoothFraction, pulseColor=$pulseColor")
    }


    */


}
