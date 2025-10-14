package com.lebaillyapp.bluetoothmultiscreensync

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.config.ViewportConfig
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import com.lebaillyapp.bluetoothmultiscreensync.navigation.AppNavGraph
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.PlaygroundSettingsScreen

import com.lebaillyapp.bluetoothmultiscreensync.ui.theme.BlueToothMultiScreenSyncTheme
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundSettingsViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory.PlaygroundSettingsViewModelFactory
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory.SetupViewModelFactory
import java.util.UUID


/**
 * ## BluetoothConstants
 *
 * Holds constant values related to Bluetooth Classic SPP used throughout the app.
 */
object BluetoothConstants {
    /**
     * Standard Serial Port Profile (SPP) UUID for Bluetooth Classic communication.
     * Used by both server and client for RFCOMM socket creation.
     */
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP standard
}
/**
 * ## MainActivity
 *
 * Entry point of the **BlueToothMultiScreenSync** app.
 * Responsible for:
 * 1. Setting up the app theme and Compose content.
 * 2. Initializing the Bluetooth adapter.
 * 3. Creating a singleton [BluetoothRepository] scoped to the activity for sharing
 *    Bluetooth state across multiple screens and ViewModels.
 * 4. Setting up the Navigation Graph and injecting required ViewModels.
 *
 * ### Notes:
 * - The repository is currently activity-scoped for simplicity during the POC.
 *   In production, it should ideally be scoped to the application to survive
 *   configuration changes and activity recreation.
 * - [ViewModel] are initialized with a factory (no DI in this POC)
 *
 * ### Navigation Flow:
 * - **SetupScreen**: Handles permissions and Bluetooth activation.
 * - **RoleSelectionScreen**: Lets the user select Master/Slave role.
 * - **PlaygroundScreen**: Displays the main interactive virtual canvas.
 *
 * All screens share the same [BluetoothRepository] instance ensuring ongoing
 * connections and message flows survive ViewModel recreation and navigation.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val repository = BluetoothRepository(
            context = applicationContext,
            adapter = bluetoothAdapter,
            serviceUUID = BluetoothConstants.SERVICE_UUID
        )

        setContent {
            BlueToothMultiScreenSyncTheme {
                val navController = rememberNavController()
                val setupViewModel: SetupViewModel = viewModel(
                    factory = SetupViewModelFactory(
                        repository = repository,
                        bluetoothAdapter = bluetoothAdapter,
                        application = application
                    )
                )

                //todo -- real flow -  ------------------------

                AppNavGraph(
                    navController = navController,
                    setupViewModel = setupViewModel,
                    repository = repository,
                    bluetoothAdapter = bluetoothAdapter,
                )






            }
        }
    }
}





