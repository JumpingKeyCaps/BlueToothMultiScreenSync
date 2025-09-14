package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * ## BluetoothRepository
 * Repository unique pour la gestion des opérations Bluetooth.
 * Il centralise les interactions avec [BluetoothConnectionService]
 * et expose les messages et événements de connexion à la ViewModel.
 *
 * @property connectionService Service responsable de la gestion des connexions Bluetooth.
 */
class BluetoothRepository(
    private val connectionService: BluetoothConnectionService
) {

    /**
     * Scope de coroutine pour les opérations de forwarding et autres tâches asynchrones.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Messages ---
    /**
     * Flux interne pour diffuser les messages reçus depuis les pairs connectés.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /**
     * Flux public pour observer les messages entrants.
     */
    val messages: SharedFlow<String> = _messages

    // --- Connection events ---
    /**
     * Flux interne pour diffuser les événements de connexion.
     */
    private val _connectionEvents = MutableSharedFlow<BluetoothConnectionService.ConnectionEvent>(extraBufferCapacity = 16)
    /**
     * Flux public pour observer les événements de connexion.
     */
    val connectionEvents: SharedFlow<BluetoothConnectionService.ConnectionEvent> = _connectionEvents

    /**
     * ## init
     * Forward les messages et événements du service vers les flux du repository.
     */
    init {
        // Forward messages
        scope.launch {
            connectionService.incomingMessages.collect { msg ->
                _messages.emit(msg)
            }
        }

        // Forward connection events
        scope.launch {
            connectionService.connectionEvents.collect { event ->
                _connectionEvents.emit(event)
            }
        }
    }

    // --- Actions exposées à la ViewModel ---
    /**
     * ## startServer
     * Lance le service en mode serveur, prêt à accepter des connexions entrantes.
     */
    fun startServer() = connectionService.startServer()

    /**
     * ## connectToPeer
     * Se connecte à un pair Bluetooth spécifique.
     *
     * @param peer Le pair à connecter.
     */
    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) {
        connectionService.connectToPeer(peer)
    }

    /**
     * ## sendMessage
     * Envoie un message à tous les pairs connectés.
     *
     * @param msg Message à envoyer.
     * @param excludeId ID d’un pair à exclure de l’envoi (optionnel).
     */
    fun sendMessage(msg: String, excludeId: String? = null) {
        connectionService.sendMessage(msg, excludeId)
    }

    /**
     * ## stopAll
     * Arrête toutes les connexions actives et réinitialise l’état du service.
     */
    fun stopAll() {
        connectionService.stopAll()
    }

    /**
     * ## cleanup
     * Nettoie toutes les ressources du repository et annule son scope.
     * À appeler lorsque la ViewModel ou le composant parent n’est plus actif.
     */
    fun cleanup() {
        stopAll()
        scope.cancel()
    }
}
