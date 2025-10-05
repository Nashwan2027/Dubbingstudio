package com.nash.dubbingstudio.utils

import android.speech.tts.TextToSpeech
import android.util.Log
import com.nash.dubbingstudio.model.DialogueCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object SmartSyncManager {

    // ✅ دالة محسنة للمزامنة الجماعية - مصححة بالكامل
    suspend fun smartSyncAllCards(
        cards: List<DialogueCard>,
        tts: TextToSpeech,
        progressCallback: ((Int, Int) -> Unit)? = null
    ): List<DialogueCard> = withContext(Dispatchers.IO) {
        Log.d("SmartSyncManager", "بدء المزامنة الذكية لـ ${cards.size} بطاقة")
        
        val measurer = AudioDurationMeasurer()
        val results = mutableListOf<DialogueCard>()
        
        cards.forEachIndexed { index, card ->
            try {
                // تحليل النص أولاً
                val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
                
                // قياس المدة الفعلية باستخدام coroutine
                val calibrationData = if (card.text.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        measurer.measureActualDuration(tts, card.text, card.selectedVoice)
                    }
                } else {
                    AudioDurationMeasurer.CalibrationData(
                        actualDurations = emptyList(),
                        averageDuration = measurer.estimateDurationFromText(card.text, textAnalysis.complexity),
                        standardDeviation = 0L,
                        confidenceScore = 0.3f,
                        minDuration = 0L,
                        maxDuration = 0L
                    )
                }
                
                val qualityMonitor = SyncQualityMonitor()
                val syncReport = qualityMonitor.analyzeCardSync(card, calibrationData, textAnalysis)
                
                val optimalSpeed = if (calibrationData.confidenceScore > 0.5) {
                    qualityMonitor.calculateOptimalSpeed(card, calibrationData.averageDuration)
                } else {
                    textAnalysis.recommendedSpeed
                }
                
                val syncedCard = card.copy(
                    speed = optimalSpeed,
                    actualDuration = calibrationData.averageDuration,
                    needsSync = syncReport.syncStatus == SyncQualityMonitor.SyncStatus.NEEDS_ADJUSTMENT ||
                               syncReport.syncStatus == SyncQualityMonitor.SyncStatus.POOR ||
                               syncReport.syncStatus == SyncQualityMonitor.SyncStatus.VERY_POOR
                )
                
                results.add(syncedCard)
                progressCallback?.invoke(index + 1, cards.size)
                Log.d("SmartSyncManager", "تم مزامنة البطاقة ${index + 1}/${cards.size}")
                
            } catch (e: Exception) {
                Log.e("SmartSyncManager", "فشل مزامنة البطاقة ${index + 1}: ${e.message}")
                results.add(card)
            }
        }
        
        val finalReport = SyncQualityMonitor().analyzeProjectSync(results)
        Log.d("SmartSyncManager", "النتيجة النهائية: ${finalReport.qualityGrade} - ${finalReport.overallAccuracy}% دقة")
        
        results
    }

    // ✅ دالة سريعة للمزامنة التقديرية - مصححة
    fun quickEstimateSync(cards: List<DialogueCard>): List<DialogueCard> {
        return cards.map { card ->
            val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
            val estimatedDuration = calculateEstimatedDurationImproved(card.text, textAnalysis.complexity, textAnalysis.wordCount)
            
            val needsSync = estimatedDuration > card.getDuration() * 1.2
            
            card.copy(
                actualDuration = estimatedDuration,
                needsSync = needsSync,
                speed = if (needsSync) textAnalysis.recommendedSpeed else card.speed
            )
        }
    }

    // ✅ دالة مساعدة محسنة لحساب المدة المقدرة (تم تغيير الاسم لحل التعارض)
    private fun calculateEstimatedDurationImproved(text: String, complexity: TextComplexity, wordCount: Int): Long {
        val baseDuration = when (complexity) {
            TextComplexity.VERY_SIMPLE -> 70L * wordCount
            TextComplexity.SIMPLE -> 85L * wordCount
            TextComplexity.MODERATE -> 105L * wordCount
            TextComplexity.COMPLEX -> 130L * wordCount
            TextComplexity.VERY_COMPLEX -> 160L * wordCount
        }
        
        val lengthAdjustment = when {
            text.length < 30 -> 0.8
            text.length > 200 -> 1.3
            else -> 1.0
        }
        
        val adjustedDuration = (baseDuration * lengthAdjustment).toLong()
        return adjustedDuration.coerceIn(800L, 25000L)
    }

    // ✅ دالة المزامنة للبطاقة الواحدة - مصححة
    fun analyzeAndSyncCard(
        card: DialogueCard, 
        measurer: AudioDurationMeasurer
    ): DialogueCard {
        Log.d("SmartSyncManager", "تحليل ومزامنة البطاقة: ${card.id}")
        
        try {
            // 1. تحليل النص أولاً
            val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
            Log.d("SmartSyncManager", "تحليل النص: ${textAnalysis.complexity} - ${textAnalysis.readingLevel}")
            
            // 2. استخدام التقدير الذكي للمدة
            val estimatedDuration = measurer.estimateDurationFromText(card.text, textAnalysis.complexity)
            
            // 3. تحليل جودة المزامنة
            val qualityMonitor = SyncQualityMonitor()
            
            // إنشاء CalibrationData تقديرية
            val calibrationData = AudioDurationMeasurer.CalibrationData(
                actualDurations = listOf(estimatedDuration),
                averageDuration = estimatedDuration,
                standardDeviation = 0L,
                confidenceScore = 0.3f,
                minDuration = estimatedDuration,
                maxDuration = estimatedDuration
            )
            
            val syncReport = qualityMonitor.analyzeCardSync(card, calibrationData, textAnalysis)
            
            // 4. حساب السرعة المثلى
            val optimalSpeed = if (card.actualDuration > 0) {
                // استخدام المدة الفعلية إذا كانت متاحة
                qualityMonitor.calculateOptimalSpeed(card, card.actualDuration)
            } else {
                // استخدام السرعة المقترحة من تحليل النص
                textAnalysis.recommendedSpeed
            }
            
            Log.d("SmartSyncManager", "النتيجة: الدقة ${"%.1f".format(syncReport.accuracyPercentage)}% - السرعة المثلى: ${"%.2f".format(optimalSpeed)}x")
            
            return card.copy(
                speed = optimalSpeed.coerceIn(0.5f, 2.5f),
                actualDuration = estimatedDuration,
                needsSync = syncReport.syncStatus == SyncQualityMonitor.SyncStatus.NEEDS_ADJUSTMENT ||
                           syncReport.syncStatus == SyncQualityMonitor.SyncStatus.POOR ||
                           syncReport.syncStatus == SyncQualityMonitor.SyncStatus.VERY_POOR
            )
            
        } catch (e: Exception) {
            Log.e("SmartSyncManager", "خطأ في تحليل البطاقة: ${e.message}", e)
            // العودة إلى البطاقة الأصلية في حالة الخطأ
            return card
        }
    }

    // ✅ دوال التوافق مع الإصدارات القديمة
    fun simulateSync(cards: List<DialogueCard>): List<DialogueCard> {
        return quickEstimateSync(cards)
    }

    fun applySmartSyncToAll(cards: List<DialogueCard>): List<DialogueCard> {
        return cards.map { card ->
            if (card.needsSync && card.actualDuration > 0) {
                val optimalSpeed = calculateOptimalSpeed(card)
                card.copy(speed = optimalSpeed, needsSync = false)
            } else {
                card
            }
        }
    }

    // ✅ دوال المساعدة المحسنة
    fun getSyncQualityReport(cards: List<DialogueCard>): String {
        val qualityMonitor = SyncQualityMonitor()
        val projectReport = qualityMonitor.analyzeProjectSync(cards)
        val statistics = qualityMonitor.collectSyncStatistics(
            cards.map { card ->
                val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
                val mockCalibration = AudioDurationMeasurer.CalibrationData(
                    actualDurations = listOf(card.actualDuration),
                    averageDuration = card.actualDuration,
                    standardDeviation = 0L,
                    confidenceScore = 0.7f,
                    minDuration = card.actualDuration,
                    maxDuration = card.actualDuration
                )
                qualityMonitor.analyzeCardSync(card, mockCalibration, textAnalysis)
            },
            cards
        )
        
        return qualityMonitor.generateDetailedReport(projectReport, statistics)
    }

    fun getCardAnalysis(card: DialogueCard): String {
        val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
        return TextComplexityAnalyzer.generateAnalysisReport(textAnalysis)
    }

    // ✅ دوال التحليل المساعدة - مصححة
    private fun calculateEstimatedDuration(text: String, complexity: TextComplexity, wordCount: Int): Long {
        val baseDurationPerWord = when (complexity) {
            TextComplexity.VERY_SIMPLE -> 350L
            TextComplexity.SIMPLE -> 400L
            TextComplexity.MODERATE -> 500L
            TextComplexity.COMPLEX -> 600L
            TextComplexity.VERY_COMPLEX -> 800L
        }
        return (wordCount * baseDurationPerWord).coerceAtLeast(1000L)
    }

    fun calculateOptimalSpeed(card: DialogueCard): Float {
        if (card.actualDuration <= 0 || card.getDuration() <= 0) {
            return card.speed
        }
        
        val ratio = card.actualDuration.toFloat() / card.getDuration().toFloat()
        val optimalSpeed = card.speed / ratio
        
        return optimalSpeed.coerceIn(0.5f, 2.5f)
    }

    fun generateSyncReport(cards: List<DialogueCard>): SyncReport {
        val totalCards = cards.size
        val syncedCards = cards.count { !it.needsSync }
        val syncPercentage = if (totalCards > 0) (syncedCards * 100.0 / totalCards) else 0.0
        
        val totalTimeDifference = cards.sumOf { 
            max(0, it.actualDuration - it.getDuration()) 
        }
        
        return SyncReport(
            totalCards = totalCards,
            syncedCards = syncedCards,
            syncPercentage = syncPercentage,
            averageTimeDifference = if (totalCards > 0) totalTimeDifference / totalCards else 0,
            needsManualReview = cards.any { it.needsSync && it.actualDuration > it.getDuration() * 1.5 }
        )
    }

    // ✅ دوال إضافية للمساعدة في التحليل
    fun getSyncStatistics(cards: List<DialogueCard>): SyncStatistics {
        val totalCards = cards.size
        val perfectlySynced = cards.count { !it.needsSync }
        val needsSync = cards.count { it.needsSync }
        
        val avgSpeed = if (totalCards > 0) cards.map { it.speed }.average().toFloat() else 1.0f
        val avgPitch = if (totalCards > 0) cards.map { it.pitch }.average().toFloat() else 1.0f
        
        val totalDuration = cards.sumOf { it.getDuration() }
        val totalActualDuration = cards.sumOf { it.actualDuration }
        
        return SyncStatistics(
            totalCards = totalCards,
            perfectlySynced = perfectlySynced,
            needsSync = needsSync,
            syncPercentage = if (totalCards > 0) (perfectlySynced * 100.0 / totalCards) else 0.0,
            averageSpeed = avgSpeed,
            averagePitch = avgPitch,
            totalExpectedDuration = totalDuration,
            totalActualDuration = totalActualDuration
        )
    }

    // ✅ دالة مساعدة للمقارنة - مصححة
    private fun compareDurations(duration1: Long, duration2: Long): Int {
        return duration1.compareTo(duration2)
    }
}

// ✅ هياكل البيانات المساعدة
data class SyncReport(
    val totalCards: Int,
    val syncedCards: Int,
    val syncPercentage: Double,
    val averageTimeDifference: Long,
    val needsManualReview: Boolean
)

data class SyncStatistics(
    val totalCards: Int,
    val perfectlySynced: Int,
    val needsSync: Int,
    val syncPercentage: Double,
    val averageSpeed: Float,
    val averagePitch: Float,
    val totalExpectedDuration: Long,
    val totalActualDuration: Long
)