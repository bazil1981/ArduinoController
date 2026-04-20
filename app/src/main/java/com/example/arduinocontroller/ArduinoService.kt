package com.example.arduinocontroller

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ArduinoService : Service() {

    private val binder = LocalBinder()
    private var udpSocket: DatagramSocket? = null
    private var arduinoIp: InetAddress? = null
    private var arduinoPort = 0
    private var isConnected = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val logs = mutableListOf<String>()
    
    private var onResponseListener: ((String) -> Unit)? = null
    private var onStatusListener: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): ArduinoService = this@ArduinoService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification("Disconnected"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "arduino_channel",
                "Arduino Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, "arduino_channel")
            .setContentTitle("Arduino Controller")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun connect(ip: String, port: Int) {
        Thread {
            try {
                arduinoIp = InetAddress.getByName(ip)
                arduinoPort = port
                udpSocket = DatagramSocket()
                udpSocket?.soTimeout = 2000
                
                // Initial check
                val data = "STATUS".toByteArray()
                val packet = DatagramPacket(data, data.size, arduinoIp, arduinoPort)
                udpSocket?.send(packet)
                
                val buffer = ByteArray(1024)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(responsePacket)
                
                isConnected = true
                val response = String(responsePacket.data, 0, responsePacket.length)
                addLog("Connected to $ip:$port")
                addLog("Initial Response: $response")
                
                mainHandler.post {
                    updateNotification("Connected to $ip:$port")
                    onStatusListener?.invoke(true)
                    onResponseListener?.invoke(response)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onStatusListener?.invoke(false)
                }
            }
        }.start()
    }

    fun sendCommand(command: String) {
        if (!isConnected) return
        addLog("Sending: $command")
        Thread {
            try {
                val data = command.toByteArray()
                val packet = DatagramPacket(data, data.size, arduinoIp, arduinoPort)
                udpSocket?.send(packet)

                val buffer = ByteArray(1024)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(responsePacket)
                
                val response = String(responsePacket.data, 0, responsePacket.length)
                addLog("Received: $response")
                mainHandler.post {
                    onResponseListener?.invoke(response)
                }
            } catch (e: Exception) {
                addLog("Error: ${e.message}")
                // Handle timeout
            }
        }.start()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, createNotification(content))
    }

    fun setListeners(onResponse: (String) -> Unit, onStatus: (Boolean) -> Unit) {
        this.onResponseListener = onResponse
        this.onStatusListener = onStatus
        // Immediately notify the current state when listener is attached
        onStatus(isConnected)
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedLog = "$timestamp - $message"
        logs.add(formattedLog)
        // Limit log size
        if (logs.size > 1000) logs.removeAt(0)
    }

    fun getLogs(): List<String> = logs

    override fun onDestroy() {
        udpSocket?.close()
        super.onDestroy()
    }
}