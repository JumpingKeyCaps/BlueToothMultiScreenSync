package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.content.Context
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothClient
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * High-level manager for handling both server and client Bluetooth operations
 * in the BlueToothMultiScreenSync project.
 *
 * This class provides a unified interface to:
 *  - Start a local server to accept incoming connections
 *  - Connect to a remote server as a client
 *  - Send messages to all connected peers (both server and client)
 *  - Observe incoming messages via a merged Flow
 *
 * @param context Android context, required for client initialization and permission checks
 */
class BluetoothManager(context: Context) {

    /** The Bluetooth server service instance */
    private val server = BluetoothService()

    /** The Bluetooth client instance */
    private val client = BluetoothClient(context)

    /**
     * Flow emitting all incoming messages from both server and client.
     * This allows the UI or ViewModel to observe messages from any peer.
     */
    val messages: Flow<String> = merge(
        server.messageFlow,
        client.incomingMessages
    )

    /**
     * Starts the server to accept incoming Bluetooth connections.
     */
    fun startServer() = server.startServer()

    /**
     * Connects to a remote Bluetooth server as a client.
     *
     * @param device The remote BluetoothDevice to connect to
     */
    fun connectToServer(device: android.bluetooth.BluetoothDevice) = client.connectToServer(device)

    /**
     * Sends a message to all connected peers.
     *
     * Both the server's connected clients and the client connection (if active) will receive it.
     *
     * @param message The message string to send
     */
    fun sendMessage(message: String) {
        server.sendMessageToAll(message)
        client.sendMessage(message)
    }

    /**
     * Stops the server and client, closes all connections, and cancels active coroutines.
     */
    fun stopAll() {
        server.onDestroy()
        client.disconnect()
    }
}
