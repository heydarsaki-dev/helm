package dev.helm.hermes.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Everything Helm remembers between launches.
 *
 * Four things: where the gateway is, the key to talk to it, which model to ask
 * for, and how the interface should look. Nothing else — no cached transcript,
 * no message history, because the gateway owns those and a second copy would
 * only ever be a stale one.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("helm", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, null) ?: DEFAULT_URL
        set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

    var apiKey: String
        get() = prefs.getString(KEY_KEY, null) ?: ""
        set(value) = prefs.edit { putString(KEY_KEY, value.trim()) }

    /** Empty means "whatever the gateway defaults to", which is usually right. */
    var model: String
        get() = prefs.getString(KEY_MODEL, null) ?: ""
        set(value) = prefs.edit { putString(KEY_MODEL, value.trim()) }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.System)
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    /** Hold the screen awake while a run is live — a long shell call is not watchable. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AWAKE, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_AWAKE, value) }

    /** Show reasoning blocks inline instead of behind a disclosure. */
    var showThinking: Boolean
        get() = prefs.getBoolean(KEY_THINKING, true)
        set(value) = prefs.edit { putBoolean(KEY_THINKING, value) }

    /** Restore the last session on launch, so opening Helm lands you back in work. */
    var resumeLast: Boolean
        get() = prefs.getBoolean(KEY_RESUME, true)
        set(value) = prefs.edit { putBoolean(KEY_RESUME, value) }

    var lastSessionId: String?
        get() = prefs.getString(KEY_LAST_SESSION, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_LAST_SESSION) else putString(KEY_LAST_SESSION, value)
        }

    var configured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(value) = prefs.edit { putBoolean(KEY_CONFIGURED, value) }

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_THEME = "theme_mode"
        const val KEY_KEEP_AWAKE = "keep_awake"
        const val KEY_THINKING = "show_thinking"
        const val KEY_RESUME = "resume_last"
        const val KEY_LAST_SESSION = "last_session"
        const val KEY_CONFIGURED = "configured"

        /** `hermes gateway run` listens here on loopback by default. */
        const val DEFAULT_URL = "http://127.0.0.1:8642"
    }
}

enum class ThemeMode { System, Night, Day }
