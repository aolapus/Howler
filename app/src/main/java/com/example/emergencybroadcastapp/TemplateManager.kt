package com.example.emergencybroadcastapp

import android.content.Context
import android.content.SharedPreferences

/**
 * TemplateManager handles saving and loading custom text message templates using Android's SharedPreferences.
 *
 * LAYMAN EXPLANATION:
 * Think of SharedPreferences as a small digital notebook built into Android that saves settings even when
 * the app is closed. This class manages 4 custom emergency messages (one for each screen quadrant).
 *
 * Quadrant Mapping:
 * - Quadrant 1 (Top-Left): Template 1
 * - Quadrant 2 (Top-Right): Template 2
 * - Quadrant 3 (Bottom-Left): Template 3
 * - Quadrant 4 (Bottom-Right): Template 4
 */
class TemplateManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("howler_templates_prefs", Context.MODE_PRIVATE)

    companion object {
        // Default templates if the user has not edited them yet
        val DEFAULT_TEMPLATES = listOf(
            "Medical Emergency - Need immediate aid",       // Template 1 (Top-Left)
            "Evacuation Alert - Move to safe area",          // Template 2 (Top-Right)
            "Search & Rescue Needed - Location pinned",      // Template 3 (Bottom-Left)
            "Status OK - Safe at rendezvous point"          // Template 4 (Bottom-Right)
        )
    }

    /**
     * Retrieves all 4 templates. If a template has not been customized, returns the default string.
     */
    fun getTemplates(): List<String> {
        return (0..3).map { index ->
            getTemplate(index)
        }
    }

    /**
     * Retrieves a single template by index (0, 1, 2, or 3).
     */
    fun getTemplate(index: Int): String {
        val key = "custom_template_$index"
        return prefs.getString(key, null) ?: DEFAULT_TEMPLATES.getOrElse(index) { "Emergency Broadcast" }
    }

    /**
     * Saves a customized template string for a specific index (0, 1, 2, or 3).
     */
    fun saveTemplate(index: Int, text: String) {
        val key = "custom_template_$index"
        prefs.edit().putString(key, text.trim()).apply()
    }

    /**
     * Saves all 4 templates at once.
     */
    fun saveAllTemplates(templates: List<String>) {
        templates.forEachIndexed { index, text ->
            if (index in 0..3) {
                saveTemplate(index, text)
            }
        }
    }
}
