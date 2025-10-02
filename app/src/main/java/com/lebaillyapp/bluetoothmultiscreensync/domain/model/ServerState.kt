package com.lebaillyapp.bluetoothmultiscreensync.domain.model

import android.bluetooth.BluetoothDevice

/**
 * Represents the current state of the Bluetooth server.
 *
 * ### States:
 * - [Stopped] → server is not running.
 * - [Listening] → server is running and waiting for incoming connections.
 * - [Connected] → a client has successfully connected. Provides the connected [BluetoothDevice].
 * - [Error] → an error occurred while running the server. Provides the [Throwable].
 *
 * ### Usage:
 * Exposed as a [StateFlow] in the [RoleViewModel] for UI consumption.
 * The UI can display feedback based on the current state (e.g., show "Listening..." or "Client connected").
 */
sealed class ServerState {
    /** Server is not running. */
    object Stopped : ServerState()

    /** Server is running and waiting for incoming connections. */
    object Listening : ServerState()

    /** A client has connected successfully.
     * @param device The [BluetoothDevice] that is connected.
     */
    data class Connected(val device: BluetoothDevice) : ServerState()

    /** An error occurred while running the server.
     * @param throwable The exception representing the error.
     */
    data class Error(val throwable: Throwable) : ServerState()
}