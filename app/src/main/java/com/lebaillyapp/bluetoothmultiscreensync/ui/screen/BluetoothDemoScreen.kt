package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

private const val TAG = "BTDemoScreen"

/**
 * Composable de test pour la fonctionnalité Bluetooth.
 *
 * Cette UI permet de :
 * 1. Démarrer l'auto-connexion Bluetooth avec d'autres appareils.
 * 2. Envoyer un message test ("Hello BT !").
 * 3. Afficher l'état courant de l'auto-connexion.
 * 4. Afficher le nombre de clients connectés.
 * 5. Lister les messages reçus, les événements de connexion et les erreurs.
 *
 * Le comportement :
 * - Les listes (`messages`, `events`, `errors`) scrollent automatiquement vers le dernier élément.
 * - Les changements de l'état `AutoConnect` sont loggés dans Logcat.
 *
 * Remarques :
 * - Cette composable crée son propre [BluetoothRepository] et [BluetoothViewModel] pour simplifier le POC.
 * - Le [CoroutineScope] local est utilisé pour l'animation du scroll des LazyColumn.
 */
@Composable
fun BluetoothDemoScreen() {
    val context = LocalContext.current

    // Repository & ViewModel
    val repository = remember { BluetoothRepository(context) }
    val viewModel = remember { BluetoothViewModel(repository) }

    // State observers
    val messages by viewModel.messages.collectAsState()
    val events by viewModel.connectionEvents.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val autoState by viewModel.autoConnectState.collectAsState()

    val scope = rememberCoroutineScope()

    // LazyList states
    val messagesState = rememberLazyListState()
    val eventsState = rememberLazyListState()
    val errorsState = rememberLazyListState()

    // --- Auto-scroll effects ---
    /**
     * Scroll automatique vers le dernier message lorsqu'un nouveau message arrive.
     */
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { messagesState.animateScrollToItem(messages.lastIndex) }
        }
    }

    /**
     * Scroll automatique vers le dernier événement lorsqu'un nouvel événement arrive.
     */
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            scope.launch { eventsState.animateScrollToItem(events.lastIndex) }
        }
    }

    /**
     * Scroll automatique vers la dernière erreur lorsqu'une nouvelle erreur arrive.
     */
    LaunchedEffect(errors.size) {
        if (errors.isNotEmpty()) {
            scope.launch { errorsState.animateScrollToItem(errors.lastIndex) }
        }
    }

    // Log autoConnect state changes
    /**
     * Affiche dans le logcat tout changement d'état de l'auto-connexion.
     */
    LaunchedEffect(autoState) {
        Log.d(TAG, "AutoConnectState changed: $autoState")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Action buttons ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            /**
             * Bouton pour démarrer le processus d'auto-connexion Bluetooth.
             * Appelle [BluetoothViewModel.startAutoConnect] et logge l'action.
             */
            Button(onClick = {
                Log.d(TAG, "Start AutoConnect clicked")
                viewModel.startAutoConnect()
            }) {
                Text("Start AutoConnect")
            }

            /**
             * Bouton pour envoyer un message test ("Hello BT !") aux appareils connectés.
             * Appelle [BluetoothViewModel.sendMessage].
             */
            Button(onClick = {
                Log.d(TAG, "Send Hello clicked")
                viewModel.sendMessage("Hello BT !")
            }) {
                Text("Send Hello")
            }
        }

        // Affichage état AutoConnect et clients connectés
        Text("AutoConnect state: $autoState")
        Text("Connected clients: ${viewModel.getConnectedClientsCount()}")
        Divider()

        // --- Messages list ---
        Text("Messages:")
        LazyColumn(
            state = messagesState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { items(messages) { Text(it) } }

        Divider()

        // --- Events list ---
        Text("Events:")
        LazyColumn(
            state = eventsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { items(events) { Text(it.toString()) } }

        Divider()

        // --- Errors list ---
        Text("Errors:")
        LazyColumn(
            state = errorsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { items(errors) { Text(it.message ?: "Unknown error", color = MaterialTheme.colorScheme.error) } }
    }
}
