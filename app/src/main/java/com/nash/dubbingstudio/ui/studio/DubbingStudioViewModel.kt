package com.nash.dubbingstudio.ui.studio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nash.dubbingstudio.model.DialogueCard

class DubbingStudioViewModel : ViewModel() {

    private val _dialogueCards = MutableLiveData<List<DialogueCard>>()
    val dialogueCards: LiveData<List<DialogueCard>> get() = _dialogueCards

    private val _availableVoices = MutableLiveData<List<Voice>>()
    val availableVoices: LiveData<List<Voice>> get() = _availableVoices

    private val _isExporting = MutableLiveData<Boolean>(false)
    val isExporting: LiveData<Boolean> get() = _isExporting

    private val _exportErrorVm = MutableLiveData<String?>()
    val exportError: LiveData<String?> get() = _exportErrorVm

    // ✅ دالة محسنة لتحميل الأصوات (تستقبل قائمة جاهزة)
    fun loadAvailableVoices(voices: List<Voice>) {
        _availableVoices.value = voices
        Log.d("DubbingStudioViewModel", "✅ تم تحميل ${voices.size} صوت")
        
        // تسجيل الأصوات المحملة للتصحيح
        voices.forEachIndexed { index, voice ->
            Log.d("VoiceDebug", "🔊 الصوت المحمل #$index: ${voice.name} - ${voice.locale} - ${getVoiceEngineInfo(voice)}")
        }
        
        // تحليل إحصائي للأصوات المحملة
        analyzeLoadedVoices(voices)
    }

    // ✅ تحليل إحصائي للأصوات المحملة
    private fun analyzeLoadedVoices(voices: List<Voice>) {
        if (voices.isEmpty()) {
            Log.w("DubbingStudioViewModel", "⚠️ لم يتم تحميل أي أصوات")
            return
        }

        val languages = voices.mapNotNull { it.locale?.language }.distinct()
        val engines = voices.map { getVoiceEngineInfo(it) }.distinct()
        
        Log.d("VoiceAnalysis", "📊 تحليل الأصوات المحملة:")
        Log.d("VoiceAnalysis", "   • العدد الإجمالي: ${voices.size}")
        Log.d("VoiceAnalysis", "   • اللغات المدعومة: ${languages.joinToString()}")
        Log.d("VoiceAnalysis", "   • المحركات المكتشفة: ${engines.joinToString()}")
        
        // تحليل توزيع الأصوات حسب اللغة
        languages.forEach { language ->
            val count = voices.count { it.locale?.language == language }
            Log.d("VoiceAnalysis", "   • اللغة '$language': $count صوت")
        }
    }

    // ✅ الحصول على معلومات المحرك من الصوت
    private fun getVoiceEngineInfo(voice: Voice): String {
        return try {
            voice.javaClass.getDeclaredField("mEngine").let { field ->
                field.isAccessible = true
                field.get(voice) as? String ?: "unknown_engine"
            }
        } catch (e: Exception) {
            "engine_unavailable"
        }
    }

    // ✅ دالة مساعدة للبحث عن أصوات بلغة محددة
    fun findVoicesByLanguage(languageCode: String): List<Voice> {
        val allVoices = _availableVoices.value ?: emptyList()
        return allVoices.filter { voice ->
            voice.locale?.language?.equals(languageCode, ignoreCase = true) == true
        }
    }

    // ✅ دالة مساعدة للبحث عن أصوات بمحرك محدد
    fun findVoicesByEngine(enginePackage: String): List<Voice> {
        val allVoices = _availableVoices.value ?: emptyList()
        return allVoices.filter { voice ->
            getVoiceEngineInfo(voice).contains(enginePackage, ignoreCase = true)
        }
    }

    // ✅ الحصول على إحصاءات الأصوات
    fun getVoiceStatistics(): VoiceStatistics {
        val allVoices = _availableVoices.value ?: emptyList()
        
        val languages = allVoices.mapNotNull { it.locale?.language }.distinct().size
        val engines = allVoices.map { getVoiceEngineInfo(it) }.distinct().size
        val vocalizerCount = allVoices.count { 
            getVoiceEngineInfo(it).contains("vocalizer", ignoreCase = true) 
        }
        val googleCount = allVoices.count { 
            getVoiceEngineInfo(it).contains("google", ignoreCase = true) 
        }
        
        return VoiceStatistics(
            totalVoices = allVoices.size,
            uniqueLanguages = languages,
            uniqueEngines = engines,
            vocalizerVoices = vocalizerCount,
            googleVoices = googleCount
        )
    }

    // ✅ دوال إدارة البطاقات الحالية
    fun setCardPlayingState(cardId: String, isPlaying: Boolean) {
        _dialogueCards.value = _dialogueCards.value?.map { card ->
            if (card.id == cardId) {
                card.copy(isPlaying = isPlaying)
            } else {
                card
            }
        }
    }

    fun setInitialCards(cards: Array<DialogueCard>) {
        _dialogueCards.value = cards.toList()
        Log.d("DubbingStudioViewModel", "تم تعيين ${cards.size} بطاقة أولية")
    }

    fun resetAllPlayingStates() {
        val currentList = _dialogueCards.value ?: return
        val updatedList = currentList.map { it.copy(isPlaying = false) }
        _dialogueCards.value = updatedList
    }

    fun updateCardVoice(cardId: String, voice: Voice) {
        _dialogueCards.value = _dialogueCards.value?.map { 
            if (it.id == cardId) it.copy(selectedVoice = voice) else it 
        }
        Log.d("DubbingStudioViewModel", "تم تحديث الصوت للبطاقة: $cardId -> ${voice.name}")
    }

    fun updateCardSpeed(cardId: String, speed: Float) {
        _dialogueCards.value = _dialogueCards.value?.map { 
            if (it.id == cardId) it.copy(speed = speed) else it 
        }
    }

    fun updateCardPitch(cardId: String, pitch: Float) {
        _dialogueCards.value = _dialogueCards.value?.map { 
            if (it.id == cardId) it.copy(pitch = pitch) else it 
        }
    }

    fun updateCardText(cardId: String, newText: String) {
        _dialogueCards.value = _dialogueCards.value?.map {
            if (it.id == cardId) it.copy(text = newText) else it
        }
    }

    fun updateCardTiming(cardId: String, startTime: Long, endTime: Long) {
        _dialogueCards.value = _dialogueCards.value?.map { card ->
            if (card.id == cardId) card.copy(
                startTimeMs = startTime,
                endTimeMs = endTime
            ) else card
        }
    }

    fun updateCardActualDuration(cardId: String, actualDuration: Long) {
        val currentList = _dialogueCards.value ?: return
        val updatedList = currentList.map { card ->
            if (card.id == cardId) {
                val expectedDuration = card.getDuration()
                val needsSync = actualDuration > expectedDuration
                card.copy(actualDuration = actualDuration, needsSync = needsSync)
            } else {
                card
            }
        }
        _dialogueCards.value = updatedList
        Log.d("DubbingStudioViewModel", "تم تحديث المدة الفعلية للبطاقة: $cardId -> ${actualDuration}ms")
    }

    fun applyAutoSync(cardId: String, requiredSpeed: Float) {
        _dialogueCards.value = _dialogueCards.value?.map { 
            if (it.id == cardId) it.copy(
                speed = requiredSpeed,
                needsSync = false
            ) else it 
        }
        Log.d("DubbingStudioViewModel", "تم تطبيق المزامنة للبطاقة: $cardId -> السرعة: ${requiredSpeed}x")
    }

    fun addNewCard(card: DialogueCard) {
        val currentList = _dialogueCards.value ?: emptyList()
        _dialogueCards.value = currentList + card
        Log.d("DubbingStudioViewModel", "تم إضافة بطاقة جديدة، الإجمالي: ${currentList.size + 1}")
    }

    fun setExportingState(exporting: Boolean) {
        _isExporting.value = exporting
        Log.d("DubbingStudioViewModel", "حالة التصدير: $exporting")
    }

    fun removeCard(card: DialogueCard) {
        val currentList = _dialogueCards.value?.toMutableList() ?: return
        currentList.remove(card)
        _dialogueCards.value = currentList
        Log.d("DubbingStudioViewModel", "تم إزالة بطاقة، الإجمالي: ${currentList.size}")
    }

    fun addCardAtIndex(index: Int, card: DialogueCard) {
        val currentList = _dialogueCards.value?.toMutableList() ?: return
        if (index >= 0 && index <= currentList.size) {
            currentList.add(index, card)
            _dialogueCards.value = currentList
            Log.d("DubbingStudioViewModel", "تم إضافة بطاقة في الموضع: $index")
        }
    }

    fun updateCards(cards: List<DialogueCard>) {
        _dialogueCards.value = cards
        Log.d("DubbingStudioViewModel", "تم تحديث جميع البطاقات: ${cards.size} بطاقة")
    }

    fun setExportError(error: String?) {
        _exportErrorVm.value = error
        Log.d("DubbingStudioViewModel", "خطأ التصدير: $error")
    }

    fun clearExportError() {
        _exportErrorVm.value = null
        Log.d("DubbingStudioViewModel", "تم مسح خطأ التصدير")
    }
}

// ✅ نموذج بيانات إحصاءات الأصوات
data class VoiceStatistics(
    val totalVoices: Int,
    val uniqueLanguages: Int,
    val uniqueEngines: Int,
    val vocalizerVoices: Int,
    val googleVoices: Int
)