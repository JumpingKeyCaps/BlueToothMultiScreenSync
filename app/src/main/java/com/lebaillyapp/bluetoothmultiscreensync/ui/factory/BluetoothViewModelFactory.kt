package com.lebaillyapp.bluetoothmultiscreensync.ui.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionService
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel

/**
 * Factory manuelle pour créer un [BluetoothViewModel] avec un [Context].
 *
 * Cette factory permet de fournir un [BluetoothRepository] initialisé avec un contexte
 * d'application au ViewModel, évitant ainsi la dépendance directe au contexte dans le ViewModel.
 *
 * Utile quand on ne veut pas utiliser un framework de DI (Hilt/Koin) et qu'on souhaite
 * instancier un ViewModel avec des paramètres personnalisés.
 *
 * Exemple d'utilisation :
 * ```
 * val factory = BluetoothViewModelFactory(context)
 * val viewModel = ViewModelProvider(this, factory)[BluetoothViewModel::class.java]
 * ```
 *
 * @property context Le contexte Android utilisé pour créer le [BluetoothRepository].
 */
class BluetoothViewModelFactory() : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            // 1. Crée le service bas-niveau
            val connectionService = BluetoothConnectionService()

            // 2. Injecte dans le repository
            val repo = BluetoothRepository(connectionService)

            // 3. Crée le ViewModel
            return BluetoothViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
