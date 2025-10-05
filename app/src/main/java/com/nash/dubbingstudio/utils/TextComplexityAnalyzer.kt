package com.nash.dubbingstudio.utils

import android.util.Log
import java.util.*
import kotlin.math.log10

/**
 * محلل تعقيد النص لتقدير المدة المطلوبة بدقة
 */
object TextComplexityAnalyzer {

    private const val TAG = "TextComplexityAnalyzer"

    /**
     * بيانات تحليل النص الشامل
     */
    data class TextAnalysis(
        val complexity: TextComplexity,
        val wordCount: Int,
        val charCount: Int,
        val avgWordLength: Double,
        val complexityScore: Float, // 0-1
        val estimatedDuration: Long,
        val language: String = "ar",
        val readingLevel: String,
        val recommendedSpeed: Float
    )

    /**
     * تحليل النص بشكل شامل
     */
    fun analyzeText(text: String): TextAnalysis {
        Log.d(TAG, "تحليل النص: '${text.take(50)}...'")
        
        val words = extractWords(text)
        val wordCount = words.size
        val charCount = text.length
        val avgWordLength = if (wordCount > 0) charCount.toDouble() / wordCount else 0.0
        
        // ✅ حساب درجة التعقيد
        val complexityScore = calculateComplexityScore(text, words, wordCount, avgWordLength)
        val complexity = determineComplexityLevel(complexityScore)
        val estimatedDuration = calculateEstimatedDuration(text, complexity, wordCount)
        val readingLevel = determineReadingLevel(complexityScore, wordCount)
        val recommendedSpeed = calculateRecommendedSpeed(complexityScore)
        
        return TextAnalysis(
            complexity = complexity,
            wordCount = wordCount,
            charCount = charCount,
            avgWordLength = avgWordLength,
            complexityScore = complexityScore,
            estimatedDuration = estimatedDuration,
            language = detectLanguage(text),
            readingLevel = readingLevel,
            recommendedSpeed = recommendedSpeed
        )
    }

    /**
     * استخراج الكلمات من النص
     */
    private fun extractWords(text: String): List<String> {
        return text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
    }

    /**
     * حساب درجة التعقيد (0-1)
     */
    private fun calculateComplexityScore(
        text: String,
        words: List<String>,
        wordCount: Int,
        avgWordLength: Double
    ): Float {
        if (text.isBlank()) return 0f
        
        var score = 0f

        // ✅ عامل عدد الكلمات (25%)
        val wordCountFactor = (wordCount.coerceIn(1, 50) / 50.0f) * 0.25f
        
        // ✅ عامل طول الكلمات (20%)
        val wordLengthFactor = (avgWordLength.coerceIn(1.0, 10.0) / 10.0f).toFloat() * 0.20f
        
        // ✅ عامل التنوع اللغوي (15%)
        val uniqueWords = words.distinct().size
        val diversityFactor = if (wordCount > 0) {
            (uniqueWords.toFloat() / wordCount) * 0.15f
        } else {
            0f
        }
        
        // ✅ عامل علامات الترقيم (10%)
        val punctuationCount = text.count { it in ".,!?;:" }
        val punctuationFactor = (punctuationCount.toFloat() / wordCount.coerceAtLeast(1)) * 0.10f
        
        // ✅ عامل التعقيد النحوي (15%)
        val complexStructures = detectComplexStructures(text)
        val structureFactor = (complexStructures / 5.0f).coerceIn(0f, 1f) * 0.15f
        
        // ✅ عامل الكلمات المعقدة (15%)
        val complexWords = detectComplexWords(words)
        val complexWordsFactor = (complexWords / wordCount.coerceAtLeast(1).toFloat()) * 0.15f
        
        score = wordCountFactor + wordLengthFactor + diversityFactor + 
                punctuationFactor + structureFactor + complexWordsFactor
        
        Log.d(TAG, "درجة التعقيد: ${"%.2f".format(score)}")
        
        return score.coerceIn(0f, 1f)
    }

    /**
     * اكتشاف التراكيب المعقدة
     */
    private fun detectComplexStructures(text: String): Int {
        var complexCount = 0
        
        // ✅ الجمل الطويلة
        val sentences = text.split(Regex("[.!?]+"))
        complexCount += sentences.count { it.length > 100 }
        
        // ✅ الجمل المعقدة (تحتوي على أكثر من فاصل)
        complexCount += sentences.count { it.count { char -> char in ",;" } > 2 }
        
        // ✅ وجود أرقام
        if (text.contains(Regex("\\d+"))) complexCount++
        
        // ✅ وجود رموز خاصة
        if (text.contains(Regex("[@#$%^&*()]"))) complexCount++
        
        return complexCount
    }

    /**
     * اكتشاف الكلمات المعقدة
     */
    private fun detectComplexWords(words: List<String>): Int {
        return words.count { word ->
            // كلمات طويلة
            word.length > 8 ||
            // كلمات تحتوي على أحرف خاصة
            word.contains(Regex("[^\\w\\s]")) ||
            // كلمات مركبة
            word.contains("-") || word.contains("_")
        }
    }

    /**
     * تحديد مستوى التعقيد
     */
    private fun determineComplexityLevel(score: Float): TextComplexity {
        return when {
            score < 0.2 -> TextComplexity.VERY_SIMPLE
            score < 0.4 -> TextComplexity.SIMPLE
            score < 0.6 -> TextComplexity.MODERATE
            score < 0.8 -> TextComplexity.COMPLEX
            else -> TextComplexity.VERY_COMPLEX
        }
    }

    /**
     * تحديد مستوى القراءة
     */
    private fun determineReadingLevel(complexityScore: Float, wordCount: Int): String {
        return when {
            complexityScore < 0.3 && wordCount < 10 -> "مبتدئ"
            complexityScore < 0.5 -> "متوسط"
            complexityScore < 0.7 -> "متقدم"
            else -> "خبير"
        }
    }

    /**
     * حساب السرعة الموصى بها
     */
    private fun calculateRecommendedSpeed(complexityScore: Float): Float {
        return when {
            complexityScore < 0.3 -> 1.2f  // نصوص بسيطة - سرعة أعلى
            complexityScore < 0.6 -> 1.0f  // نصوص متوسطة - سرعة عادية
            else -> 0.8f                   // نصوص معقدة - سرعة أبطأ
        }
    }

    /**
     * حساب المدة المقدرة
     */
    private fun calculateEstimatedDuration(text: String, complexity: TextComplexity, wordCount: Int): Long {
        val baseDuration = when (complexity) {
            TextComplexity.VERY_SIMPLE -> 70L * wordCount
            TextComplexity.SIMPLE -> 85L * wordCount
            TextComplexity.MODERATE -> 105L * wordCount
            TextComplexity.COMPLEX -> 130L * wordCount
            TextComplexity.VERY_COMPLEX -> 160L * wordCount
        }
        
        // ✅ تعديل بناءً على طول النص
        val lengthAdjustment = when {
            text.length < 30 -> 0.8  // نصوص قصيرة أسرع
            text.length > 200 -> 1.3 // نصوص طويلة أبطأ
            else -> 1.0
        }
        
        val adjustedDuration = (baseDuration * lengthAdjustment).toLong()
        return adjustedDuration.coerceIn(800L, 25000L) // بين 0.8 و25 ثانية
    }

    /**
     * اكتشاف اللغة
     */
    private fun detectLanguage(text: String): String {
        val arabicChars = text.count { it in '\u0600'..'\u06FF' }
        val totalChars = text.length.toFloat()
        
        return if (totalChars > 0 && arabicChars / totalChars > 0.5) {
            "ar"
        } else {
            "unknown"
        }
    }

    /**
     * مقارنة تحليلين للنص
     */
    fun compareAnalyses(analysis1: TextAnalysis, analysis2: TextAnalysis): Float {
        val durationDiff = Math.abs(analysis1.estimatedDuration - analysis2.estimatedDuration).toFloat()
        val maxDuration = Math.max(analysis1.estimatedDuration, analysis2.estimatedDuration).toFloat()
        
        val durationSimilarity = if (maxDuration > 0) {
            1 - (durationDiff / maxDuration)
        } else {
            1f
        }
        
        val complexitySimilarity = 1 - Math.abs(analysis1.complexityScore - analysis2.complexityScore)
        
        return (durationSimilarity * 0.6f + complexitySimilarity * 0.4f).coerceIn(0f, 1f)
    }

    /**
     * إنشاء تقرير تحليل النص
     */
    fun generateAnalysisReport(analysis: TextAnalysis): String {
        return """
            📊 تقرير تحليل النص:
            • التعقيد: ${analysis.complexity}
            • عدد الكلمات: ${analysis.wordCount}
            • متوسط طول الكلمة: ${"%.1f".format(analysis.avgWordLength)}
            • مستوى القراءة: ${analysis.readingLevel}
            • السرعة الموصى بها: ${"%.1f".format(analysis.recommendedSpeed)}x
            • المدة المقدرة: ${analysis.estimatedDuration} مللي ثانية
        """.trimIndent()
    }
}