package com.lebaillyapp.bluetoothmultiscreensync.data.service.old

import android.Manifest
import android.R
import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


class BluetoothServiceOpti : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val clientSockets = ConcurrentLinkedQueue<BluetoothSocket>()
    private var serverSocket: BluetoothServerSocket? = null

    // Flows pour communiquer avec le ViewModel
    private val _messageFlow = MutableSharedFlow<BluetoothMessage>(extraBufferCapacity = 64)
    private val _errorFlow = MutableSharedFlow<Throwable>(extraBufferCapacity = 16)
    private val _permissionFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val messageFlow = _messageFlow.asSharedFlow()
    val errorFlow = _errorFlow.asSharedFlow()
    val permissionFlow = _permissionFlow.asSharedFlow()

    private val sendChannel = Channel<String>(Channel.UNLIMITED)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val CHANNEL_ID = "BluetoothServiceChannel"
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val BUFFER_SIZE = 1024
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startMessageSender()
    }

    private fun startMessageSender() = serviceScope.launch {
        sendChannel.consumeEach { jsonMessage ->
            clientSockets.removeAll { socket ->
                try {
                    if (hasBluetoothConnectPermission()) {
                        socket.outputStream.write(jsonMessage.toByteArray())
                        false
                    } else {
                        _permissionFlow.tryEmit(Unit)
                        true
                    }
                } catch (e: IOException) {
                    true
                }
            }
        }
    }

    // Server Mode
    fun startServer() {
        serviceScope.launch {
            try {
                if (!ensureBluetoothPermission()) return@launch

                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "BlueToothMultiScreenSync",
                    BT_UUID
                )

                while (isActive) {
                    serverSocket?.accept()?.let { socket ->
                        clientSockets.add(socket)
                        listenToSocket(socket)
                    }
                }
            } catch (e: SecurityException) {
                _permissionFlow.tryEmit(Unit)
            } catch (e: Exception) {
                _errorFlow.tryEmit(e)
            }
        }
    }

    // Client Mode
    fun connectToDevice(device: BluetoothDevice) {
        serviceScope.launch {
            try {
                if (!ensureBluetoothPermission()) return@launch

                val socket = device.createRfcommSocketToServiceRecord(BT_UUID)
                if (hasBluetoothConnectPermission()) {
                    bluetoothAdapter?.cancelDiscovery()
                }
                socket.connect()
                clientSockets.add(socket)
                listenToSocket(socket)
            } catch (e: SecurityException) {
                _permissionFlow.tryEmit(Unit)
            } catch (e: Exception) {
                _errorFlow.tryEmit(e)
            }
        }
    }

    private suspend fun ensureBluetoothPermission(): Boolean {
        return if (hasBluetoothConnectPermission()) {
            true
        } else {
            _permissionFlow.tryEmit(Unit)
            false
        }
    }

    private fun listenToSocket(socket: BluetoothSocket) = serviceScope.launch {
        try {
            val input = socket.inputStream
            val buffer = ByteArray(BUFFER_SIZE)

            while (isActive) {
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    val jsonString = String(buffer, 0, bytesRead)
                    try {
                        val message = json.decodeFromString<BluetoothMessage>(jsonString)
                        _messageFlow.tryEmit(message)
                    } catch (e: Exception) {
                        _errorFlow.tryEmit(Exception("Failed to parse message: $jsonString", e))
                    }
                }
            }
        } catch (e: Exception) {
            clientSockets.remove(socket)
            _errorFlow.tryEmit(e)
        } finally {
            socket.close()
        }
    }

    fun sendMessage(message: BluetoothMessage) {
        try {
            val jsonMessage = json.encodeToString(message)
            if (hasBluetoothConnectPermission()) {
                sendChannel.trySend(jsonMessage).getOrThrow()
            } else {
                _permissionFlow.tryEmit(Unit)
            }
        } catch (e: Exception) {
            _errorFlow.tryEmit(Exception("Failed to serialize message", e))
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Bluetooth MultiScreen Sync",
                NotificationManager.IMPORTANCE_LOW
            ).also {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(it)
            }
        }

        startForeground(
            1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BlueToothMultiScreenSync")
                .setContentText("Bluetooth Service Running")
                .setSmallIcon(R.drawable.stat_sys_data_bluetooth)
                .build()
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        clientSockets.forEach { it.close() }
        serverSocket?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}