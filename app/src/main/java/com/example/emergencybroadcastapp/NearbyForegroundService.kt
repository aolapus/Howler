package com.example.emergencybroadcastapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

class NearbyForegroundService : Service() {

    private val SERVICE_ID = "com.example.emergencybroadcastapp.MESH_ID"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val CHANNEL_ID = "NearbyServiceChannel"
    private val ALERT_CHANNEL_ID = "EmergencyAlertChannel"

    private val connectedEndpoints = mutableSetOf<String>()
    private val connectingEndpoints = mutableSetOf<String>()
    private val processedMessageIds = Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 1000
        }
    })

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = createNotification(
            "Emergency Service Active",
            "Listening for emergency mesh network..."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(1, notification)
        }

        startAdvertisingAndDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "BROADCAST_EMERGENCY") {
            val messageId = UUID.randomUUID().toString()
            val jsonPayload = JSONObject().apply {
                put("id", messageId)
                put("type", "EMERGENCY_ALERT")
                put("hops", 0)
                put("timestamp", System.currentTimeMillis())
            }
            broadcastToAll(jsonPayload.toString())
        }
        return START_STICKY
    }

    private fun startAdvertisingAndDiscovery() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this).startAdvertising(
            "EmergencyNode", SERVICE_ID, connectionLifecycleCallback, options
        ).addOnSuccessListener {
            Log.d("MeshService", "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e("MeshService", "Advertising failed to start", e)
        }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this).startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            Log.d("MeshService", "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e("MeshService", "Discovery failed to start", e)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("MeshService", "Endpoint found: $endpointId (${info.endpointName})")
            
            // Basic collision avoidance: only initiate if we haven't already
            if (!connectedEndpoints.contains(endpointId) && !connectingEndpoints.contains(endpointId)) {
                connectingEndpoints.add(endpointId)
                Nearby.getConnectionsClient(this@NearbyForegroundService)
                    .requestConnection("EmergencyNode", endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        connectingEndpoints.remove(endpointId)
                        Log.e("MeshService", "Request connection failed for $endpointId", e)
                    }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("MeshService", "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("MeshService", "Connection initiated with $endpointId")
            Nearby.getConnectionsClient(this@NearbyForegroundService)
                .acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e("MeshService", "Accept connection failed for $endpointId", e)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                Log.d("MeshService", "Connected to $endpointId")
                connectedEndpoints.add(endpointId)
            } else {
                Log.e("MeshService", "Connection failed with $endpointId: ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("MeshService", "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val rawStr = String(bytes, StandardCharsets.UTF_8)
                handleIncomingMessage(rawStr)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handleIncomingMessage(rawJson: String) {
        val receiveTime = System.currentTimeMillis()
        try {
            val json = JSONObject(rawJson)
            val msgId = json.getString("id")
            val currentHops = json.optInt("hops", 0)
            val originalTimestamp = json.optLong("timestamp", -1L)

            // Ignore if already processed or exceeds 200 hops
            if (processedMessageIds.contains(msgId) || currentHops >= 200) {
                return
            }

            // Calculate and log latency if timestamp exists
            if (originalTimestamp != -1L) {
                val latency = receiveTime - originalTimestamp
                Log.d("MeshLatency", "Message $msgId received. Hops: $currentHops, Latency: ${latency}ms")
                saveLatencyLog(msgId, currentHops, latency)
                
                // TRIGGER ALARM
                triggerEmergencyAlarm(msgId)
            }

            // Deduplicate: mark as received
            processedMessageIds.add(msgId)

            // Re-broadcast (Hop counter + 1)
            json.put("hops", currentHops + 1)
            broadcastToAll(json.toString())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLatencyLog(msgId: String, hops: Int, latency: Long) {
        try {
            val logEntry = "ID: $msgId, Hops: $hops, Latency: ${latency}ms, Time: ${System.currentTimeMillis()}\n"
            val file = File(filesDir, "mesh_latency_log.txt")
            FileOutputStream(file, true).use {
                it.write(logEntry.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("MeshLatency", "Failed to save log", e)
        }
    }

    private fun broadcastToAll(data: String) {
        if (connectedEndpoints.isNotEmpty()) {
            val payload = Payload.fromBytes(data.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(this).sendPayload(connectedEndpoints.toList(), payload)
        }
    }

    override fun onDestroy() {
        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopDiscovery()
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        super.onDestroy()
    }

    private fun triggerEmergencyAlarm(msgId: String) {
        // 1. Play Alarm Sound
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone.play()
            // Stop after 5 seconds to not be too annoying in tests
            Handler(Looper.getMainLooper()).postDelayed({
                if (ringtone.isPlaying) ringtone.stop()
            }, 5000)
        } catch (e: Exception) {
            Log.e("MeshService", "Failed to play alarm sound", e)
        }

        // 2. Vibrate
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }

        // 3. Show High-Priority Notification
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("EMERGENCY ALERT RECEIVED")
            .setContentText("A mesh broadcast message was received: $msgId")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(msgId.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Emergency Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency mesh alerts"
                enableLights(true)
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}