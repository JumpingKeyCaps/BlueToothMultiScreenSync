package com.lebaillyapp.bluetoothmultiscreensync.ui.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
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
class BluetoothViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    /**
     * Crée une instance du ViewModel demandé si le type est compatible.
     *
     * @param T Le type de ViewModel à créer.
     * @param modelClass La classe du ViewModel demandé.
     * @return Une instance du ViewModel demandé.
     * @throws IllegalArgumentException si la classe demandée n'est pas [BluetoothViewModel].
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            val repo = BluetoothRepository(context.applicationContext)
            return BluetoothViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
