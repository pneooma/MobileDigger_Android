package com.example.mobiledigger.file

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mobiledigger_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SOURCE_ROOT_URI = "source_root_uri"
        private const val KEY_DEST_ROOT_URI = "dest_root_uri"
        private const val KEY_STATS_LISTENED_TODAY = "stats_listened_today"
        private const val KEY_STATS_LIKED_TODAY = "stats_liked_today"
        private const val KEY_STATS_REFUSED_TODAY = "stats_refused_today"
        private const val KEY_STATS_DATE = "stats_date"
    }

    /** What a load includes: "PHONE", "NETWORK" or "BOTH" (default, the old behaviour). */
    fun getSourceMode(): String = prefs.getString("source_mode", "BOTH") ?: "BOTH"
    fun setSourceMode(mode: String) { prefs.edit().putString("source_mode", mode).apply() }

    fun getSourceRootUri(): String? = prefs.getString(KEY_SOURCE_ROOT_URI, null)
    fun setSourceRootUri(uri: String?) { prefs.edit().putString(KEY_SOURCE_ROOT_URI, uri).apply() }
    
    fun getDestinationRootUri(): String? = prefs.getString(KEY_DEST_ROOT_URI, null)
    fun setDestinationRootUri(uri: String?) { prefs.edit().putString(KEY_DEST_ROOT_URI, uri).apply() }

    // Simple daily counters
    fun incrementListened() { rollDateIfNeeded(); prefs.edit().putInt(KEY_STATS_LISTENED_TODAY, getListened()+1).apply() }
    fun incrementLiked() { rollDateIfNeeded(); prefs.edit().putInt(KEY_STATS_LIKED_TODAY, getLiked()+1).apply() }
    fun incrementRefused() { rollDateIfNeeded(); prefs.edit().putInt(KEY_STATS_REFUSED_TODAY, getRefused()+1).apply() }
    fun getListened(): Int { rollDateIfNeeded(); return prefs.getInt(KEY_STATS_LISTENED_TODAY, 0) }
    fun getLiked(): Int { rollDateIfNeeded(); return prefs.getInt(KEY_STATS_LIKED_TODAY, 0) }
    fun getRefused(): Int { rollDateIfNeeded(); return prefs.getInt(KEY_STATS_REFUSED_TODAY, 0) }
    
    // Reset counters (for app start)
    fun resetSessionCounters() {
        prefs.edit()
            .putInt(KEY_STATS_LISTENED_TODAY, 0)
            .putInt(KEY_STATS_LIKED_TODAY, 0)
            .putInt(KEY_STATS_REFUSED_TODAY, 0)
            .apply()
    }

    private fun rollDateIfNeeded() {
        val today = java.time.LocalDate.now().toString()
        val saved = prefs.getString(KEY_STATS_DATE, null)
        if (saved != today) {
            prefs.edit()
                .putString(KEY_STATS_DATE, today)
                .putInt(KEY_STATS_LISTENED_TODAY, 0)
                .putInt(KEY_STATS_LIKED_TODAY, 0)
                .putInt(KEY_STATS_REFUSED_TODAY, 0)
                .apply()
        }
    }
    
}


