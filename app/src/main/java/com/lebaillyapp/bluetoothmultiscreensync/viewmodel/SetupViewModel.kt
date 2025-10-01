package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ## SetupViewModel
 * Vérifie les prérequis Bluetooth & Location et expose un état prêt pour l'UI.
 */
class SetupViewModel(
    application: Application,                // <- Application context
    private val repository: BluetoothRepository,
    private val bluetoothAdapter: BluetoothAdapter
) : AndroidViewModel(application) {

    private val context = getApplication<Application>()  // safe context

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(bluetoothAdapter.isEnabled)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _locationEnabled = MutableStateFlow(isLocationEnabled())
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    val readyToProceed: StateFlow<Boolean> = combine(
        permissionsGranted,
        bluetoothEnabled,
        locationEnabled
    ) { perms, bt, loc -> perms && bt && loc }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)


    init {
        _permissionsGranted.value = checkPermissions()
    }

    private fun checkPermissions(): Boolean {
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
        return perms.all { perm ->
            context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun updatePermissions(granted: Boolean) {
        _permissionsGranted.value = granted
        if (granted) {
            // Double check
            _permissionsGranted.value = checkPermissions()
        }
    }

    fun updateBluetoothState(enabled: Boolean) {
        _bluetoothEnabled.value = enabled
    }

    fun updateLocationState(enabled: Boolean) {
        _locationEnabled.value = enabled
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /** Optionnel : peut relancer des checks périodiques ou triggers si besoin */
    fun refreshStates() {
        _bluetoothEnabled.value = bluetoothAdapter.isEnabled
        _locationEnabled.value = isLocationEnabled()
    }
}
