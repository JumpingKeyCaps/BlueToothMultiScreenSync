package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
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

    fun updatePermissions(granted: Boolean) {
        _permissionsGranted.value = granted
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
