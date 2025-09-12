package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException

class RealPeerConnection(
    private val socket: BluetoothSocket
) : BluetoothConnectionService.PeerConnection(
    id = socket.remoteDevice.address,
    messages = MutableSharedFlow(extraBufferCapacity = 64)
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val msgFlow = messages as MutableSharedFlow<String>
    private val outStream = socket.outputStream
    private val inStream = socket.inputStream

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
            } catch (_: IOException) {}
        }
    }

    override suspend fun send(message: String) {
        try {
            outStream.write(message.toByteArray())
            outStream.flush()
        } catch (_: IOException) {}
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
        scope.cancel()
    }
}