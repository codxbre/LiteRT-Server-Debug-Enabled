package com.litert.server.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("litert_settings", Context.MODE_PRIVATE)

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

    var maxTokens: Int
        get() = prefs.getInt("max_tokens", 4096)
        set(value) = prefs.edit().putInt("max_tokens", value).apply()

    var useGpu: Boolean
        get() = prefs.getBoolean("use_gpu", true)
        set(value) = prefs.edit().putBoolean("use_gpu", value).apply()

    var topK: Int
        get() = prefs.getInt("top_k", 40)
        set(value) = prefs.edit().putInt("top_k", value).apply()

    var topP: Float
        get() = prefs.getFloat("top_p", 0.9f)
        set(value) = prefs.edit().putFloat("top_p", value).apply()

    // Context window is usually fixed by the model, but we can store a preference for it
    var contextWindow: Int
        get() = prefs.getInt("context_window", 16384)
        set(value) = prefs.edit().putInt("context_window", value).apply()
}
