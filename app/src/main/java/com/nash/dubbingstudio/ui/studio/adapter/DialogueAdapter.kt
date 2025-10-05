/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.ui.studio.adapter

import android.speech.tts.Voice
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nash.dubbingstudio.R
import com.nash.dubbingstudio.databinding.ListItemDialogueCardBinding
import com.nash.dubbingstudio.model.DialogueCard
import com.nash.dubbingstudio.utils.SmartSyncManager
import java.util.concurrent.TimeUnit

class DialogueAdapter(
    private val interactionListener: OnDialogueCardInteraction
) : ListAdapter<DialogueCard, DialogueAdapter.DialogueViewHolder>(DialogueDiffCallback()) {

    private var availableVoices: List<Voice> = emptyList()

    fun updateVoices(voices: List<Voice>) {
        availableVoices = voices
        notifyDataSetChanged()
        Log.d("DialogueAdapter", "تم تحديث ${voices.size} صوت في المحول")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DialogueViewHolder {
        val binding = ListItemDialogueCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DialogueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DialogueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DialogueViewHolder(
        private val binding: ListItemDialogueCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var textWatcher: TextWatcher? = null
        private var currentCard: DialogueCard? = null

        init {
            // إعداد مستمعي الأزرار
            setupButtonListeners()
            setupTextChangeListener()
            setupSeekBarListeners()
            
            // إضافة النقر الطويل للبطاقة
            binding.root.setOnLongClickListener {
                currentCard?.let { card ->
                    showCardContextMenu(card)
                }
                true
            }
        }

        private fun setupButtonListeners() {
            binding.btnPlaySegment.setOnClickListener {
                currentCard?.let { interactionListener.onPlayClicked(it) }
            }
            
            binding.btnPreview.setOnClickListener {
                currentCard?.let { interactionListener.onPreviewClicked(it) }
            }
            
            binding.btnAutoSync.setOnClickListener {
                currentCard?.let { interactionListener.onAutoSyncClicked(it) }
            }

            // ✅ أزرار نوع الصوت المحسنة
            binding.btnVoiceChild.setOnClickListener {
                currentCard?.let { setVoiceType(it, "child") }
            }
            binding.btnVoiceBoy.setOnClickListener {
                currentCard?.let { setVoiceType(it, "boy") }
            }
            binding.btnVoiceYoung.setOnClickListener {
                currentCard?.let { setVoiceType(it, "young") }
            }
            binding.btnVoiceMan.setOnClickListener {
                currentCard?.let { setVoiceType(it, "man") }
            }
        }

        private fun setupSeekBarListeners() {
            binding.seekbarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val speed = progress / 100.0f
                    binding.tvSpeedValue.text = "${String.format("%.2f", speed)}x" 
                    if (fromUser) {
                        val clampedSpeed = speed.coerceIn(0.5f, 2.0f) 
                        currentCard?.let { interactionListener.onSpeedChanged(it.id, clampedSpeed) }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            binding.seekbarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val pitch = progress / 100.0f
                    binding.tvPitchValue.text = String.format("%.1f", pitch)
                    if (fromUser) {
                        currentCard?.let { interactionListener.onPitchChanged(it.id, pitch) }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // ✅ الدالة المحسنة لتعيين نوع الصوت
        private fun setVoiceType(card: DialogueCard, voiceType: String) {
            Log.d("VoiceSelection", "محاولة اختيار صوت نوع: $voiceType")
            
            val voice = findVoiceByType(voiceType)
            
            voice?.let {
                interactionListener.onVoiceSelected(card.id, it)
                updateVoiceButtons(voiceType)
                showVoiceSelectedMessage(voiceType, it.name)
                Log.d("VoiceSelection", "✅ تم اختيار صوت: ${it.name} للنوع: $voiceType")
            } ?: run {
                showVoiceNotFoundMessage(voiceType)
                logAvailableVoicesForDebugging()
                
                // ✅ محاولة استخدام أول صوت متاح كبديل
                if (availableVoices.isNotEmpty()) {
                    val defaultVoice = availableVoices.first()
                    interactionListener.onVoiceSelected(card.id, defaultVoice)
                    Toast.makeText(binding.root.context, 
                        "تم استخدام الصوت الافتراضي: ${defaultVoice.name}", 
                        Toast.LENGTH_SHORT).show()
                    Log.d("VoiceSelection", "🔄 استخدام الصوت الافتراضي: ${defaultVoice.name}")
                }
            }
        }

        // ✅ دالة البحث المحسنة عن الأصوات
        private fun findVoiceByType(voiceType: String): Voice? {
            val searchTerms = when (voiceType) {
                "child" -> listOf("child", "kids", "طفل", "صغير", "أطفال", "صوت أطفال", "infant", "baby")
                "boy" -> listOf("boy", "male", "ولد", "صبي", "صوت ولد", "youth", "teen")
                "young" -> listOf("young", "youth", "شاب", "مراهق", "صوت شاب", "adolescent", "teenager")
                "man" -> listOf("man", "adult", "رجل", "كبير", "صوت رجل", "male", "adult male")
                else -> emptyList()
            }
            
            return availableVoices.firstOrNull { voice ->
                val voiceName = voice.name.lowercase()
                val localeName = voice.locale?.displayName?.lowercase() ?: ""
                
                searchTerms.any { term -> 
                    voiceName.contains(term, ignoreCase = true) || 
                    localeName.contains(term, ignoreCase = true)
                }
            }
        }

        // ✅ رسائل محسنة
        private fun showVoiceSelectedMessage(voiceType: String, voiceName: String) {
            val voiceTypeName = getVoiceTypeName(voiceType)
            val message = "تم اختيار صوت: $voiceTypeName"
            Toast.makeText(binding.root.context, message, Toast.LENGTH_SHORT).show()
        }

        private fun showVoiceNotFoundMessage(voiceType: String) {
            val voiceTypeName = getVoiceTypeName(voiceType)
            val message = "لم يتم العثور على صوت $voiceTypeName"
            Toast.makeText(binding.root.context, message, Toast.LENGTH_LONG).show()
            Log.w("VoiceSelection", "❌ $message - الأصوات المتاحة: ${availableVoices.size}")
        }

        // ✅ دالة لتسجيل جميع الأصوات المتاحة
        private fun logAvailableVoicesForDebugging() {
            Log.d("VoiceDebug", "=== جميع الأصوات المتاحة (${availableVoices.size}) ===")
            availableVoices.forEachIndexed { index, voice ->
                Log.d("VoiceDebug", "الصوت #$index: '${voice.name}' - اللغة: '${voice.locale?.displayName ?: "غير معروف"}'")
            }
        }

        private fun getVoiceTypeName(voiceType: String): String {
            return when (voiceType) {
                "child" -> "طفل"
                "boy" -> "ولد"
                "young" -> "شاب"
                "man" -> "رجل"
                else -> "غير معروف"
            }
        }

        private fun updateVoiceButtons(selectedType: String) {
            val normalColor = ContextCompat.getColor(binding.root.context, android.R.color.darker_gray)
            val selectedColor = ContextCompat.getColor(binding.root.context, R.color.primary_color)
            
            binding.btnVoiceChild.setBackgroundColor(if (selectedType == "child") selectedColor else normalColor)
            binding.btnVoiceBoy.setBackgroundColor(if (selectedType == "boy") selectedColor else normalColor)
            binding.btnVoiceYoung.setBackgroundColor(if (selectedType == "young") selectedColor else normalColor)
            binding.btnVoiceMan.setBackgroundColor(if (selectedType == "man") selectedColor else normalColor)
        }

        private fun setupTextChangeListener() {
            binding.etDialogueText.removeTextChangedListener(textWatcher)
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    currentCard?.let {
                        val newText = s.toString().trim()
                        if (it.text != newText) {
                            interactionListener.onTextChanged(it.id, newText)
                        }
                    }
                }
            }
            binding.etDialogueText.addTextChangedListener(textWatcher)
        }

        private fun showCardContextMenu(card: DialogueCard) {
            val context = binding.root.context
            val options = arrayOf("🔄 مزامنة ذكية لهذه البطاقة", "📊 تحليل النص", "ℹ️ معلومات البطاقة")
            
            AlertDialog.Builder(context)
                .setTitle("خيارات البطاقة")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            val activity = context as? com.nash.dubbingstudio.ui.studio.DubbingStudioActivity
                            activity as? DubbingStudioActivity)?.smartSyncSingleCard(card) // ✅ السطر الذي فيه الخط
                        }
                        1 -> showTextAnalysis(card)
                        2 -> showCardInfo(card)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        private fun showTextAnalysis(card: DialogueCard) {
            val analysis = SmartSyncManager.getCardAnalysis(card)
            val context = binding.root.context
            
            AlertDialog.Builder(context)
                .setTitle("📊 تحليل النص")
                .setMessage(analysis)
                .setPositiveButton("حسناً", null)
                .show()
        }

        private fun showCardInfo(card: DialogueCard) {
            val info = """
                🎴 معلومات البطاقة:
                
                • المدة المتوقعة: ${card.getDuration()} مللي ثانية
                • المدة الفعلية: ${card.actualDuration} مللي ثانية
                • السرعة الحالية: ${"%.2f".format(card.speed)}x
                • حدة الصوت: ${"%.1f".format(card.pitch)}
                • تحتاج مزامنة: ${if (card.needsSync) "نعم" else "لا"}
            """.trimIndent()
            
            val context = binding.root.context
            AlertDialog.Builder(context)
                .setTitle("ℹ️ معلومات البطاقة")
                .setMessage(info)
                .setPositiveButton("حسناً", null)
                .show()
        }

        fun bind(card: DialogueCard) {
            currentCard = card

            // تعيين البيانات الأساسية
            binding.tvCardCounter.text = "${adapterPosition + 1} / ${itemCount}"
            binding.tvTiming.text = formatTimeRange(card.startTimeMs, card.endTimeMs)
            
            // إعداد مؤشر المزامنة
            binding.ivSyncWarning.visibility = if (card.needsSync) View.VISIBLE else View.GONE
            
            // إعداد النص
            binding.etDialogueText.removeTextChangedListener(textWatcher)
            if (binding.etDialogueText.text.toString() != card.text) {
                binding.etDialogueText.setText(card.text)
            }
            binding.etDialogueText.addTextChangedListener(textWatcher)
            
            // إعداد أشرطة التمرير
            binding.seekbarSpeed.max = 200 
            binding.seekbarSpeed.progress = (card.speed * 100).toInt().coerceIn(0, 200)
            binding.tvSpeedValue.text = "${String.format("%.2f", card.speed)}x"
            binding.seekbarPitch.max = 200
            binding.seekbarPitch.progress = (card.pitch * 100).toInt().coerceIn(0, 200)
            binding.tvPitchValue.text = String.format("%.1f", card.pitch)

            // إعداد حالة التشغيل
            binding.btnPlaySegment.setImageResource(
                if (card.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            
            binding.syncProgress.visibility = if (card.isPlaying) View.VISIBLE else View.GONE
            binding.btnPreview.visibility = if (card.needsSync) View.VISIBLE else View.GONE
            
            // إعداد الأصوات
            setupVoiceSpinner(card)
            updateVoiceButtonsBasedOnCurrentVoice(card.selectedVoice)
        }
        
        private fun updateVoiceButtonsBasedOnCurrentVoice(voice: Voice?) {
            voice?.let {
                val voiceName = it.name.lowercase()
                
                when {
                    voiceName.contains("child") || voiceName.contains("طفل") || voiceName.contains("صغير") -> 
                        updateVoiceButtons("child")
                    voiceName.contains("boy") || voiceName.contains("ولد") || voiceName.contains("صبي") -> 
                        updateVoiceButtons("boy")
                    voiceName.contains("young") || voiceName.contains("شاب") || voiceName.contains("مراهق") -> 
                        updateVoiceButtons("young")
                    voiceName.contains("man") || voiceName.contains("رجل") || voiceName.contains("كبير") -> 
                        updateVoiceButtons("man")
                    else -> updateVoiceButtons("")
                }
            } ?: run {
                updateVoiceButtons("")
            }
        }
        
        private fun setupVoiceSpinner(card: DialogueCard) {
            val voiceNames = availableVoices.map { it.name }
            val adapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item,
                voiceNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerVoiceSelector.adapter = adapter

            // تحديد الصوت الحالي في السبينر
            val currentVoiceIndex = availableVoices.indexOfFirst { it.name == card.selectedVoice?.name }
            if (currentVoiceIndex != -1) {
                binding.spinnerVoiceSelector.setSelection(currentVoiceIndex)
            } else if (availableVoices.isNotEmpty()) {
                binding.spinnerVoiceSelector.setSelection(0)
            }

            // مستمع اختيار الصوت من السبينر
            binding.spinnerVoiceSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position >= 0 && position < availableVoices.size) {
                        val selectedVoice = availableVoices[position]
                        if (card.selectedVoice?.name != selectedVoice.name) {
                            interactionListener.onVoiceSelected(card.id, selectedVoice)
                            updateVoiceButtonsBasedOnCurrentVoice(selectedVoice)
                            Log.d("VoiceSelection", "تم اختيار الصوت من السبينر: ${selectedVoice.name}")
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        
        private fun formatMsToTime(ms: Long): String {
            val seconds = ms / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            val remainingMs = ms % 1000
            return String.format("%02d:%02d.%03d", minutes, remainingSeconds, remainingMs)
        }
        
        private fun formatTimeRange(startMs: Long, endMs: Long): String {
            val startHms = formatMsToHms(startMs)
            val endHms = formatMsToHms(endMs)
            return "$startHms - $endHms"
        }

        private fun formatMsToHms(ms: Long): String {
            return String.format(
                "%02d:%02d:%02d.%03d",
                TimeUnit.MILLISECONDS.toHours(ms),
                TimeUnit.MILLISECONDS.toMinutes(ms) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(ms) % TimeUnit.MINUTES.toSeconds(1),
                ms % 1000
            )
        }
    }
}

class DialogueDiffCallback : DiffUtil.ItemCallback<DialogueCard>() {
    override fun areItemsTheSame(oldItem: DialogueCard, newItem: DialogueCard): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DialogueCard, newItem: DialogueCard): Boolean {
        return oldItem.text == newItem.text &&
               oldItem.selectedVoice?.name == newItem.selectedVoice?.name &&
               oldItem.speed == newItem.speed &&
               oldItem.pitch == newItem.pitch &&
               oldItem.isPlaying == newItem.isPlaying &&
               oldItem.startTimeMs == newItem.startTimeMs &&
               oldItem.endTimeMs == newItem.endTimeMs &&
               oldItem.needsSync == newItem.needsSync &&
               oldItem.actualDuration == newItem.actualDuration
    }
}