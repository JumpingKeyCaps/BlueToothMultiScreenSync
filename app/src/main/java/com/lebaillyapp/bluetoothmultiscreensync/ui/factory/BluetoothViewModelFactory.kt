package com.lebaillyapp.bluetoothmultiscreensync.ui.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel

/**
 * Factory manuelle pour créer un [BluetoothViewModel] avec un [Context].
 *
 * Utile quand on ne veut pas utiliser de DI framework (Hilt/Koin).
 */
class BluetoothViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            val repo = BluetoothRepository(context.applicationContext)
            return BluetoothViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}