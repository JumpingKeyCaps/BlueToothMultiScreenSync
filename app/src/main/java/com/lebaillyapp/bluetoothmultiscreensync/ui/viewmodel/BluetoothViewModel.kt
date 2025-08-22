package com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionManager
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel exposant les fonctionnalités Bluetooth pour l'UI.
 *
 * Ce ViewModel encapsule un [BluetoothRepository] et transforme ses flux pour
 * l'UI en StateFlows lisibles. Il ne dépend pas directement du [Context],
 * ce qui permet de l'utiliser dans un POC ou avec injection de dépendances.
 *
 * Fonctionnalités :
 * - Démarrage de l'auto-connexion
 * - Envoi de messages vers les appareils connectés
 * - Observation des messages reçus, événements de connexion et erreurs
 * - Comptage des clients connectés
 */
class BluetoothViewModel(
    private val repo: BluetoothRepository
) : ViewModel() {

    /** État courant de l'auto-connexion (Idle, Scanning, ServerMode, etc.) */
    val autoConnectState = repo.autoConnectState

    /** Messages reçus depuis les appareils connectés, affichables dans l'UI */
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    /** Événements de connexion (connexion/déconnexion client/server) */
    private val _connectionEvents =
        MutableStateFlow<List<BluetoothConnectionManager.ConnectionEvent>>(emptyList())
    val connectionEvents: StateFlow<List<BluetoothConnectionManager.ConnectionEvent>> =
        _connectionEvents.asStateFlow()

    /** Liste des dernières erreurs survenues lors des opérations Bluetooth */
    private val _errors = MutableStateFlow<List<Throwable>>(emptyList())
    val errors: StateFlow<List<Throwable>> = _errors.asStateFlow()

    init {
        observeRepo()
    }

    /** Observe les flows du repository et met à jour les StateFlows exposés */
    private fun observeRepo() {
        viewModelScope.launch {
            repo.messages.collect { msg ->
                _messages.update { it + msg }
            }
        }
        viewModelScope.launch {
            repo.connectionEvents.collect { ev ->
                _connectionEvents.update { it + ev }
            }
        }
        viewModelScope.launch {
            repo.errors.collect { e ->
                _errors.update { it + e }
            }
        }
    }

    /** Démarre le processus d'auto-connexion Bluetooth */
    fun startAutoConnect() = repo.startAutoConnect()

    /**
     * Envoie un message texte à tous les appareils connectés
     * @param message le contenu à envoyer
     */
    fun sendMessage(message: String) = repo.sendMessage(message)

    /**
     * Envoie un message structuré (BluetoothMessage) à tous les appareils connectés
     * @param message le message structuré
     */
    fun sendMessage(message: BluetoothMessage) = repo.sendMessage(message)

    /** Retourne le nombre d'appareils actuellement connectés */
    fun getConnectedClientsCount(): Int = repo.getConnectedClientsCount()

    /** Arrête toutes les connexions et nettoie le repository */
    override fun onCleared() {
        super.onCleared()
        repo.stopAll()
    }
}
