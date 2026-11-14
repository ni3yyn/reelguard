package com.reelguard.app.data

import android.content.Context

object ProtectionPreferences {
    private const val PREFS_NAME = "reelguard_preferences"
    private const val KEY_PROTECTION_ENABLED = "protection_enabled"
    private const val KEY_SESSION_END_EPOCH_MS = "session_end_epoch_ms"
    private const val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isProtectionEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_PROTECTION_ENABLED, false)

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
        if (!enabled) clearSession(context)
    }

    fun hasAccessibilityDisclosureBeenAccepted(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)

    fun setAccessibilityDisclosureAccepted(context: Context, accepted: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, accepted)
            .apply()
    }

    fun sessionEndEpochMs(context: Context): Long =
        preferences(context).getLong(KEY_SESSION_END_EPOCH_MS, 0L)

    fun setSessionEndEpochMs(context: Context, endEpochMs: Long) {
        preferences(context).edit().putLong(KEY_SESSION_END_EPOCH_MS, endEpochMs).apply()
    }

    fun clearSession(context: Context) {
        preferences(context).edit().remove(KEY_SESSION_END_EPOCH_MS).apply()
    }
}
