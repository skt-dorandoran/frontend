package org.duckdns.dorandoran.callaiassistant

import android.content.Context

object SettingsStore {
    private const val PREFS_NAME = "call_settings"
    private const val KEY_CALL_TEXT_SIZE_STEP = "call_text_size_step"

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

}
