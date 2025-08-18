package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

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
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionManager
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

/**
 * Demo screen for testing Bluetooth functionality.
 *
 * Shows buttons to start auto-connect and send a test message.
 * Displays received messages, connection events, and errors in scrollable lists.
 *
 * This composable automatically scrolls to the latest item in each list when new data arrives.
 */
@Composable
fun BluetoothDemoScreen() {
    val context = LocalContext.current

    // Create repository and viewmodel
    val repository = remember { BluetoothRepository(context) }
    val viewModel = remember { BluetoothViewModel(repository) }

    // Observe state from ViewModel
    val messages by viewModel.messages.collectAsState()
    val events by viewModel.connectionEvents.collectAsState()
    val errors by viewModel.errors.collectAsState()

    // LazyList states for auto-scroll
    val messagesState = rememberLazyListState()
    val eventsState = rememberLazyListState()
    val errorsState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll whenever new items are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { messagesState.animateScrollToItem(messages.lastIndex) }
        }
    }
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            scope.launch { eventsState.animateScrollToItem(events.lastIndex) }
        }
    }
    LaunchedEffect(errors.size) {
        if (errors.isNotEmpty()) {
            scope.launch { errorsState.animateScrollToItem(errors.lastIndex) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.startAutoConnect() }) {
                Text("Start AutoConnect")
            }
            Button(onClick = { viewModel.sendMessage("Hello BT !") }) {
                Text("Send Hello")
            }
        }

        // Connected clients count
        Text("Connected clients: ${viewModel.getConnectedClientsCount()}")
        Divider()

        // Messages list
        Text("Messages:")
        LazyColumn(
            state = messagesState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { msg ->
                Text(msg)
            }
        }

        Divider()
        // Events list
        Text("Events:")
        LazyColumn(
            state = eventsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(events) { ev ->
                Text(ev.toString())
            }
        }

        Divider()
        // Errors list
        Text("Errors:")
        LazyColumn(
            state = errorsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(errors) { err ->
                Text(err.message ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
