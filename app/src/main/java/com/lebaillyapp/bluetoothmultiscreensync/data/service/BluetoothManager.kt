package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.content.Context
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothClient
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class BluetoothManager(context: Context) {

    private val server = BluetoothService()
    private val client = BluetoothClient(context)

    val messages: Flow<String> = merge(
        server.messageFlow,
        client.incomingMessages
    )

    fun startServer() = server.startServer()
    fun connectToServer(device: android.bluetooth.BluetoothDevice) = client.connectToServer(device)
    fun sendMessage(message: String) {
        server.sendMessageToAll(message)
        client.sendMessage(message)
    }

    fun stopAll() {
        server.onDestroy()
        client.disconnect()
    }
}