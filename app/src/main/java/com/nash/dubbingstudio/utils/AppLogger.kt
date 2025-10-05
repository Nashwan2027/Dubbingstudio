/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.utils

import android.util.Log

object AppLogger {
    
    private const val DEFAULT_TAG = "DubbingStudio"
    
    private var isDebugEnabled = true
    private var isInfoEnabled = true
    private var isWarnEnabled = true
    private var isErrorEnabled = true
    
    fun initialize(debug: Boolean = true) {
        isDebugEnabled = debug
        isInfoEnabled = debug
        isWarnEnabled = true
        isErrorEnabled = true
        Log.i(DEFAULT_TAG, "AppLogger initialized - Debug: $isDebugEnabled")
    }
    
    fun debug(message: String, tag: String = DEFAULT_TAG) {
        if (isDebugEnabled) {
            Log.d(tag, formatMessage(message))
        }
    }
    
    fun info(message: String, tag: String = DEFAULT_TAG) {
        if (isInfoEnabled) {
            Log.i(tag, formatMessage(message))
        }
    }
    
    fun warn(message: String, tag: String = DEFAULT_TAG) {
        if (isWarnEnabled) {
            Log.w(tag, formatMessage(message))
        }
    }
    
    fun error(message: String, exception: Exception? = null, tag: String = DEFAULT_TAG) {
        if (isErrorEnabled) {
            if (exception != null) {
                Log.e(tag, formatMessage(message), exception)
            } else {
                Log.e(tag, formatMessage(message))
            }
        }
    }
    
    fun logTts(operation: String, success: Boolean = true, details: String = "") {
        val emoji = if (success) "✅" else "❌"
        val tag = "DubbingStudio_TTS"
        info("$emoji TTS $operation: $details", tag)
    }
    
    fun logAudio(operation: String, duration: Long = 0, details: String = "") {
        val tag = "DubbingStudio_Audio"
        val durationText = if (duration > 0) "(${duration}ms)" else ""
        info("🎵 $operation $durationText: $details", tag)
    }
    
    fun logExportProgress(operation: String, progress: Int = 0, total: Int = 0, details: String = "") {
        val tag = "DubbingStudio_Export"
        val progressText = if (total > 0) "($progress/$total)" else ""
        info("📤 $operation $progressText: $details", tag)
    }
    
    fun logExport(operation: String, details: String = "") {
        val tag = "DubbingStudio_Export"
        info("📤 $operation: $details", tag)
    }
    
    fun logUi(operation: String, details: String = "") {
        val tag = "DubbingStudio_UI"
        debug("🎨 $operation: $details", tag)
    }
    
    fun logPerformance(operation: String, duration: Long, tag: String = DEFAULT_TAG) {
        if (isDebugEnabled) {
            Log.d(tag, "⏱️ [$operation] completed in ${duration}ms")
        }
    }
    
    fun logEnter(className: String, methodName: String) {
        debug("➡️ ENTER: $className.$methodName")
    }
    
    fun logExit(className: String, methodName: String) {
        debug("⬅️ EXIT: $className.$methodName")
    }
    
    fun logLifecycle(component: String, state: String) {
        info("🔁 $component: $state")
    }
    
    private fun formatMessage(message: String): String {
        val threadName = Thread.currentThread().name
        val timestamp = System.currentTimeMillis() % 100000
        return "[$timestamp|$threadName] $message"
    }
    
    fun logDialogueCard(card: com.nash.dubbingstudio.model.DialogueCard, operation: String) {
        if (isDebugEnabled) {
            debug("""
                🎴 DialogueCard $operation:
                ├── ID: ${card.id}
                ├── Text: ${card.text.take(50)}...
                ├── Duration: ${card.getDuration()}ms
                ├── Speed: ${card.speed}x
                ├── Pitch: ${card.pitch}
                └── Needs Sync: ${card.needsSync}
            """.trimIndent(), "DubbingStudio_Cards")
        }
    }
    
    // ✅ الدالة المصححة بالكامل
    fun logIntentExtras(intent: android.content.Intent?) {
        if (isDebugEnabled) {
            debug("=== فحص بيانات الـ Intent ===")
            val extras = intent?.extras
            if (extras != null) {
                extras.keySet().forEach { key ->
                    try {
                        val value = when {
                            extras.getString(key) != null -> extras.getString(key)
                            extras.getInt(key, Int.MIN_VALUE) != Int.MIN_VALUE -> extras.getInt(key).toString()
                            extras.getLong(key, Long.MIN_VALUE) != Long.MIN_VALUE -> extras.getLong(key).toString()
                            extras.getFloat(key, Float.MIN_VALUE) != Float.MIN_VALUE -> extras.getFloat(key).toString()
                            extras.getDouble(key, Double.MIN_VALUE) != Double.MIN_VALUE -> extras.getDouble(key).toString()
                            extras.getBoolean(key, false) -> extras.getBoolean(key).toString()
                            extras.getByteArray(key) != null -> "byte[${extras.getByteArray(key)!!.size}]"
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                                if (extras.getParcelable(key, android.os.Parcelable::class.java) != null) "Parcelable" 
                                else "unknown_type"
                            }
                            else -> {
                                @Suppress("DEPRECATION")
                                if (extras.getParcelable<android.os.Parcelable>(key) != null) "Parcelable" 
                                else "unknown_type"
                            }
                        }
                        debug("📦 المفتاح: $key -> القيمة: $value")
                    } catch (e: Exception) {
                        warn("❌ خطأ في قراءة المفتاح: $key - ${e.message}")
                    }
                }
            } else {
                debug("📭 لا توجد بيانات في الـ Intent")
            }
        }
    }
    
    fun logStackTrace(exception: Exception, tag: String = DEFAULT_TAG) {
        if (isErrorEnabled) {
            val stackTrace = exception.stackTraceToString()
            error("💥 Stack Trace:\n$stackTrace", null, tag)
        }
    }
}