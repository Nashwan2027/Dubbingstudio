/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.utils

/**
 * تعريفات الأنواع المشتركة في التطبيق
 */

// ✅ معاملات التقدم
typealias ProgressCallback = (Int, Int) -> Unit
typealias DetailedProgressCallback = (Int, Int, String) -> Unit

// ✅ معاملات الإكمال
typealias CompletionCallback<T> = (T?) -> Unit
typealias ErrorCallback = (String) -> Unit

// ✅ تعريف TextComplexity مرة واحدة فقط
enum class TextComplexity {
    VERY_SIMPLE,
    SIMPLE,
    MODERATE,
    COMPLEX,
    VERY_COMPLEX
}

// ✅ تعريفات إضافية للجودة
enum class SyncQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    VERY_POOR
}

// ✅ حالات المزامنة
enum class SyncStatus {
    PERFECT,
    GOOD,
    NEEDS_ADJUSTMENT,
    POOR,
    VERY_POOR
}