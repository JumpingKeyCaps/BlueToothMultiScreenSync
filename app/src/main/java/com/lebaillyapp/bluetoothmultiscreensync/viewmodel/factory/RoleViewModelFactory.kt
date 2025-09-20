package com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.RoleViewModel

class RoleViewModelFactory(
    private val repository: BluetoothRepository,
    private val bluetoothAdapter: BluetoothAdapter
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoleViewModel::class.java)) {
            return RoleViewModel(repository, bluetoothAdapter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}