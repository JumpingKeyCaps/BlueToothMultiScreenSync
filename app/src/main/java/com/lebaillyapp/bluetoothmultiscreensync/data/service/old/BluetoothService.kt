package com.lebaillyapp.bluetoothmultiscreensync.data.service.old

import android.Manifest
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Foreground Service handling Bluetooth communication for the BlueToothMultiScreenSync project.
 *
 * This service can act as a master (server accepting multiple clients) or a slave (client connecting to a master).
 * Messages are emitted via [messageFlow] to allow ViewModels to observe incoming data.
 *
 * Permissions required: BLUETOOTH_CONNECT (Android 12+)
 */
class BluetoothService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Foreground notification channel ID */
        const val CHANNEL_ID = "BluetoothServiceChannel"

        /** Standard SPP UUID used for RFCOMM connections */
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    /** Flow emitting incoming messages from connected devices */
    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow = _messageFlow.asSharedFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null

    /** List of currently connected client sockets */
    private val clientSockets = mutableListOf<BluetoothSocket>()

    /** Server socket for master mode */
    private var serverSocket: BluetoothServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        startForegroundService()
    }

    /**
     * Starts the foreground notification required for persistent service.
     */
    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth MultiScreen Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlueToothMultiScreenSync")
            .setContentText("Bluetooth Service Running")
            .setSmallIcon(R.drawable.stat_sys_data_bluetooth)
            .build()

        startForeground(1, notification)
    }

    /**
     * Checks if the app has BLUETOOTH_CONNECT permission (Android 12+).
     * @return true if permission granted, false otherwise.
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Master: server multi-client ---

    /**
     * Starts the service in master mode, listening for incoming client connections.
     *
     * Each accepted client socket is added to [clientSockets] and starts listening for messages.
     *
     * @throws SecurityException if BLUETOOTH_CONNECT permission is missing.
     */
    fun startServer() {
        if (!hasBluetoothConnectPermission()) throw SecurityException("Missing BLUETOOTH_CONNECT permission")

        serviceScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "BlueToothMultiScreenSync",
                    BT_UUID
                )
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    synchronized(clientSockets) { clientSockets.add(socket) }
                    listenToSocket(socket)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (se: SecurityException) {
                se.printStackTrace()
            }
        }
    }

    // --- Slave: connect to master ---

    /**
     * Connects to a master device (server) in slave mode.
     *
     * Adds the socket to [clientSockets] and starts listening for incoming messages.
     *
     * @param device BluetoothDevice to connect to.
     * @throws SecurityException if BLUETOOTH_CONNECT permission is missing.
     */
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) throw SecurityException("Missing BLUETOOTH_CONNECT permission")

        serviceScope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(BT_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                synchronized(clientSockets) { clientSockets.add(socket) }
                listenToSocket(socket)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (se: SecurityException) {
                se.printStackTrace()
            }
        }
    }

    /**
     * Continuously listens to a connected socket for incoming messages.
     *
     * Messages are emitted to [messageFlow]. On IOException, the socket is removed from [clientSockets].
     *
     * @param socket Connected BluetoothSocket.
     */
    private fun listenToSocket(socket: BluetoothSocket) {
        serviceScope.launch {
            try {
                val input: InputStream = socket.inputStream
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (true) {
                    bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        val message = String(buffer, 0, bytesRead)
                        _messageFlow.emit(message)
                    }
                }
            } catch (e: IOException) {
                synchronized(clientSockets) { clientSockets.remove(socket) }
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends a string message to all connected clients.
     *
     * @param message String message to send.
     */
    fun sendMessageToAll(message: String) {
        serviceScope.launch {
            synchronized(clientSockets) {
                val iterator = clientSockets.iterator()
                while (iterator.hasNext()) {
                    val socket = iterator.next()
                    try {
                        socket.outputStream.write(message.toByteArray())
                    } catch (e: IOException) {
                        iterator.remove()
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        synchronized(clientSockets) {
            clientSockets.forEach { try { it.close() } catch (_: IOException) {} }
            clientSockets.clear()
        }
        try { serverSocket?.close() } catch (_: IOException) {}
        super.onDestroy()
    }
}
