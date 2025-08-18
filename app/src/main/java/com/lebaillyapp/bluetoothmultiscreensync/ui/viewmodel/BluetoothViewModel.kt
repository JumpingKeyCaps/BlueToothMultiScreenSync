package com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionManager
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Pure ViewModel wrapping BluetoothRepository for UI consumption.
 *
 * No direct dependency on Context — repository must be provided
 * (via DI framework or manual factory).
 */
class BluetoothViewModel(
    private val repo: BluetoothRepository
) : ViewModel() {

    /** Auto-connect state (idle, scanning, connected, etc.) */
    val autoConnectState = repo.autoConnectState

    /** Messages received from connected peers */
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    /** Connection events (client/server connection changes) */
    private val _connectionEvents =
        MutableStateFlow<List<BluetoothConnectionManager.ConnectionEvent>>(emptyList())
    val connectionEvents: StateFlow<List<BluetoothConnectionManager.ConnectionEvent>> =
        _connectionEvents.asStateFlow()

    /** Latest errors collected */
    private val _errors = MutableStateFlow<List<Throwable>>(emptyList())
    val errors: StateFlow<List<Throwable>> = _errors.asStateFlow()

    init {
        observeRepo()
    }

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

    fun startAutoConnect() = repo.startAutoConnect()

    fun sendMessage(message: String) = repo.sendMessage(message)

    fun sendMessage(message: BluetoothMessage) = repo.sendMessage(message)

    fun getConnectedClientsCount(): Int = repo.getConnectedClientsCount()

    override fun onCleared() {
        super.onCleared()
        repo.stopAll()
    }
}