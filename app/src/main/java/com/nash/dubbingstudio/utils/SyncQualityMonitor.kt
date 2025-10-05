package com.nash.dubbingstudio.utils

import android.util.Log
import com.nash.dubbingstudio.model.DialogueCard

/**
 * مراقب جودة المزامنة وإعداد التقارير المتقدمة
 */
class SyncQualityMonitor {

    companion object {
        private const val TAG = "SyncQualityMonitor"
    }

    /**
     * تقرير جودة المزامنة للبطاقة الواحدة
     */
    data class SyncQualityReport(
        val cardId: String,
        val expectedDuration: Long,
        val actualDuration: Long,
        val durationDifference: Long,
        val accuracyPercentage: Float,
        val syncStatus: SyncStatus,
        val recommendedAction: String,
        val confidenceLevel: Float,
        val complexityLevel: String,
        val optimalSpeed: Float,
        val qualityScore: Float // 0-100
    )

    /**
     * تقرير شامل لجودة المشروع
     */
    data class ProjectSyncReport(
        val totalCards: Int,
        val perfectlySynced: Int,
        val wellSynced: Int,
        val needsAdjustment: Int,
        val poorlySynced: Int,
        val overallAccuracy: Float,
        val averageDeviation: Long,
        val estimatedTotalDeviation: Long,
        val qualityGrade: String, // A, B, C, D, F
        val recommendations: List<String>,
        val estimatedExportTime: Long,
        val confidenceScore: Float
    )

    /**
     * إحصائيات المزامنة
     */
    data class SyncStatistics(
        val totalCardsProcessed: Int,
        val averageAccuracy: Float,
        val bestAccuracy: Float,
        val worstAccuracy: Float,
        val mostComplexCard: String,
        val easiestCard: String,
        val totalTimeSaved: Long
    )

    /**
     * حالة المزامنة
     */
    enum class SyncStatus {
        PERFECT,      // 95-100% دقة
        EXCELLENT,    // 90-95% دقة  
        GOOD,         // 85-90% دقة
        ACCEPTABLE,   // 80-85% دقة
        NEEDS_ADJUSTMENT, // 75-80% دقة
        POOR,         // 70-75% دقة
        VERY_POOR     // أقل من 70% دقة
    }

    /**
     * تحليل جودة مزامنة بطاقة مع البيانات من AudioDurationMeasurer
     */
    fun analyzeCardSync(
        card: DialogueCard, 
        calibrationData: AudioDurationMeasurer.CalibrationData,
        textAnalysis: TextComplexityAnalyzer.TextAnalysis
    ): SyncQualityReport {
        val expectedDuration = card.getDuration()
        val actualDuration = calibrationData.averageDuration
        val difference = Math.abs(expectedDuration - actualDuration)
        
        val accuracy = if (expectedDuration > 0) {
            1 - (difference.toFloat() / expectedDuration)
        } else {
            0f
        }
        
        val accuracyPercentage = accuracy * 100
        val syncStatus = determineSyncStatus(accuracyPercentage)
        val recommendedAction = generateRecommendation(syncStatus, difference, card, textAnalysis)
        val confidenceLevel = calibrationData.confidenceScore
        val optimalSpeed = calculateOptimalSpeed(card, actualDuration)
        val qualityScore = calculateQualityScore(accuracyPercentage, confidenceLevel, textAnalysis.complexityScore)

        return SyncQualityReport(
            cardId = card.id,
            expectedDuration = expectedDuration,
            actualDuration = actualDuration,
            durationDifference = difference,
            accuracyPercentage = accuracyPercentage,
            syncStatus = syncStatus,
            recommendedAction = recommendedAction,
            confidenceLevel = confidenceLevel,
            complexityLevel = textAnalysis.readingLevel,
            optimalSpeed = optimalSpeed,
            qualityScore = qualityScore
        )
    }

    /**
     * تحديد حالة المزامنة بدقة
     */
    private fun determineSyncStatus(accuracy: Float): SyncStatus {
        return when {
            accuracy >= 95f -> SyncStatus.PERFECT
            accuracy >= 90f -> SyncStatus.EXCELLENT
            accuracy >= 85f -> SyncStatus.GOOD
            accuracy >= 80f -> SyncStatus.ACCEPTABLE
            accuracy >= 75f -> SyncStatus.NEEDS_ADJUSTMENT
            accuracy >= 70f -> SyncStatus.POOR
            else -> SyncStatus.VERY_POOR
        }
    }

    /**
     * توليد توصيات ذكية بناءً على التحليل
     */
    private fun generateRecommendation(
        status: SyncStatus, 
        difference: Long, 
        card: DialogueCard,
        textAnalysis: TextComplexityAnalyzer.TextAnalysis
    ): String {
        return when (status) {
            SyncStatus.PERFECT -> "✅ المزامنة ممتازة - جاهز للتصدير"
            SyncStatus.EXCELLENT -> "👍 المزامنة ممتازة - يمكن المتابعة"
            SyncStatus.GOOD -> "👌 المزامنة جيدة - قد تحتاج تحسين بسيط"
            SyncStatus.ACCEPTABLE -> "💡 المزامنة مقبولة - يوصى بضبط السرعة إلى ${"%.2f".format(calculateOptimalSpeed(card, card.actualDuration))}x"
            SyncStatus.NEEDS_ADJUSTMENT -> "⚠️ تحتاج تعديل - استخدم المزامنة التلقائية"
            SyncStatus.POOR -> "🔧 مزامنة ضعيفة - أعد قياس المدة واضبط الإعدادات"
            SyncStatus.VERY_POOR -> "❌ مزامنة سيئة - تحقق من النص والإعدادات"
        }
    }

    /**
     * حساب درجة الجودة الشاملة
     */
    private fun calculateQualityScore(accuracy: Float, confidence: Float, complexity: Float): Float {
        // ✅ الوزن: الدقة 50%، الثقة 30%، التعقيد 20%
        val accuracyScore = (accuracy / 100) * 0.5f
        val confidenceScore = confidence * 0.3f
        val complexityScore = (1 - complexity) * 0.2f // تعقيد أقل = جودة أعلى
        
        return (accuracyScore + confidenceScore + complexityScore) * 100
    }

    /**
     * تحليل جودة المشروع كاملاً
     */
    fun analyzeProjectSync(cards: List<DialogueCard>): ProjectSyncReport {
        if (cards.isEmpty()) {
            return ProjectSyncReport(0, 0, 0, 0, 0, 0f, 0L, 0L, "N/A", emptyList(), 0L, 0f)
        }

        // ✅ تحليل كل بطاقة
        val syncReports = cards.map { card ->
            val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
            // محاكاة calibration data (في الواقع سيأتي من AudioDurationMeasurer)
            val mockCalibration = AudioDurationMeasurer.CalibrationData(
                actualDurations = listOf(card.actualDuration),
                averageDuration = card.actualDuration,
                standardDeviation = 0L,
                confidenceScore = 0.8f,
                minDuration = card.actualDuration,
                maxDuration = card.actualDuration
            )
            analyzeCardSync(card, mockCalibration, textAnalysis)
        }

        val perfectlySynced = syncReports.count { it.syncStatus == SyncStatus.PERFECT }
        val wellSynced = syncReports.count { it.syncStatus == SyncStatus.EXCELLENT }
        val needsAdjustment = syncReports.count { it.syncStatus == SyncStatus.NEEDS_ADJUSTMENT }
        val poorlySynced = syncReports.count { it.syncStatus in setOf(SyncStatus.POOR, SyncStatus.VERY_POOR) }

        val overallAccuracy = syncReports.map { it.accuracyPercentage }.average().toFloat()
        val averageDeviation = syncReports.map { it.durationDifference }.average().toLong()
        val estimatedTotalDeviation = syncReports.sumOf { it.durationDifference }
        val qualityGrade = calculateQualityGrade(overallAccuracy)
        val estimatedExportTime = calculateEstimatedExportTime(cards)
        val confidenceScore = syncReports.map { it.confidenceLevel }.average().toFloat()

        val recommendations = generateProjectRecommendations(syncReports, cards)

        Log.d(TAG, "تقرير المشروع: دقة ${"%.1f".format(overallAccuracy)}% - ${perfectlySynced}/${cards.size} بطاقة متزامنة بشكل ممتاز")

        return ProjectSyncReport(
            totalCards = cards.size,
            perfectlySynced = perfectlySynced,
            wellSynced = wellSynced,
            needsAdjustment = needsAdjustment,
            poorlySynced = poorlySynced,
            overallAccuracy = overallAccuracy,
            averageDeviation = averageDeviation,
            estimatedTotalDeviation = estimatedTotalDeviation,
            qualityGrade = qualityGrade,
            recommendations = recommendations,
            estimatedExportTime = estimatedExportTime,
            confidenceScore = confidenceScore
        )
    }

    /**
     * حساب تقدير الجودة
     */
    private fun calculateQualityGrade(overallAccuracy: Float): String {
        return when {
            overallAccuracy >= 95f -> "A+"
            overallAccuracy >= 90f -> "A"
            overallAccuracy >= 85f -> "B+"
            overallAccuracy >= 80f -> "B"
            overallAccuracy >= 75f -> "C+"
            overallAccuracy >= 70f -> "C"
            overallAccuracy >= 60f -> "D"
            else -> "F"
        }
    }

    /**
     * حساب الوقت المتوقع للتصدير
     */
    private fun calculateEstimatedExportTime(cards: List<DialogueCard>): Long {
        val totalDuration = cards.sumOf { it.getDuration() }
        // ✅ تقدير: وقت التصدير = المدة الإجمالية + 20% overhead
        return (totalDuration * 1.2).toLong()
    }

    /**
     * توليد توصيات للمشروع
     */
    private fun generateProjectRecommendations(reports: List<SyncQualityReport>, cards: List<DialogueCard>): List<String> {
        val recommendations = mutableListOf<String>()

        val poorSyncCount = reports.count { it.syncStatus in setOf(SyncStatus.POOR, SyncStatus.VERY_POOR) }
        val needsAdjustmentCount = reports.count { it.syncStatus == SyncStatus.NEEDS_ADJUSTMENT }

        if (poorSyncCount > 0) {
            recommendations.add("$poorSyncCount بطاقة تحتاج إعادة مزامنة عاجلة")
        }

        if (needsAdjustmentCount > 0) {
            recommendations.add("$needsAdjustmentCount بطاقة تحتاج تعديل السرعة")
        }

        val overallAccuracy = reports.map { it.accuracyPercentage }.average()
        if (overallAccuracy < 80f) {
            recommendations.add("الدقة العامة منخفضة - يوصى بمراجعة إعدادات الصوت")
        }

        // ✅ تحليل التعقيد
        val complexCards = cards.count { it.text.length > 100 }
        if (complexCards > 0) {
            recommendations.add("$complexCards بطاقة معقدة - تحتاج سرعة أبطأ")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("✅ جودة المزامنة ممتازة - يمكن المتابعة للتصدير")
        }

        return recommendations
    }

    /**
     * حساب السرعة المثلى للبطاقة
     */
    fun calculateOptimalSpeed(card: DialogueCard, actualDuration: Long): Float {
        val expectedDuration = card.getDuration()
        
        if (expectedDuration <= 0 || actualDuration <= 0) {
            return card.speed
        }

        val requiredSpeed = (actualDuration.toFloat() / expectedDuration.toFloat()) * card.speed
        
        // ✅ حدود معقولة للسرعة مع مراعاة التعقيد
        val textAnalysis = TextComplexityAnalyzer.analyzeText(card.text)
        val speedBounds = when (textAnalysis.complexity) {
            TextComplexity.VERY_SIMPLE -> 0.5f to 2.5f
            TextComplexity.SIMPLE -> 0.5f to 2.2f
            TextComplexity.MODERATE -> 0.6f to 2.0f
            TextComplexity.COMPLEX -> 0.7f to 1.8f
            TextComplexity.VERY_COMPLEX -> 0.8f to 1.6f
        }
        
        return requiredSpeed.coerceIn(speedBounds.first, speedBounds.second)
    }

    /**
     * جمع إحصائيات المزامنة
     */
    fun collectSyncStatistics(reports: List<SyncQualityReport>, cards: List<DialogueCard>): SyncStatistics {
        val accuracies = reports.map { it.accuracyPercentage }
        val averageAccuracy = accuracies.average().toFloat()
        val bestAccuracy = accuracies.maxOrNull() ?: 0f
        val worstAccuracy = accuracies.minOrNull() ?: 0f
        
        val mostComplexCard = cards.maxByOrNull { it.text.length }?.text?.take(30) ?: "N/A"
        val easiestCard = cards.minByOrNull { it.text.length }?.text?.take(30) ?: "N/A"
        
        val totalTimeSaved = reports.sumOf { report ->
            Math.max(0, report.expectedDuration - report.actualDuration)
        }

        return SyncStatistics(
            totalCardsProcessed = cards.size,
            averageAccuracy = averageAccuracy,
            bestAccuracy = bestAccuracy,
            worstAccuracy = worstAccuracy,
            mostComplexCard = mostComplexCard,
            easiestCard = easiestCard,
            totalTimeSaved = totalTimeSaved
        )
    }

    /**
     * إنشاء تقرير نصي مفصل
     */
    fun generateDetailedReport(projectReport: ProjectSyncReport, statistics: SyncStatistics): String {
        return """
            📊 تقرير جودة المزامنة الشامل
            =============================
            
            📈 نظرة عامة:
            • التقدير: ${projectReport.qualityGrade}
            • الدقة العامة: ${"%.1f".format(projectReport.overallAccuracy)}%
            • عدد البطاقات: ${projectReport.totalCards}
            
            🎯 التوزيع:
            • ممتاز: ${projectReport.perfectlySynced} بطاقة
            • جيد: ${projectReport.wellSynced} بطاقة  
            • يحتاج تعديل: ${projectReport.needsAdjustment} بطاقة
            • ضعيف: ${projectReport.poorlySynced} بطاقة
            
            ⏱️ التوقيت:
            • متوسط الانحراف: ${projectReport.averageDeviation} مللي ثانية
            • الانحراف الكلي: ${projectReport.estimatedTotalDeviation} مللي ثانية
            • الوقت المتوقع: ${projectReport.estimatedExportTime / 1000} ثانية
            
            💡 التوصيات:
            ${projectReport.recommendations.joinToString("\n• ") { "• $it" }}
            
            📊 الإحصائيات:
            • أفضل دقة: ${"%.1f".format(statistics.bestAccuracy)}%
            • أسوأ دقة: ${"%.1f".format(statistics.worstAccuracy)}%
            • الوقت الموفر: ${statistics.totalTimeSaved} مللي ثانية
        """.trimIndent()
    }
}