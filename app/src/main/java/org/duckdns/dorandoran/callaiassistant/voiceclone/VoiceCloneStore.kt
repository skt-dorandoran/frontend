package org.duckdns.dorandoran.callaiassistant.voiceclone

import android.content.Context

object VoiceCloneStore {
    private const val PREFS_NAME = "voice_clone_prefs"
    private const val KEY_VOICE_ID = "voice_id"

    fun setVoiceId(context: Context, voiceId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VOICE_ID, voiceId).apply()
    }

    fun getVoiceId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VOICE_ID, null)
    }

    fun clearVoiceId(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_VOICE_ID).apply()
    }
}
