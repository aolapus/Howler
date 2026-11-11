package com.example.emergencybroadcastapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

enum class AppScreen {
    MAIN,
    LOGS,
    TEMPLATES
}

class MainActivity : ComponentActivity() {

    private val templateManager by lazy { TemplateManager(applicationContext) }

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

        // Set top system status bar color to match green header navbar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.parseColor("#63AE73")
        }

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
                    color = Color(0xFF63AE73) // Unified status bar & header background green
                ) {
                    MainApp(
                        onEmergencyTriggered = { customMsg -> triggerEmergencyBroadcast(customMsg) },
                        onInfoTriggered = { customMsg -> triggerInfoBroadcast(customMsg) },
                        onToggleMesh = { active -> toggleMesh(active) },
                        onReadLogs = { readLogs() },
                        onExportLogs = { exportLogs() },
                        onClearLogs = { clearLogs() },
                        onSimulateTestAlert = { type, desc -> simulateTestAlert(type, desc) },
                        meshStatus = meshStatus,
                        templateManager = templateManager
                    )
                }
            }
        }
    }

    private fun readLogs(): String {
        val file = File(filesDir, "mesh_latency_log.txt")
        return if (file.exists() && file.length() > 0) file.readText() else "No logs found."
    }

    private fun clearLogs() {
        val file = File(filesDir, "mesh_latency_log.txt")
        if (file.exists()) {
            file.delete()
        }
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
    }

    private fun exportLogs() {
        val file = File(filesDir, "mesh_latency_log.txt")
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "No logs to export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Logs"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun simulateTestAlert(type: String, description: String) {
        try {
            val messageId = "000000"
            val logEntry = "ID: $messageId, Type: $type, Hops: 0, Latency: 0ms, Msg: \"$description\", Time: ${System.currentTimeMillis()}\n"
            val file = File(filesDir, "mesh_latency_log.txt")
            FileOutputStream(file, true).use {
                it.write(logEntry.toByteArray())
            }
            Toast.makeText(this, "Simulated $description added to feed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to simulate alert: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, NearbyForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun triggerEmergencyBroadcast(customMessage: String? = null) {
        val intent = Intent(this, NearbyForegroundService::class.java).apply {
            action = "BROADCAST_EMERGENCY"
            if (!customMessage.isNullOrBlank()) {
                putExtra("EXTRA_CUSTOM_MESSAGE", customMessage)
            }
        }
        startService(intent)
    }

    private fun triggerInfoBroadcast(customMessage: String? = null) {
        val intent = Intent(this, NearbyForegroundService::class.java).apply {
            action = "BROADCAST_NOTIFICATION"
            if (!customMessage.isNullOrBlank()) {
                putExtra("EXTRA_CUSTOM_MESSAGE", customMessage)
            }
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

/**
 * MainApp structures the app's primary layout with a persistent Bottom Navigation Bar.
 */
@Composable
fun MainApp(
    onEmergencyTriggered: (customMessage: String?) -> Unit,
    onInfoTriggered: (customMessage: String?) -> Unit,
    onToggleMesh: (Boolean) -> Unit,
    onReadLogs: () -> String,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onSimulateTestAlert: (type: String, description: String) -> Unit,
    meshStatus: MeshStatus,
    templateManager: TemplateManager
) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Broadcast, 1 = Feed
    var menuExpanded by remember { mutableStateOf(false) }
    var logsContent by remember { mutableStateOf("") }

    val refreshLogs = {
        logsContent = onReadLogs()
    }

    Scaffold(
        containerColor = Color(0xFF63AE73),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Persistent Bottom Navigation Bar pinned across screens
            NavigationBar(
                containerColor = Color(0xFFF5F5F5),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0 && currentScreen == AppScreen.MAIN,
                    onClick = {
                        selectedTab = 0
                        currentScreen = AppScreen.MAIN
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Broadcast"
                        )
                    },
                    label = { Text("Broadcast", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B5E20),
                        selectedTextColor = Color(0xFF1B5E20),
                        indicatorColor = Color(0xFFC8E6C9)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1 && currentScreen == AppScreen.MAIN,
                    onClick = {
                        selectedTab = 1
                        currentScreen = AppScreen.MAIN
                        refreshLogs()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Feed"
                        )
                    },
                    label = { Text("Feed", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B5E20),
                        selectedTextColor = Color(0xFF1B5E20),
                        indicatorColor = Color(0xFFC8E6C9)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation Bar Header (Extends green background through the status bar & camera cutout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF63AE73))
                    .statusBarsPadding()
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.howler_logo),
                    contentDescription = "Howler Logo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )

                // Hamburger Settings Menu
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Templates") },
                            onClick = {
                                menuExpanded = false
                                currentScreen = AppScreen.TEMPLATES
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Raw Logs") },
                            onClick = {
                                menuExpanded = false
                                refreshLogs()
                                currentScreen = AppScreen.LOGS
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Logs") },
                            onClick = {
                                menuExpanded = false
                                onExportLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Logs") },
                            onClick = {
                                menuExpanded = false
                                onClearLogs()
                                refreshLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Simulate TEST EAB") },
                            onClick = {
                                menuExpanded = false
                                onSimulateTestAlert("EMERGENCY_ALERT", "TEST EAB")
                                refreshLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Simulate TEST RAB") },
                            onClick = {
                                menuExpanded = false
                                onSimulateTestAlert("INFO_NOTIFICATION", "TEST RAB")
                                refreshLogs()
                            }
                        )
                    }
                }
            }

            // Body Content based on active screen and bottom tab
            when (currentScreen) {
                AppScreen.MAIN -> {
                    if (selectedTab == 0) {
                        MainDashboardScreen(
                            onEmergencyTriggered = onEmergencyTriggered,
                            onInfoTriggered = onInfoTriggered,
                            onToggleMesh = onToggleMesh,
                            meshStatus = meshStatus,
                            templateManager = templateManager
                        )
                    } else {
                        FeedScreen(
                            rawLogText = logsContent,
                            onRefresh = refreshLogs
                        )
                    }
                }
                AppScreen.LOGS -> {
                    LogsScreen(
                        logs = logsContent,
                        onBack = { currentScreen = AppScreen.MAIN },
                        onExport = onExportLogs,
                        onClear = {
                            onClearLogs()
                            refreshLogs()
                        }
                    )
                }
                AppScreen.TEMPLATES -> {
                    TemplatesScreen(
                        templateManager = templateManager,
                        onBack = { currentScreen = AppScreen.MAIN }
                    )
                }
            }
        }
    }
}

@Composable
fun MainDashboardScreen(
    onEmergencyTriggered: (customMessage: String?) -> Unit,
    onInfoTriggered: (customMessage: String?) -> Unit,
    onToggleMesh: (Boolean) -> Unit,
    meshStatus: MeshStatus,
    templateManager: TemplateManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            StatusRow("Advertising", meshStatus.isAdvertising)
            StatusRow("Discovery", meshStatus.isDiscovering)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(text = "Mesh Receiver", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                color = if (meshStatus.peerCount > 0) Color(0xFF4CAF50) else Color.DarkGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            meshStatus.lastError?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // emergency broadcast buttons
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HoldToBroadcastButton(
                text = "BROADCAST\nEMERGENCY",
                color = Color.Red,
                activeColor = Color(0xFFB71C1C),
                onTriggered = onEmergencyTriggered,
                templateManager = templateManager
            )

            Spacer(modifier = Modifier.height(24.dp))

            HoldToBroadcastButton(
                text = "SEND INFO\nNOTIFICATION",
                color = Color(0xFF1B5E20),
                activeColor = Color(0xFF1B3E20),
                onTriggered = onInfoTriggered,
                templateManager = templateManager
            )
        }
    }
}

/**
 * FeedScreen renders the recency-sorted timeline list of broadcast events.
 */
@Composable
fun FeedScreen(
    rawLogText: String,
    onRefresh: () -> Unit
) {
    val feedItems = remember(rawLogText) {
        parseLogsToFeedItems(rawLogText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Broadcast Feed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${feedItems.size} events recorded",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Feed",
                    tint = Color(0xFF1B5E20)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (feedItems.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                shape = RectangleShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Broadcast History Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Received emergency alerts and info notifications will automatically appear in this feed.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(feedItems, key = { it.id + it.timestamp }) { item ->
                    FeedItemCard(item = item)
                }
            }
        }
    }
}

/**
 * FeedItemCard displays a single broadcast event with status indicators, timestamps, and message text.
 */
@Composable
fun FeedItemCard(item: FeedItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEmergency) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        ),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Visual Status Indicator Circle (Red for EAB / Green for RAB)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (item.isEmergency) Color(0xFFD32F2F) else Color(0xFF2E7D32))
                    .align(Alignment.CenterVertically)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header Row: Formatted Timestamp & Metrics Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RectangleShape,
                            color = Color.White.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "Hops: ${item.hops}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.Black
                            )
                        }

                        Surface(
                            shape = RectangleShape,
                            color = Color.White.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "${item.latencyMs}ms",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message Body Description
                Text(
                    text = if (!item.customText.isNullOrBlank()) {
                        item.customText
                    } else if (item.isEmergency) {
                        "Emergency Mesh Broadcast Alert (ID: ${item.id})"
                    } else {
                        "Info Broadcast Message (ID: ${item.id})"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isEmergency) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                )
            }
        }
    }
}

@Composable
fun TemplatesScreen(
    templateManager: TemplateManager,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var t1 by remember { mutableStateOf(templateManager.getTemplate(0)) }
    var t2 by remember { mutableStateOf(templateManager.getTemplate(1)) }
    var t3 by remember { mutableStateOf(templateManager.getTemplate(2)) }
    var t4 by remember { mutableStateOf(templateManager.getTemplate(3)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
            Text(
                text = "Edit Emergency Templates",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Template 1 (Top-Left Position)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = t1,
            onValueChange = { t1 = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Template 2 (Top-Right Position)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = t2,
            onValueChange = { t2 = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Template 3 (Bottom-Left Position)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = t3,
            onValueChange = { t3 = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Template 4 (Bottom-Right Position)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = t4,
            onValueChange = { t4 = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                templateManager.saveTemplate(0, t1)
                templateManager.saveTemplate(1, t2)
                templateManager.saveTemplate(2, t3)
                templateManager.saveTemplate(3, t4)
                Toast.makeText(context, "Templates saved successfully!", Toast.LENGTH_SHORT).show()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF63AE73))
        ) {
            Text("Save Templates", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * LogsScreen displays raw log messages formatted with simple black text on a white background,
 * rectangular non-rounded card container, and clear visual dividers separating individual messages.
 */
@Composable
fun LogsScreen(
    logs: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    // Format raw log text with clear visual dividers between distinct entries
    val formattedLogs = remember(logs) {
        if (logs.isBlank() || logs.startsWith("No logs found")) {
            "No logs found."
        } else {
            logs.lines()
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n────────────────────────────────────────\n")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
                Text(
                    text = "Raw Mesh Logs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Row {
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Logs",
                        tint = Color(0xFF1B5E20)
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simple black text on white background with non-rounded rectangular card
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RectangleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = formattedLogs,
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * HoldToBroadcastButton handles the 3-second press-and-hold radial template drag gesture.
 */
@Composable
fun HoldToBroadcastButton(
    text: String,
    color: Color,
    activeColor: Color,
    onTriggered: (customMessage: String?) -> Unit,
    templateManager: TemplateManager
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    var selectedQuadrant by remember { mutableIntStateOf(0) } // 0 = Center/Default, 1..4 = Template positions

    var buttonSizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val templates = remember(isHolding) {
        templateManager.getTemplates()
    }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed / 3000f).coerceAtMost(1f)
                if (holdProgress >= 1f) {
                    val selectedMessage = if (selectedQuadrant in 1..4) {
                        templates.getOrNull(selectedQuadrant - 1)
                    } else {
                        null
                    }
                    onTriggered(selectedMessage)
                }
                delay(16)
            }
        } else {
            holdProgress = 0f
            selectedQuadrant = 0
        }
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(140.dp)
            .onGloballyPositioned { coordinates ->
                buttonSizePx = androidx.compose.ui.geometry.Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
            .clip(RectangleShape) // Rectangular shape (no rounded corners)
            .background(if (isHolding) activeColor else color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isHolding = true
                    holdProgress = 0f

                    var currentPos = down.position
                    val centerX = buttonSizePx.width / 2f
                    val centerY = buttonSizePx.height / 2f
                    val deadzonePx = 30f

                    while (isHolding) {
                        val event = awaitPointerEvent()
                        val pointerChange = event.changes.firstOrNull()

                        if (pointerChange == null || !pointerChange.pressed) {
                            isHolding = false
                            break
                        }

                        currentPos = pointerChange.position
                        val dx = currentPos.x - centerX
                        val dy = currentPos.y - centerY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                        if (distance < deadzonePx) {
                            selectedQuadrant = 0
                        } else {
                            selectedQuadrant = when {
                                dx < 0 && dy < 0 -> 1 // Top-Left Position
                                dx >= 0 && dy < 0 -> 2 // Top-Right Position
                                dx < 0 && dy >= 0 -> 3 // Bottom-Left Position
                                else -> 4             // Bottom-Right Position
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Normal Button View when not holding or overlay during holding
        if (!isHolding) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hold 3s & drag to select template",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        } else {
            // Clean Template Overlay while holding (No unnecessary middle text, no "Q1:" / "Quadrant" labels)
            Box(modifier = Modifier.fillMaxSize()) {
                // Position 1 (Top-Left)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .align(Alignment.TopStart)
                        .background(if (selectedQuadrant == 1) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = templates.getOrElse(0) { "" }.take(22),
                        color = if (selectedQuadrant == 1) Color.Yellow else Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (selectedQuadrant == 1) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                // Position 2 (Top-Right)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .align(Alignment.TopEnd)
                        .background(if (selectedQuadrant == 2) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = templates.getOrElse(1) { "" }.take(22),
                        color = if (selectedQuadrant == 2) Color.Yellow else Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (selectedQuadrant == 2) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                // Position 3 (Bottom-Left)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .align(Alignment.BottomStart)
                        .background(if (selectedQuadrant == 3) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = templates.getOrElse(2) { "" }.take(22),
                        color = if (selectedQuadrant == 3) Color.Yellow else Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (selectedQuadrant == 3) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                // Position 4 (Bottom-Right)
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .align(Alignment.BottomEnd)
                        .background(if (selectedQuadrant == 4) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = templates.getOrElse(3) { "" }.take(22),
                        color = if (selectedQuadrant == 4) Color.Yellow else Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (selectedQuadrant == 4) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                // Clean Center Progress Bar
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (holdProgress >= 1f) {
                        Text(
                            text = "SENT!",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    LinearProgressIndicator(
                        progress = holdProgress,
                        modifier = Modifier.width(110.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
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
        Text(text = label, color = Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    }
}