package com.example.emergencybroadcastapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FeedItem represents a single parsed broadcast event displayed in the Message Feed tab.
 *
 * LAYMAN EXPLANATION:
 * When the app receives or logs a mesh alert, it writes a line of raw text into `mesh_latency_log.txt`.
 * This FeedItem data class converts that raw line into a clean, human-readable card with:
 * - Red/Green alert status indicator
 * - Formatted date/time timestamp (e.g. "Nov 11, 2026 • 14:32:05")
 * - Hop count & latency metrics
 * - Custom template message text or fallback description
 */
data class FeedItem(
    val id: String,
    val type: String,               // "EMERGENCY_ALERT" (EAB - Red) vs "INFO_NOTIFICATION" (RAB - Green)
    val hops: Int,
    val latencyMs: Long,
    val customText: String?,
    val timestamp: Long,
    val formattedTime: String,
    val isEmergency: Boolean
)

/**
 * Helper function to parse raw text lines from `mesh_latency_log.txt` into a list of FeedItem objects
 * sorted in descending recency order (newest broadcasts at the top).
 */
fun parseLogsToFeedItems(rawLogText: String): List<FeedItem> {
    if (rawLogText.isBlank() || rawLogText.startsWith("No logs found")) {
        return emptyList()
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
    val items = mutableListOf<FeedItem>()

    // Regular expressions to extract key fields from each log line
    val idRegex = Regex("""ID:\s*([A-Z0-9]+)""")
    val typeRegex = Regex("""Type:\s*([A-Z_]+)""")
    val hopsRegex = Regex("""Hops:\s*([0-9]+)""")
    val latencyRegex = Regex("""Latency:\s*([0-9]+)ms""")
    val msgRegex = Regex("""Msg:\s*"(.*?)"|Msg:\s*(.*?)(?:,|$)""")
    val timeRegex = Regex("""Time:\s*([0-9]+)""")

    rawLogText.lineSequence().forEach { line ->
        if (line.isNotBlank()) {
            val idMatch = idRegex.find(line)?.groupValues?.get(1) ?: "UNKNOWN"
            val typeMatch = typeRegex.find(line)?.groupValues?.get(1) ?: "EMERGENCY_ALERT"
            val hopsMatch = hopsRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val latencyMatch = latencyRegex.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val timeMatch = timeRegex.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()

            val msgMatch = msgRegex.find(line)
            val customText = msgMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: msgMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

            val formattedTime = dateFormat.format(Date(timeMatch))
            val isEmergency = typeMatch.contains("EMERGENCY") || (customText != null && (
                    customText.contains("Emergency", ignoreCase = true) ||
                    customText.contains("Aid", ignoreCase = true) ||
                    customText.contains("Evacuation", ignoreCase = true) ||
                    customText.contains("Rescue", ignoreCase = true)
            ))

            items.add(
                FeedItem(
                    id = idMatch,
                    type = typeMatch,
                    hops = hopsMatch,
                    latencyMs = latencyMatch,
                    customText = customText,
                    timestamp = timeMatch,
                    formattedTime = formattedTime,
                    isEmergency = isEmergency
                )
            )
        }
    }

    // Sort descending by timestamp (newest broadcast at the top!)
    return items.sortedByDescending { it.timestamp }
}
