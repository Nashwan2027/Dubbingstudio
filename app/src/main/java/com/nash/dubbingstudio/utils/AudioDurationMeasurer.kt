package com.nash.dubbingstudio.utils

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.sqrt
import kotlin.coroutines.resume

/**
 * نظام قياس المدة الفعلية للصوت بدقة عالية
 * النسخة المصححة - متوافقة مع النظام الحالي
 */
class AudioDurationMeasurer {

    companion object {
        private const val TAG = "AudioDurationMeasurer"
        private const val CALIBRATION_SAMPLES = 3
        private const val SAMPLE_DELAY_MS = 100L
    }

    data class CalibrationData(
        val actualDurations: List<Long>,
        val averageDuration: Long,
        val standardDeviation: Long,
        val confidenceScore: Float,
        val minDuration: Long,
        val maxDuration: Long
    )

    // ✅ نظام القياس الأساسي (بدون tts) - للاستخدام في setupTtsListeners
    private val startTimes = mutableMapOf<String, Long>()
    
    fun startMeasuring(cardId: String) {
        startTimes[cardId] = System.currentTimeMillis()
        Log.d(TAG, "بدء القياس للبطاقة: $cardId")
    }
    
    fun stopMeasuring(cardId: String): Long {
        val startTime = startTimes[cardId] ?: return 0L
        startTimes.remove(cardId)
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "انتهاء القياس للبطاقة: $cardId - المدة: $duration مللي ثانية")
        return duration
    }

    // ✅ نظام القياس المتقدم (مع tts) - للاستخدام في SmartSyncManager
    suspend fun measureActualDuration(
        tts: TextToSpeech,
        text: String, 
        voice: android.speech.tts.Voice? = null
    ): CalibrationData {
        Log.d(TAG, "بدء قياس المدة للنص: '${text.take(50)}...'")
        
        val durations = mutableListOf<Long>()
        
        repeat(CALIBRATION_SAMPLES) { sampleIndex ->
            try {
                val duration = measureSingleDuration(tts, text, voice, sampleIndex)
                if (duration > 0) {
                    durations.add(duration)
                    Log.d(TAG, "العينة $sampleIndex: $duration مللي ثانية")
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في العينة $sampleIndex: ${e.message}")
            }
            
            if (sampleIndex < CALIBRATION_SAMPLES - 1) {
                delay(SAMPLE_DELAY_MS)
            }
        }
        
        return calculateCalibrationData(durations, text)
    }

    private suspend fun measureSingleDuration(
        tts: TextToSpeech,
        text: String, 
        voice: android.speech.tts.Voice?, 
        sampleIndex: Int
    ): Long = suspendCancellableCoroutine { continuation ->
        val utteranceId = "measure_${System.currentTimeMillis()}_$sampleIndex"
        var startTime = 0L
        
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == utteranceId) {
                    startTime = System.nanoTime()
                    Log.d(TAG, "بدء القياس للعينة $sampleIndex")
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == utteranceId && continuation.isActive) {
                    Log.e(TAG, "خطأ في قياس المدة للعينة $sampleIndex")
                    continuation.resume(0L)
                }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == utteranceId && continuation.isActive) {
                    Log.e(TAG, "خطأ في قياس المدة للعينة $sampleIndex: $errorCode")
                    continuation.resume(0L)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == utteranceId && continuation.isActive) {
                    val endTime = System.nanoTime()
                    val durationMs = (endTime - startTime) / 1_000_000
                    Log.d(TAG, "انتهاء القياس للعينة $sampleIndex: $durationMs مللي ثانية")
                    continuation.resume(durationMs)
                }
            }
        }

        tts.setOnUtteranceProgressListener(listener)

        try {
            voice?.let { tts.voice = it }
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)

            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "فشل speak للعينة $sampleIndex: $result")
                if (continuation.isActive) continuation.resume(0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تشغيل TTS للعينة $sampleIndex", e)
            if (continuation.isActive) continuation.resume(0L)
        }
    }

    private fun calculateCalibrationData(durations: List<Long>, text: String): CalibrationData {
        return if (durations.isEmpty()) {
            Log.w(TAG, "لا توجد عينات صالحة للنص: '${text.take(50)}...'")
            CalibrationData(emptyList(), 0L, 0L, 0f, 0L, 0L)
        } else {
            val average = durations.average().toLong()
            val min = durations.minOrNull() ?: 0L
            val max = durations.maxOrNull() ?: 0L
            val stdDev = calculateStandardDeviation(durations, average)
            val confidence = calculateConfidenceScore(durations, stdDev)
            
            Log.d(TAG, "نتيجة المعايرة: متوسط=$average, انحراف=$stdDev, ثقة=${"%.2f".format(confidence)}")
            
            CalibrationData(durations, average, stdDev, confidence, min, max)
        }
    }

    private fun calculateStandardDeviation(durations: List<Long>, mean: Long): Long {
        if (durations.size <= 1) return 0L
        
        val variance = durations.map { duration ->
            val diff = duration - mean
            diff * diff
        }.average()
        
        return sqrt(variance).toLong()
    }

    private fun calculateConfidenceScore(durations: List<Long>, stdDev: Long): Float {
        if (durations.isEmpty()) return 0f
        
        val mean = durations.average()
        val coefficientOfVariation = if (mean > 0) {
            stdDev / mean
        } else {
            1.0
        }
        
        val cvScore = 1.0 - coefficientOfVariation.coerceIn(0.0, 1.0)
        val sampleSizeScore = durations.size.toFloat() / CALIBRATION_SAMPLES
        val consistencyScore = calculateConsistencyScore(durations)
        
        return (cvScore * 0.5 + sampleSizeScore * 0.3 + consistencyScore * 0.2).toFloat().coerceIn(0f, 1f)
    }

    private fun calculateConsistencyScore(durations: List<Long>): Float {
        if (durations.size <= 1) return 1.0f
        
        val sorted = durations.sorted()
        val range = sorted.last() - sorted.first()
        val mean = sorted.average()
        val rangeRatio = if (mean > 0) range / mean else 1.0
        return 1.0f - rangeRatio.toFloat().coerceIn(0f, 1f)
    }

    fun estimateDurationFromText(text: String, complexity: TextComplexity = TextComplexity.MODERATE): Long {
        val baseDurationPerChar = when (complexity) {
            TextComplexity.VERY_SIMPLE -> 85L
            TextComplexity.SIMPLE -> 95L
            TextComplexity.MODERATE -> 110L
            TextComplexity.COMPLEX -> 130L
            TextComplexity.VERY_COMPLEX -> 160L
        }
        
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val adjustedDuration = text.length * baseDurationPerChar
        
        val wordAdjustment = when {
            wordCount < 3 -> 0.8
            wordCount > 15 -> 1.3
            else -> 1.0
        }
        
        val finalDuration = (adjustedDuration * wordAdjustment).toLong()
        return finalDuration.coerceIn(500L, 15000L)
    }

    fun calculateDurationAccuracy(estimated: Long, actual: Long): Float {
        if (estimated <= 0 || actual <= 0) return 0f
        
        val difference = Math.abs(estimated - actual).toFloat()
        val maxDuration = Math.max(estimated, actual).toFloat()
        val accuracy = 1 - (difference / maxDuration)
        
        return accuracy.coerceIn(0f, 1f)
    }

    fun analyzeMeasurementQuality(calibrationData: CalibrationData): String {
        return when {
            calibrationData.confidenceScore > 0.8 -> "قياس عالي الدقة"
            calibrationData.confidenceScore > 0.6 -> "قياس جيد"
            calibrationData.confidenceScore > 0.4 -> "قياس مقبول"
            else -> "يحتاج إعادة قياس"
        }
    }

    // ✅ دالة مساعدة للقياس السريع (بدون معايرة)
    suspend fun quickMeasure(tts: TextToSpeech, text: String, voice: android.speech.tts.Voice? = null): Long {
        return try {
            val calibration = measureActualDuration(tts, text, voice)
            calibration.averageDuration
        } catch (e: Exception) {
            Log.e(TAG, "فشل القياس السريع: ${e.message}")
            estimateDurationFromText(text)
        }
    }
}