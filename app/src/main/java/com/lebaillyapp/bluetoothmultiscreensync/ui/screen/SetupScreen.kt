package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel
import com.lebaillyapp.bluetoothmultiscreensync.R
/**
 * ## SetupScreen
 *
 * Composable UI step that ensures all prerequisites for Bluetooth communication
 * are satisfied before entering the rest of the app.
 *
 * ### Responsibilities
 * - Requests **runtime permissions** required for Bluetooth Classic.
 *   - Android 12+ (API 31+): `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION`
 *   - Below Android 12: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`
 * - Ensures **Bluetooth is enabled** on the device by launching the system intent
 *   `BluetoothAdapter.ACTION_REQUEST_ENABLE`.
 * - Ensures **Location Services** are enabled, since scanning requires it.
 *   Opens system location settings (`Settings.ACTION_LOCATION_SOURCE_SETTINGS`).
 * - Observes [SetupViewModel] state (`permissionsGranted`, `bluetoothEnabled`,
 *   `locationEnabled`, `readyToProceed`).
 * - When all conditions are met, triggers the [onReady] callback to allow navigation
 *   to the next screen.
 *
 * ### UI Behavior
 * - Displays a column of action buttons:
 *   - **Request Permissions**
 *   - **Enable Bluetooth**
 *   - **Enable Location**
 * - Each button becomes enabled only if the prerequisite condition is not yet satisfied.
 * - Shows a success message `"Tout est prêt !"` once all requirements are fulfilled.
 *
 *
 * @param viewModel [SetupViewModel] providing state and update logic for setup flow.
 * @param onReady Callback invoked once all prerequisites are satisfied.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onReady: () -> Unit
) {
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsState()
    val locationEnabled by viewModel.locationEnabled.collectAsState()
    val ready by viewModel.readyToProceed.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.updatePermissions(results.all { it.value })
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshStates() }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshStates() }

    LaunchedEffect(ready) {
        if (ready) onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Texte stylé / animé ---
            val allGood = permissionsGranted && bluetoothEnabled && locationEnabled
            val message by animateFloatAsState(
                targetValue = if (allGood) 1f else 0.7f,
                animationSpec = tween(800),
                label = "alphaAnim"
            )

            val text = when {
                allGood -> "Everything’s ready, let’s connect!"
                !allGood && !permissionsGranted -> "Grant permissions to continue."
                permissionsGranted && !bluetoothEnabled -> "Enable Bluetooth now!"
                permissionsGranted && bluetoothEnabled && !locationEnabled -> "Almost there, enable Location!"
                permissionsGranted && locationEnabled -> "Almost done — turn on Bluetooth!"
                bluetoothEnabled && locationEnabled -> "Just grant permissions to finish!"
                permissionsGranted && !locationEnabled -> "Grant location permissions to continue."

                else -> "Let’s get your device ready for connection."
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .alpha(message)
                    .padding(bottom = 48.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // Ligne 1 → Permissions
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                RoundStateButton(
                    iconRes = if (permissionsGranted) R.drawable.ok_ico else R.drawable.phone_ico,
                    isActive = permissionsGranted,
                    onClick = {

                        if (!permissionsGranted){
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH,
                                    Manifest.permission.BLUETOOTH_ADMIN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            }
                            permissionLauncher.launch(perms)
                        }


                    }
                )
            }

            Spacer(modifier = Modifier.height(38.dp))

            // Ligne 2 → Bluetooth + Location
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                RoundStateButton(
                    iconRes = if (bluetoothEnabled) R.drawable.bluetooth_ico else R.drawable.bluetooth_disabled,
                    isActive = bluetoothEnabled,
                    onClick = {
                        if (!bluetoothEnabled){
                            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }

                    }
                )

                RoundStateButton(
                    iconRes = if (locationEnabled) R.drawable.my_location else R.drawable.location_disabled,
                    isActive = locationEnabled,
                    onClick = {
                        if (!locationEnabled){
                            locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun RoundStateButton(
    iconRes: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scaleAnim"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "bgAnim"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tintAnim"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(70.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isActive) 8.dp else 2.dp,
                shape = CircleShape,
                clip = true
            )
            .clip(CircleShape)
            .background(bgColor)
            .indication(
                interactionSource = interactionSource,
                indication = rememberRippleOrMaterial3()
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Wrapper utilitaire pour éviter les warnings sur rememberRipple()
 * et rester compatible Material3.
 */
@Composable
private fun rememberRippleOrMaterial3(): Indication {
    // Si Material3 est dispo → on crée un ripple via le thème Material3
    return LocalIndication.current // safe + clean + conforme
}