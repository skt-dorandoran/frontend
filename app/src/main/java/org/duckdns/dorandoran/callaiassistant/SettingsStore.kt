package org.duckdns.dorandoran.callaiassistant

import android.content.Context

object SettingsStore {
        private const val KEY_CALL_INTRO_PROMPT_CUSTOM = "call_intro_prompt_custom"
        fun getCallIntroPromptCustom(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_CALL_INTRO_PROMPT_CUSTOM, "") ?: ""
        }

        fun setCallIntroPromptCustom(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CALL_INTRO_PROMPT_CUSTOM, text).apply()
        }
    private const val PREFS_NAME = "call_settings"
    private const val KEY_CALL_TEXT_SIZE_STEP = "call_text_size_step"
    private const val KEY_CALL_INTRO_PROMPT_ENABLED = "call_intro_prompt_enabled"
    private const val KEY_CALL_INTRO_PROMPT_STYLE = "call_intro_prompt_style"
    private const val KEY_CALL_INTRO_PROMPT_DEFAULTS_APPLIED = "call_intro_prompt_defaults_applied"
    private const val KEY_VOICE_CLONE_ENABLED = "voice_clone_enabled"
    private const val KEY_MY_PHONE_NUMBER = "my_phone_number"
    private const val ONE_CLICK_REPLY_COUNT = 9

    const val DEFAULT_MY_PHONE_NUMBER = "00000000000"

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

    fun isVoiceCloneEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VOICE_CLONE_ENABLED, false)
    }

    fun setVoiceCloneEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VOICE_CLONE_ENABLED, enabled).apply()
    }

    fun getMyPhoneNumber(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MY_PHONE_NUMBER, "") ?: ""
    }

    fun ensureMyPhoneNumberDefault(context: Context, deviceNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalizedDeviceNumber = deviceNumber.filter { it.isDigit() }
        if (prefs.contains(KEY_MY_PHONE_NUMBER)) {
            val current = prefs.getString(KEY_MY_PHONE_NUMBER, "")?.filter { it.isDigit() }.orEmpty()
            val isPlaceholder = current.isBlank() || current == DEFAULT_MY_PHONE_NUMBER
            if (isPlaceholder && normalizedDeviceNumber.isNotBlank()) {
                prefs.edit().putString(KEY_MY_PHONE_NUMBER, normalizedDeviceNumber).apply()
            }
            return
        }
        val defaultValue = if (normalizedDeviceNumber.isNotBlank()) normalizedDeviceNumber else DEFAULT_MY_PHONE_NUMBER
        prefs.edit().putString(KEY_MY_PHONE_NUMBER, defaultValue).apply()
    }

    fun setMyPhoneNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val digitsOnly = number.filter { it.isDigit() }
        prefs.edit().putString(KEY_MY_PHONE_NUMBER, digitsOnly).apply()
    }

    private fun oneClickReplyKey(index: Int): String {
        return "one_click_reply_${index.coerceIn(1, ONE_CLICK_REPLY_COUNT)}"
    }

    fun getOneClickReplies(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (1..ONE_CLICK_REPLY_COUNT).map { idx ->
            prefs.getString(oneClickReplyKey(idx), "") ?: ""
        }
    }

    fun setOneClickReply(context: Context, index: Int, text: String) {
        if (index !in 1..ONE_CLICK_REPLY_COUNT) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(oneClickReplyKey(index), text.trim()).apply()
    }

    fun setOneClickReplies(context: Context, replies: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (idx in 1..ONE_CLICK_REPLY_COUNT) {
            val value = replies.getOrNull(idx - 1)?.trim().orEmpty()
            editor.putString(oneClickReplyKey(idx), value)
        }
        editor.apply()
    }
}

enum class CallIntroPromptStyle(val value: String) {
    BASIC("basic"),
    SITUATION("situation"),
    ASSISTANT("assistant"),
    CUSTOM("custom")
}
