/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.model

import android.os.Parcelable
import android.speech.tts.Voice
import kotlinx.parcelize.Parcelize

@Parcelize
data class DialogueCard(
    val id: String,
    val text: String,
    val startTimeMs: Long = 0,
    val endTimeMs: Long = 0,
    var selectedVoice: Voice? = null,
    var speed: Float = 1.0f,
    var pitch: Float = 1.0f,
    var isPlaying: Boolean = false,
    // ✅ الحقول الجديدة للمزامنة
    var needsSync: Boolean = false,
    var actualDuration: Long = 0L
) : Parcelable {

    /**
     * يحسب المدة الزمنية للمقطع بالمللي ثانية
     */
    fun getDuration(): Long = endTimeMs - startTimeMs
    
    /**
     * ينشئ نسخة من البطاقة مع إزالة معلومات الصوت (للتنظيف مثلاً)
     */
    fun withoutVoice(): DialogueCard = this.copy(selectedVoice = null)
    
    /**
     * ✅ جديد: يحسب السرعة المطلوبة للمزامنة
     */
    fun calculateRequiredSpeed(): Float {
        val expectedDuration = getDuration()
        if (expectedDuration <= 0 || actualDuration <= 0) return speed
        
        val requiredSpeed = (actualDuration.toFloat() / expectedDuration.toFloat()) * speed
        return requiredSpeed.coerceIn(0.5f, 2.5f) // حدود معقولة
    }
    
    /**
     * ✅ جديد: يتحقق إذا كانت البطاقة تحتاج مزامنة
     */
    fun checkSyncNeeded(): Boolean {
        return actualDuration > 0 && actualDuration > getDuration()
    }
}