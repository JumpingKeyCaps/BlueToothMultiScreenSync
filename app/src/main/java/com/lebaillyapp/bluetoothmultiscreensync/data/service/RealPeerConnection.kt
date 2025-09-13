package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * ## RealPeerConnection
 * A concrete implementation of [BluetoothConnectionService.PeerConnection] that uses a
 * standard [BluetoothSocket] for communication. It manages message sending and receiving
 * over the Bluetooth connection.
 *
 * @param socket The [BluetoothSocket] representing the established connection to the peer.
 */
class RealPeerConnection(
    private val socket: BluetoothSocket
) : BluetoothConnectionService.PeerConnection(
    id = socket.remoteDevice.address,
    messages = MutableSharedFlow(extraBufferCapacity = 64)
) {

    /**
     * A coroutine scope specifically for managing the lifecycle of coroutines related
     * to this connection, ensuring they are cancelled when the connection is closed.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /**
     * A reference to the mutable flow for incoming messages, allowing messages to be emitted.
     */
    private val msgFlow = messages as MutableSharedFlow<String>
    /**
     * The output stream from the Bluetooth socket, used for sending data.
     */
    private val outStream = socket.outputStream
    /**
     * The input stream from the Bluetooth socket, used for receiving data.
     */
    private val inStream = socket.inputStream

    /**
     * ## init
     * An initializer block that starts a coroutine to continuously read data from the
     * input stream. Messages are read in a loop and emitted to the `msgFlow`.
     * Any `IOException` will stop the loop, effectively handling disconnection.
     */
    init {
        scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val bytesRead = inStream.read(buffer)
                    if (bytesRead > 0) {
                        val msg = String(buffer, 0, bytesRead)
                        msgFlow.emit(msg)
                    }
                }
            } catch (_: IOException) {} // Ignore exceptions during a read, which typically signals a disconnect.
        }
    }

    /**
     * ## send
     * Sends a message to the connected peer. The message is converted to a byte array and
     * written to the output stream.
     *
     * @param message The string message to be sent.
     */
    override suspend fun send(message: String) {
        try {
            outStream.write(message.toByteArray())
            outStream.flush()
        } catch (_: IOException) {} // Ignore exceptions during a write, which typically signals a disconnect.
    }

    /**
     * ## close
     * Closes the Bluetooth socket, which terminates the connection.
     * It also cancels the coroutine scope, stopping any ongoing operations.
     */
    override fun close() {
        try { socket.close() } catch (_: Exception) {} // Ignore any exceptions during socket closure.
        scope.cancel()
    }
}