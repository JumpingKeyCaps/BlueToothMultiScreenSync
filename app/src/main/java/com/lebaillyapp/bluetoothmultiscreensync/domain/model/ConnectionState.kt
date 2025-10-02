package com.lebaillyapp.bluetoothmultiscreensync.domain.model

/**
 * Represents the state of a Bluetooth connection.
 */
sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val throwable: Throwable) : ConnectionState()
}