/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.ui.writescript

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log // ✅ تم إضافة الاستيراد
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nash.dubbingstudio.databinding.ActivityWriteScriptBinding
import com.nash.dubbingstudio.model.DialogueCard
import com.nash.dubbingstudio.ui.studio.DubbingStudioActivity
import com.nash.dubbingstudio.utils.DataTransferHelper
import java.util.*

class WriteScriptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWriteScriptBinding
    private var selectedLanguage: String = "العربية"
    private var selectedLanguageCode: String = "ar"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteScriptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupLanguageSpinner()
        setupTextWatcher()
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnContinueToDubbing.setOnClickListener {
            val script = binding.etScript.text.toString().trim()
            if (script.isNotEmpty()) {
                val dialogueCards = createDialogueCardsFromScript(script)
                
                if (dialogueCards.isNotEmpty()) {
                    // ✅ إصلاح: تمرير اللغة بشكل صحيح
                    val intent = Intent(this, DubbingStudioActivity::class.java).apply {
                        putExtra("selected_language", selectedLanguage)
                        putExtra("selected_language_code", selectedLanguageCode)
                    }
                    DataTransferHelper.putDialogueCardsInIntent(intent, dialogueCards)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "لم يتم إنشاء أي بطاقات من النص", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء كتابة نص أولاً", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnAddSpeakerBreak.setOnClickListener {
            val currentText = binding.etScript.text.toString()
            val cursorPosition = binding.etScript.selectionStart
            val newText = StringBuilder(currentText).insert(cursorPosition, "\n* ").toString()
            binding.etScript.setText(newText)
            binding.etScript.setSelection(cursorPosition + 3)
        }
    }
    
    private fun setupLanguageSpinner() {
        val languages = arrayOf("العربية", "English", "Français", "Español")
        val languageCodes = arrayOf("ar", "en", "fr", "es")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedLanguage = languages[position]
                selectedLanguageCode = languageCodes[position]
                
                // ✅ الآن ستعمل Log.d لأننا أضفنا الاستيراد
                Log.d("WriteScriptActivity", "تم اختيار اللغة: $selectedLanguage ($selectedLanguageCode)")
                Toast.makeText(this@WriteScriptActivity, "تم اختيار اللغة: $selectedLanguage", Toast.LENGTH_SHORT).show()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupTextWatcher() {
        binding.etScript.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
                updateWordCount(wordCount)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun updateWordCount(count: Int) {
        binding.btnContinueToDubbing.text = "متابعة ($count كلمة)"
    }
    
    private fun createDialogueCardsFromScript(script: String): Array<DialogueCard> {
        val dialogueCards = script.split(Regex("\\*\\s*", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text ->  
                DialogueCard(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    startTimeMs = calculateStartTime(index),
                    endTimeMs = calculateEndTime(index, text),
                    selectedVoice = null,
                    speed = 1.0f,
                    pitch = 1.0f,
                    isPlaying = false
                )
            }
        
        Toast.makeText(this, "تم إنشاء ${dialogueCards.size} بطاقة", Toast.LENGTH_SHORT).show()
        return dialogueCards.toTypedArray()
    }
    
    private fun calculateStartTime(index: Int): Long {
        return (index * 5000L)
    }
    
    private fun calculateEndTime(index: Int, text: String): Long {
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val speechDuration = (wordCount * 500L).coerceAtLeast(2000L).coerceAtMost(10000L)
        return calculateStartTime(index) + speechDuration
    }
}