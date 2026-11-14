package com.reelguard.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedLogEntry(
    val id: Long,
    val timestamp: String,
    val eventType: String,
    val phase: String,
    val summary: String,
    val nodeTreeText: String
)

/**
 * Thread-safe singleton repository holding the last 100 captured AccessibilityEvents
 * and full UI Snapshot node hierarchies for selector inspection and clipboard export.
 */
object DebugLogRepository {
    private const val PREFS_NAME = "reelguard_prefs"
    private const val KEY_DEBUG_MODE = "debug_inspector_mode_enabled"
    const val MAX_LOGS = 100

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var nextId = 1L

    private val _isDebugMode = MutableStateFlow(false)
    val isDebugMode: StateFlow<Boolean> = _isDebugMode.asStateFlow()

    private val _isReelGuardInForeground = MutableStateFlow(false)
    val isReelGuardInForeground: StateFlow<Boolean> = _isReelGuardInForeground.asStateFlow()

    private val _logs = MutableStateFlow<List<CapturedLogEntry>>(emptyList())
    val logs: StateFlow<List<CapturedLogEntry>> = _logs.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDebugMode.value = prefs.getBoolean(KEY_DEBUG_MODE, false)
    }

    fun setDebugMode(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        _isDebugMode.value = enabled
    }

    fun setReelGuardInForeground(inForeground: Boolean) {
        _isReelGuardInForeground.value = inForeground
    }

    fun recordEvent(
        eventType: String,
        phase: String,
        summary: String,
        nodeTreeText: String
    ) {
        if (!_isDebugMode.value && eventType != "UI_SNAPSHOT") return
        synchronized(lock) {
            val entry = CapturedLogEntry(
                id = nextId++,
                timestamp = timeFormat.format(Date()),
                eventType = eventType,
                phase = phase,
                summary = summary,
                nodeTreeText = nodeTreeText
            )
            _logs.value = (listOf(entry) + _logs.value).take(MAX_LOGS)
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }

    fun formatLogsForClipboard(): String {
        val snapshot = synchronized(lock) { _logs.value }
        if (snapshot.isEmpty()) return "No ReelGuard debug logs captured."

        return buildString {
            appendLine("=== ReelGuard Accessibility Inspector Logs (${snapshot.size} entries) ===")
            snapshot.forEach { entry ->
                appendLine()
                appendLine("[${entry.timestamp}] ${entry.eventType} (phase=${entry.phase})")
                appendLine("Summary: ${entry.summary}")
                if (entry.nodeTreeText.isNotBlank()) {
                    appendLine(entry.nodeTreeText)
                }
            }
        }
    }
}
