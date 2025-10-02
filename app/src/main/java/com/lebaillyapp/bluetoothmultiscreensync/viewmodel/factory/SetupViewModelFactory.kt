package com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.SetupViewModel

class SetupViewModelFactory(
    private val application: Application,
    private val repository: BluetoothRepository,
    private val bluetoothAdapter: BluetoothAdapter
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
            return SetupViewModel(
                application = application,
                repository = repository,
                bluetoothAdapter = bluetoothAdapter
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}