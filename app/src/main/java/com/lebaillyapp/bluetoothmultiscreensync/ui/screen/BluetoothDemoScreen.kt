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

    // Auto-scroll effects
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

    // Log autoConnect state changes
    LaunchedEffect(autoState) {
        Log.d(TAG, "AutoConnectState changed: $autoState")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                Log.d(TAG, "Start AutoConnect clicked")
                viewModel.startAutoConnect()
            }) {
                Text("Start AutoConnect")
            }
            Button(onClick = {
                Log.d(TAG, "Send Hello clicked")
                viewModel.sendMessage("Hello BT !")
            }) {
                Text("Send Hello")
            }
        }

        // AutoConnect state display
        Text("AutoConnect state: $autoState")
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
        ) { items(messages) { Text(it) } }

        Divider()
        // Events list
        Text("Events:")
        LazyColumn(
            state = eventsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { items(events) { Text(it.toString()) } }

        Divider()
        // Errors list
        Text("Errors:")
        LazyColumn(
            state = errorsState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { items(errors) { Text(it.message ?: "Unknown error", color = MaterialTheme.colorScheme.error) } }
    }
}
