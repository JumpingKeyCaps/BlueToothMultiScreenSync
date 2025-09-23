package com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundViewModel

/**
 * ## PlaygroundViewModelFactory
 *
 * Factory to create [PlaygroundViewModel] instances.
 * Injects the shared [BluetoothRepository] so that ongoing connections
 * are preserved across navigation.
 *
 * @property repository Shared [BluetoothRepository] instance.
 */
class PlaygroundViewModelFactory(
    private val repository: BluetoothRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaygroundViewModel::class.java)) {
            return PlaygroundViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
