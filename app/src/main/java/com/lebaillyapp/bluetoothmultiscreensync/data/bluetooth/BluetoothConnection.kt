package com.lebaillyapp.bluetoothmultiscreensync.data.bluetooth

import android.bluetooth.BluetoothSocket
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter

/**
 * ## BluetoothConnection
 *
 * Encapsulates an active Bluetooth RFCOMM socket.
 * Provides a [Flow] of incoming messages and allows sending messages over the socket.
 * Tracks connection state via [connectionState].
 *
 * ### Features:
 * - Reads incoming messages line by line in a background coroutine.
 * - Sends outgoing messages asynchronously.
 * - Updates connection state: [Connected], [Disconnected], or [Error].
 * - Automatically closes the socket and internal channels on error or disconnect.
 *
 *
 * @property socket The connected [BluetoothSocket] instance.
 * @property scope The [CoroutineScope] used for background read/write operations.
 */
class BluetoothConnection(
    private val socket: BluetoothSocket,
    private val scope: CoroutineScope
) {

    private val incomingChannel = Channel<String>(Channel.BUFFERED)

    /**
     * Flow of incoming messages from the connected Bluetooth device.
     * Each message is a line terminated by newline.
     */
    val incomingMessages: Flow<String> = incomingChannel.receiveAsFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    /**
     * StateFlow representing the current connection state.
     * Possible values: [ConnectionState.Connecting], [Connected], [Disconnected], [Error].
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val input: BufferedReader = BufferedReader(InputStreamReader(socket.inputStream))
    private val output: PrintWriter = PrintWriter(OutputStreamWriter(socket.outputStream), true)

    init {
        _connectionState.value = ConnectionState.Connected

        // Launch a coroutine to read incoming messages continuously
        scope.launch(Dispatchers.IO) {
            try {
                while (scope.isActive) {
                    val line = input.readLine() ?: break
                    incomingChannel.trySend(line).isSuccess
                }
                // If readLine() returns null, the socket was closed
                _connectionState.value = ConnectionState.Disconnected
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e)
                close()
            }
        }
    }

    /**
     * Sends a message to the connected Bluetooth device asynchronously.
     *
     * @param message The string message to send. Each call appends a newline.
     */
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

    /**
     * Closes the socket and internal channels.
     * Updates [connectionState] to [Disconnected].
     */
    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
        incomingChannel.close()
        _connectionState.value = ConnectionState.Disconnected
    }
}
