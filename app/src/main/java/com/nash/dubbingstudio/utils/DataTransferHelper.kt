/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.utils

import android.content.Intent
import android.os.Build
import android.util.Log
import com.nash.dubbingstudio.model.DialogueCard

object DataTransferHelper {
    
    const val EXTRA_DIALOGUE_CARDS = "dialogue_cards"
    private const val TAG = "DataTransferHelper"
    private const val MAX_CARDS_SIZE = 50 // ✅ حد أقصى للبطاقات
    
    fun putDialogueCardsInIntent(intent: Intent, cards: Array<DialogueCard>): Intent {
        return try {
            AppLogger.debug("📤 إعداد ${cards.size} بطاقة للإرسال عبر الـ Intent")
            
            // ✅ تحقق من عدد البطاقات
            if (cards.size > MAX_CARDS_SIZE) {
                Log.w(TAG, "⚠️ عدد البطاقات كبير جداً: ${cards.size}، الحد الأقصى: $MAX_CARDS_SIZE")
                // نأخذ فقط أول MAX_CARDS_SIZE بطاقة
                val limitedCards = cards.take(MAX_CARDS_SIZE).toTypedArray()
                return putDialogueCardsInIntent(intent, limitedCards)
            }
            
            // ✅ تنظيف البيانات قبل الإرسال
            val safeCards = cards.map { it.withoutVoice() }.toTypedArray()
            
            AppLogger.debug("✅ تم تنظيف ${safeCards.size} بطاقة للإرسال")
            
            intent.apply {
                putExtra(EXTRA_DIALOGUE_CARDS, safeCards)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            AppLogger.error("❌ خطأ في إعداد البطاقات للإرسال", e)
            intent // إرجاع الـ intent كما هو في حالة الخطأ
        }
    }
    
    fun getDialogueCardsFromIntent(intent: Intent): List<DialogueCard> {
        return try {
            AppLogger.debug("📥 محاولة استخراج البطاقات من الـ Intent")
            
            if (!intent.hasExtra(EXTRA_DIALOGUE_CARDS)) {
                AppLogger.warn("📭 الـ Intent لا يحتوي على EXTRA_DIALOGUE_CARDS")
                return emptyList()
            }
            
            val cards = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // ✅ للإصدارات الحديثة (Android 13+)
                    intent.getParcelableArrayExtra(EXTRA_DIALOGUE_CARDS, DialogueCard::class.java)
                        ?.filterIsInstance<DialogueCard>()
                        ?.toList() ?: emptyList()
                }
                else -> {
                    // ✅ للإصدارات القديمة
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayExtra(EXTRA_DIALOGUE_CARDS)
                        ?.filterIsInstance<DialogueCard>()
                        ?.toList() ?: emptyList()
                }
            }

            AppLogger.debug("✅ تم استخراج ${cards.size} بطاقة من الـ Intent")
            
            // ✅ التحقق من صحة البيانات
            val validCards = cards.filter { card ->
                val isValid = card.id.isNotBlank() && 
                             card.text.isNotBlank() && 
                             card.endTimeMs > card.startTimeMs
                
                if (!isValid) {
                    Log.w(TAG, "❌ بطاقة غير صالحة: ${card.id} - النص: ${card.text.take(20)}...")
                }
                isValid
            }
            
            if (validCards.size != cards.size) {
                val invalidCount = cards.size - validCards.size
                AppLogger.warn("⚠️ تم تصفية $invalidCount بطاقة غير صالحة")
            }
            
            // ✅ تسجيل البطاقات الصالحة
            validCards.forEachIndexed { index, card ->
                AppLogger.logDialogueCard(card, "✅ بطاقة صالحة #${index + 1}")
            }
            
            validCards
        } catch (e: Exception) {
            AppLogger.error("❌ خطأ في استخراج البطاقات من الـ Intent", e)
            emptyList()
        }
    }
    
    // ✅ دالة جديدة للتحقق من حجم البيانات
    fun isDataSizeReasonable(cards: List<DialogueCard>): Boolean {
        val estimatedSize = cards.sumOf { it.text.length * 2 } // تقدير الحجم بالبايت
        val maxReasonableSize = 1024 * 1024 // 1 MB
        
        if (estimatedSize > maxReasonableSize) {
            Log.w(TAG, "⚠️ حجم البيانات كبير: $estimatedSize بايت")
            return false
        }
        
        return true
    }
}