package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.bluetooth.BluetoothSocket
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter

/**
 * Encapsulates a connected Bluetooth RFCOMM socket.
 * Handles reading incoming messages and sending outgoing ones.
 */
class BluetoothConnection(
    private val socket: BluetoothSocket,
    private val scope: CoroutineScope
) {
    private val incomingChannel = Channel<String>(Channel.BUFFERED)
    val incomingMessages: Flow<String> = incomingChannel.receiveAsFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val input: BufferedReader = BufferedReader(InputStreamReader(socket.inputStream))
    private val output: PrintWriter = PrintWriter(OutputStreamWriter(socket.outputStream), true)

    init {
        _connectionState.value = ConnectionState.Connected

        // Start listening for incoming messages
        scope.launch(Dispatchers.IO) {
            try {
                while (scope.isActive) {
                    val line = input.readLine() ?: break
                    incomingChannel.trySend(line).isSuccess
                }
                // if readLine() returns null, socket closed
                _connectionState.value = ConnectionState.Disconnected
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e)
                close()
            }
        }
    }

    /** Send a message to the connected device. */
    fun sendMessage(message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                output.println(message)
                output.flush()
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e)
                close()
            }
        }
    }

    /** Close the socket and channels. */
    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
        incomingChannel.close()
        _connectionState.value = ConnectionState.Disconnected
    }
}