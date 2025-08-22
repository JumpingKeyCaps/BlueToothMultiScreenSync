package com.lebaillyapp.bluetoothmultiscreensync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.ui.screen.BluetoothDemoScreen
import com.lebaillyapp.bluetoothmultiscreensync.ui.theme.BlueToothMultiScreenSyncTheme
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Bluetooth permissions dynamically
        requestBtPermissions()

        setContent {
            BlueToothMultiScreenSyncTheme {
                Column(modifier = Modifier.fillMaxSize().padding(top = 36.dp)) {
                    // Test BT feature !
                    BluetoothDemoScreen()
                }
            }
        }
    }

    private fun requestBtPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        ActivityCompat.requestPermissions(this, perms, 0)
    }
}
