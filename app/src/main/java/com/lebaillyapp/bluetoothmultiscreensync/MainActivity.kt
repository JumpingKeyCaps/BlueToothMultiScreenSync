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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.BTStatusPulseScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.BluetoothDemoScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.BtScannerWithPermissions
import com.lebaillyapp.bluetoothmultiscreensync.ui.theme.BlueToothMultiScreenSyncTheme

/**
 * Main entry point of the app.
 *
 * Handles:
 * 1- Dynamic request of Bluetooth permissions.
 * 2- Ensures Bluetooth is enabled on device (asks user if needed).
 * 3- Sets the Compose UI with [BluetoothDemoScreen] for testing BT features.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1- Request Bluetooth permissions dynamically
   //     requestBtPermissions()

        // 2- Ensure Bluetooth is enabled
   //     ensureBluetoothEnabled()

        // 3- Set the main Compose UI
        setContent {
            BlueToothMultiScreenSyncTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Test BT feature flow !
                   // BluetoothDemoScreen()
                  //  BTStatusPulseScreen()

                    BtScannerWithPermissions(this@MainActivity)
                }
            }
        }
    }

    /**
     * Requests required Bluetooth permissions depending on Android version.
     *
     * Android 12+ requires [Manifest.permission.BLUETOOTH_CONNECT] and [Manifest.permission.BLUETOOTH_SCAN].
     * Older versions use [Manifest.permission.BLUETOOTH] and [Manifest.permission.BLUETOOTH_ADMIN].
     *
     * Permissions are requested at runtime using [ActivityCompat.requestPermissions].
     */
    private fun requestBtPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, perms, 0)

    }





    /**
     * Ensures Bluetooth is enabled on the device.
     *
     * If Bluetooth is not supported, prints a log.
     * If Bluetooth is disabled, sends an [Intent] to request the user to enable it.
     *
     * Requires [Manifest.permission.BLUETOOTH_CONNECT] on Android 12+.
     */
    private fun ensureBluetoothEnabled() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            println("Bluetooth not supported on this device")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // check permission only on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                println("BLUETOOTH_CONNECT permission missing, cannot enable BT programmatically")
                return
            }

            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableIntent)
        }
    }




}





