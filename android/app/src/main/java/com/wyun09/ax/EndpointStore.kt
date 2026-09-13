package com.wyun09.ax

import android.content.Context
import java.net.URL

class EndpointStore(context: Context) {
    private val prefs = context.getSharedPreferences("ax.settings", Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT

    fun save(endpoint: String) {
        prefs.edit().putString(KEY_ENDPOINT, endpoint).apply()
    }

    fun guideSeen(): Boolean = prefs.getBoolean(KEY_GUIDE_SEEN, false)

    fun markGuideSeen() {
        prefs.edit().putBoolean(KEY_GUIDE_SEEN, true).apply()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:18766"
        private const val KEY_ENDPOINT = "agent_endpoint"
        private const val KEY_GUIDE_SEEN = "guide_seen"
    }
}

fun normalizeEndpoint(raw: String): String? {
    val value = raw.trim().trimEnd('/')
    if (value.isBlank()) return null
    return runCatching {
        val url = URL(value)
        if ((url.protocol == "http" || url.protocol == "https") && url.host.isNotBlank()) value else null
    }.getOrNull()
}
