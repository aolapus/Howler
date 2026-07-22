package com.example.emergencybroadcastapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

class NearbyForegroundService : Service() {

    private val SERVICE_ID = "EMERGENCY_BROADCAST_SERVICE"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val CHANNEL_ID = "NearbyServiceChannel"

    private val connectedEndpoints = mutableSetOf<String>()
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
            }
            broadcastToAll(jsonPayload.toString())
        }
        return START_STICKY
    }

    private fun startAdvertisingAndDiscovery() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this).startAdvertising(
            "EmergencyNode", SERVICE_ID, connectionLifecycleCallback, options
        )

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(this).startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        )
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Nearby.getConnectionsClient(this@NearbyForegroundService)
                .requestConnection("EmergencyNode", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(this@NearbyForegroundService)
                .acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
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
        try {
            val json = JSONObject(rawJson)
            val msgId = json.getString("id")
            val currentHops = json.optInt("hops", 0)

            // Ignore if already processed or exceeds 200 hops
            if (processedMessageIds.contains(msgId) || currentHops >= 200) {
                return
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Emergency Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
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