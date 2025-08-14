package com.lebaillyapp.bluetoothmultiscreensync.data.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
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
import java.io.OutputStream
import java.util.*

class BluetoothService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "BluetoothServiceChannel"
        val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP
    }

    // Flow to emit messages to ViewModel
    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow = _messageFlow.asSharedFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val clientSockets = mutableListOf<BluetoothSocket>()
    private var serverSocket: BluetoothServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        startForegroundService()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth MultiScreen Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlueToothMultiScreenSync")
            .setContentText("Bluetooth Service Running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()

        startForeground(1, notification)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Master: server multi-client ---
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

    // send message to all clients
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
