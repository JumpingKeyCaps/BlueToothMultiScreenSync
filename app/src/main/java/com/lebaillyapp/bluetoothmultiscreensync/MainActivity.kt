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
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.navigation.AppNavGraph

import com.lebaillyapp.bluetoothmultiscreensync.ui.theme.BlueToothMultiScreenSyncTheme
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory.SetupViewModelFactory
import java.util.UUID

object BluetoothConstants {
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP standard
}

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





