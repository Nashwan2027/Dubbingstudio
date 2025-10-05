/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.ui.srtimport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nash.dubbingstudio.databinding.ActivitySrtImportBinding
import com.nash.dubbingstudio.ui.studio.DubbingStudioActivity
import com.nash.dubbingstudio.utils.DataTransferHelper
import com.nash.dubbingstudio.utils.SrtParser
import java.io.BufferedReader
import java.io.InputStreamReader

class SrtImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySrtImportBinding
    private val TAG = "SrtImportActivity"
    private var selectedLanguage: String = "العربية"
    private var selectedLanguageCode: String = "ar"

    // ✅ استخدام API حديث بدلاً من startActivityForResult المهمل
    private val srtFilePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                parseSrtFile(uri)
            } else {
                Log.e(TAG, "URI is null")
                Toast.makeText(this, "❌ لم يتم اختيار ملف", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "File picker result not OK: ${result.resultCode}")
            Toast.makeText(this, "❌ لم يتم اختيار ملف", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySrtImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d(TAG, "SrtImportActivity created")
        
        setupLanguageSpinner()
        
        binding.btnChooseSrtFile.setOnClickListener {
            Log.d(TAG, "Choose SRT button clicked")
            openFilePicker()
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
                
                Log.d(TAG, "تم اختيار اللغة: $selectedLanguage ($selectedLanguageCode)")
                Toast.makeText(this@SrtImportActivity, "تم اختيار اللغة: $selectedLanguage", Toast.LENGTH_SHORT).show()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun openFilePicker() {
        Log.d(TAG, "Opening file picker")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-subrip"))
        }
        srtFilePickerLauncher.launch(intent)
    }

    private fun parseSrtFile(uri: Uri) {
        Log.d(TAG, "Parsing SRT file: $uri")
        try {
            val content = readTextFromUri(uri)
            Log.d(TAG, "File content length: ${content.length}")
            
            if (content.isNotEmpty()) {
                val dialogueCards = SrtParser.parseSrtContent(content)
                Log.d(TAG, "Parsed ${dialogueCards.size} cards from SRT")
                
                if (dialogueCards.isNotEmpty()) {
                    val validCards = dialogueCards.filter { 
                        it.text.isNotBlank() && it.endTimeMs > it.startTimeMs 
                    }
                    
                    if (validCards.isNotEmpty()) {
                        Log.d(TAG, "Starting DubbingStudioActivity with ${validCards.size} valid cards")
                        
                        // ✅ تمرير اللغة المختارة إلى DubbingStudioActivity
                        val intent = Intent(this, DubbingStudioActivity::class.java).apply {
                            putExtra("selected_language", selectedLanguage)
                            putExtra("selected_language_code", selectedLanguageCode)
                        }
                        
                        DataTransferHelper.putDialogueCardsInIntent(intent, validCards.toTypedArray())
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e(TAG, "No valid cards found after filtering")
                        Toast.makeText(this, "❌ لم يتم العثور على نصوص صالحة في الملف", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "No cards parsed from SRT")
                    Toast.makeText(this, "❌ الملف لا يحتوي على بيانات SRT صالحة", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "File is empty")
                Toast.makeText(this, "❌ الملف فارغ", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing SRT file: ${e.message}", e)
            Toast.makeText(this, "❌ حدث خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append('\n')
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }
}