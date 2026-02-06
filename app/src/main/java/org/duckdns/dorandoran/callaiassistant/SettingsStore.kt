package org.duckdns.dorandoran.callaiassistant

import android.content.Context

object SettingsStore {
    private const val PREFS_NAME = "call_settings"
    private const val KEY_CALL_TEXT_SIZE_STEP = "call_text_size_step"
    private const val KEY_CALL_INTRO_PROMPT_ENABLED = "call_intro_prompt_enabled"
    private const val KEY_CALL_INTRO_PROMPT_STYLE = "call_intro_prompt_style"
    private const val KEY_CALL_INTRO_PROMPT_DEFAULTS_APPLIED = "call_intro_prompt_defaults_applied"

    fun getCallTextSizeStep(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CALL_TEXT_SIZE_STEP, 1)
    }

    fun setCallTextSizeStep(context: Context, step: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CALL_TEXT_SIZE_STEP, step.coerceIn(0, 2)).apply()
    }

    fun getCallTextScale(context: Context): Float {
        return when (getCallTextSizeStep(context)) {
            0 -> 0.9f
            1 -> 1.0f
            else -> 1.1f
        }
    }

    fun isCallIntroPromptEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CALL_INTRO_PROMPT_ENABLED, false)
    }

    fun ensureCallIntroPromptDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CALL_INTRO_PROMPT_DEFAULTS_APPLIED, false)) {
            prefs.edit()
                .putBoolean(KEY_CALL_INTRO_PROMPT_ENABLED, false)
                .putBoolean(KEY_CALL_INTRO_PROMPT_DEFAULTS_APPLIED, true)
                .apply()
        }
    }

    fun setCallIntroPromptEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALL_INTRO_PROMPT_ENABLED, enabled).apply()
    }

    fun getCallIntroPromptStyle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CALL_INTRO_PROMPT_STYLE, CallIntroPromptStyle.BASIC.value)
            ?: CallIntroPromptStyle.BASIC.value
    }

    fun setCallIntroPromptStyle(context: Context, style: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CALL_INTRO_PROMPT_STYLE, style).apply()
    }
}

enum class CallIntroPromptStyle(val value: String) {
    BASIC("basic"),
    SITUATION("situation"),
    ASSISTANT("assistant")
}
