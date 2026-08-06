package com.example.emergencybroadcastapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }
        permissions.toTypedArray()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startForegroundService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasRequiredPermissions()) {
            startForegroundService()
        } else {
            requestRequiredPermissions()
        }

        setContent {
            val meshStatus by NearbyForegroundService.status.collectAsStateWithLifecycle()
            
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    MainScreen(
                        onEmergencyTriggered = { triggerEmergencyBroadcast() },
                        onInfoTriggered = { triggerInfoBroadcast() },
                        onViewLogs = { showLogs() },
                        onToggleMesh = { active -> toggleMesh(active) },
                        meshStatus = meshStatus
                    )
                }
            }
        }
    }

    private fun showLogs() {
        val file = File(filesDir, "mesh_latency_log.txt")
        val logs = if (file.exists()) file.readText() else "No logs found."
        
        AlertDialog.Builder(this)
            .setTitle("Mesh Network Logs")
            .setMessage(logs)
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ -> 
                file.delete()
            }
            .show()
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, NearbyForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun triggerEmergencyBroadcast() {
        val intent = Intent(this, NearbyForegroundService::class.java).apply {
            action = "BROADCAST_EMERGENCY"
        }
        startService(intent)
    }

    private fun triggerInfoBroadcast() {
        val intent = Intent(this, NearbyForegroundService::class.java).apply {
            action = "BROADCAST_NOTIFICATION"
        }
        startService(intent)
    }

    private fun toggleMesh(active: Boolean) {
        val intent = Intent(this, NearbyForegroundService::class.java).apply {
            action = if (active) NearbyForegroundService.ACTION_START_MESH else NearbyForegroundService.ACTION_STOP_MESH
        }
        startService(intent)
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun MainScreen(
    onEmergencyTriggered: () -> Unit,
    onInfoTriggered: () -> Unit,
    onViewLogs: () -> Unit,
    onToggleMesh: (Boolean) -> Unit,
    meshStatus: MeshStatus
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Status Section (Top)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.howler_logo),
                contentDescription = "Howler Logo",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatusRow("Advertising", meshStatus.isAdvertising)
            StatusRow("Discovery", meshStatus.isDiscovering)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(text = "Mesh Receiver", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = meshStatus.isMeshActive,
                    onCheckedChange = onToggleMesh,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            Text(
                text = "Connected Peers: ${meshStatus.peerCount}",
                color = if (meshStatus.peerCount > 0) Color(0xFF4CAF50) else Color.Gray,
                fontSize = 12.sp
            )
            meshStatus.lastError?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        }

        // 2. Button Section (Center)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HoldToBroadcastButton(
                text = "BROADCAST\nEMERGENCY",
                color = Color.Red,
                activeColor = Color(0xFFB71C1C),
                onTriggered = onEmergencyTriggered
            )

            Spacer(modifier = Modifier.height(24.dp))

            HoldToBroadcastButton(
                text = "SEND INFO\nNOTIFICATION",
                color = Color(0xFF1B5E20),
                activeColor = Color(0xFF1B3E20),
                onTriggered = onInfoTriggered
            )
        }

        // 3. Footer Section (Bottom)
        Button(
            onClick = onViewLogs,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("View Latency Logs", color = Color.White)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun HoldToBroadcastButton(
    text: String,
    color: Color,
    activeColor: Color,
    onTriggered: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed / 3000f).coerceAtMost(1f)
                if (holdProgress >= 1f) {
                    onTriggered()
                }
                delay(16)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isHolding) activeColor else color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (holdProgress >= 1f) "SENT!" else text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (isHolding && holdProgress < 1f) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = holdProgress,
                    modifier = Modifier.width(100.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF4CAF50) else Color.Gray)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = Color.LightGray, fontSize = 12.sp)
    }
}