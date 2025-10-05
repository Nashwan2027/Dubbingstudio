package com.nash.dubbingstudio.utils

import android.content.Context
import android.content.SharedPreferences

object SyncPreferences {
    private const val PREFS_NAME = "sync_preferences"
    private const val KEY_PREFERRED_SPEED = "preferred_speed"
    private const val KEY_SYNC_STRATEGY = "sync_strategy"
    private const val KEY_LAST_USED_VOICE = "last_used_voice"
    
    fun savePreferredSpeed(context: Context, speed: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_PREFERRED_SPEED, speed).apply()
    }
    
    fun getPreferredSpeed(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_PREFERRED_SPEED, 1.0f)
    }
    
    fun saveSyncStrategy(context: Context, strategy: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SYNC_STRATEGY, strategy).apply()
    }
    
    fun getSyncStrategy(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SYNC_STRATEGY, "AUTO_CALCULATE") ?: "AUTO_CALCULATE"
    }
    
    fun saveLastUsedVoice(context: Context, voiceName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_USED_VOICE, voiceName).apply()
    }
    
    fun getLastUsedVoice(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_USED_VOICE, null)
    }
}