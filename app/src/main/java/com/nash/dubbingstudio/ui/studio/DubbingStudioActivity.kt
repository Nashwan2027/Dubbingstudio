package com.nash.dubbingstudio.ui.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.nash.dubbingstudio.R
import com.nash.dubbingstudio.databinding.ActivityDubbingStudioBinding
import com.nash.dubbingstudio.model.DialogueCard
import com.nash.dubbingstudio.services.AudioExportService
import com.nash.dubbingstudio.ui.studio.adapter.DialogueAdapter
import com.nash.dubbingstudio.ui.studio.adapter.OnDialogueCardInteraction
import com.nash.dubbingstudio.utils.AppLogger
import com.nash.dubbingstudio.utils.DataTransferHelper
import com.nash.dubbingstudio.utils.SyncPreferences
import com.nash.dubbingstudio.utils.AudioDurationMeasurer
import com.nash.dubbingstudio.utils.SmartSyncManager
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DubbingStudioActivity : AppCompatActivity(), TextToSpeech.OnInitListener, OnDialogueCardInteraction {

    private lateinit var binding: ActivityDubbingStudioBinding
    private lateinit var viewModel: DubbingStudioViewModel
    private lateinit var dialogueAdapter: DialogueAdapter
    private lateinit var exportReceiver: BroadcastReceiver
    private var tts: TextToSpeech? = null
    private var isExportReceiverRegistered = false
    private var isTtsInitialized = false
    private val TAG = "DubbingStudioActivity"
    private var selectedLanguageCode: String = "ar"
    
    private val durationMeasurer = AudioDurationMeasurer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDubbingStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        logIntentExtras()
        
        selectedLanguageCode = intent.getStringExtra("selected_language_code") ?: "ar"
        Log.d(TAG, "اللغة المختارة: $selectedLanguageCode")

        checkAvailableTtsEngines()
        loadSyncPreferences()

        viewModel = ViewModelProvider(this).get(DubbingStudioViewModel::class.java)
        setupRecyclerView()
        setupExportReceiver()

        val dialogueCards = DataTransferHelper.getDialogueCardsFromIntent(intent)
        viewModel.setInitialCards(dialogueCards.toTypedArray())

        detectAndSetupVocalizerTTS()

        viewModel.dialogueCards.observe(this) { cards ->
            dialogueAdapter.submitList(cards)
            binding.btnExportFinal.isEnabled = !cards.isNullOrEmpty()
        }

        viewModel.availableVoices.observe(this) { voices ->
            dialogueAdapter.updateVoices(voices)
            Log.d(TAG, "تم تحميل ${voices.size} صوت للغة $selectedLanguageCode")
        }

        viewModel.isExporting.observe(this) { isExporting ->
            binding.btnExportFinal.isEnabled = isExporting != true
            binding.progressBar.visibility = if (isExporting == true) View.VISIBLE else View.GONE
        }

        setupSmartSyncButtons()

        if (!checkVocalizerInstallation()) {
            Handler(Looper.getMainLooper()).postDelayed({
                showVocalizerInstallPrompt()
            }, 3000)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_dubbing_studio, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_switch_engine -> {
                showEngineSelectionDialog()
                true
            }
            android.R.id.home -> {
                super.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEngineSelectionDialog() {
        val availableEngines = getAvailableTtsEngines()
        
        if (availableEngines.size <= 1) {
            Toast.makeText(this, "لا توجد محركات أخرى متاحة", Toast.LENGTH_SHORT).show()
            return
        }
        
        val engineNames = availableEngines.map { getEngineDisplayName(it) }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("اختر محرك الصوت")
            .setItems(engineNames) { dialog, which ->
                val selectedEngine = availableEngines[which]
                switchTtsEngine(selectedEngine)
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getEngineDisplayName(packageName: String): String {
        return when (packageName) {
            "es.codefactory.vocalizertts" -> "Vocalizer TTS"
            "com.google.android.tts" -> "Google Text-to-Speech"
            "com.samsung.SMT" -> "Samsung Text-to-Speech"
            "com.ivona.tts" -> "IVONA Text-to-Speech"
            "com.svox.pico" -> "Pico TTS"
            "org.nobody.multitts" -> "Multi TTS"
            else -> packageName
        }
    }

    private fun logIntentExtras() {
        Log.d(TAG, "=== فحص بيانات الـ Intent ===")
        val extras = intent?.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                try {
                    val value = when {
                        extras.getString(key) != null -> extras.getString(key)
                        extras.getInt(key, Int.MIN_VALUE) != Int.MIN_VALUE -> extras.getInt(key).toString()
                        extras.getLong(key, Long.MIN_VALUE) != Long.MIN_VALUE -> extras.getLong(key).toString()
                        extras.getFloat(key, Float.MIN_VALUE) != Float.MIN_VALUE -> extras.getFloat(key).toString()
                        extras.getDouble(key, Double.MIN_VALUE) != Double.MIN_VALUE -> extras.getDouble(key).toString()
                        extras.getBoolean(key, false) -> extras.getBoolean(key).toString()
                        extras.getByteArray(key) != null -> "byte[${extras.getByteArray(key)!!.size}]"
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            if (extras.getParcelable(key, android.os.Parcelable::class.java) != null) "Parcelable" 
                            else "unknown_type"
                        }
                        else -> {
                            @Suppress("DEPRECATION")
                            if (extras.getParcelable<android.os.Parcelable>(key) != null) "Parcelable" 
                            else "unknown_type"
                        }
                    }
                    Log.d(TAG, "المفتاح: $key -> القيمة: $value")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ خطأ في قراءة المفتاح: $key - ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "لا توجد بيانات في الـ Intent")
        }
    }

    private fun checkAvailableTtsEngines() {
        Log.d(TAG, "=== فحص محركات TTS المتاحة ===")
        
        val packageManager = packageManager
        val engines = packageManager.getInstalledPackages(PackageManager.GET_SERVICES)
        
        val desiredEngines = listOf(
            "com.google.android.tts",
            "com.samsung.SMT",
            "es.codefactory.vocalizertts",
            "org.nobody.multitts",
            "com.svox.pico",
            "com.ivona.tts"
        )
        
        val ttsEngines = engines.filter { pkg ->
            pkg.services?.any { service ->
                service.permission?.contains("tts") == true ||
                service.name?.contains("tts", ignoreCase = true) == true ||
                pkg.packageName.contains("tts", ignoreCase = true)
            } == true || desiredEngines.contains(pkg.packageName)
        }
        
        Log.d(TAG, "عدد محركات TTS المكتشفة: ${ttsEngines.size}")
        ttsEngines.forEach { engine ->
            Log.d(TAG, "المحرك: ${engine.packageName}")
        }
        
        desiredEngines.forEach { engineName ->
            try {
                packageManager.getPackageInfo(engineName, 0)
                Log.d(TAG, "✅ المحرك المطلوب متوفر: $engineName")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "❌ المحرك المطلوب غير مثبت: $engineName")
            }
        }
    }
    
    // ✅ أضف هذه الدالة في DubbingStudioActivity
fun smartSyncSingleCard(card: DialogueCard) {
    if (!isTtsInitialized) {
        Toast.makeText(this, "محرك الصوت ليس جاهزاً بعد", Toast.LENGTH_SHORT).show()
        return
    }

    viewModel.setExportingState(true)
    
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // قياس المدة الفعلية للبطاقة
            val calibrationData = durationMeasurer.measureActualDuration(tts!!, card.text, card.selectedVoice)
            val actualDuration = calibrationData.averageDuration
            
            runOnUiThread {
                viewModel.setExportingState(false)
                
                if (actualDuration > 0) {
                    // تحديث المدة الفعلية للبطاقة
                    viewModel.updateCardActualDuration(card.id, actualDuration)
                    
                    // حساب السرعة المثلى
                    val expectedDuration = card.getDuration()
                    val requiredSpeed = calculateRequiredSpeed(actualDuration, expectedDuration, card.speed)
                    
                    // تطبيق المزامنة
                    viewModel.applyAutoSync(card.id, requiredSpeed)
                    
                    val message = """
                        ✅ تمت المزامنة الذكية:
                        • المدة الفعلية: ${formatMsToTime(actualDuration)}
                        • المدة المطلوبة: ${formatMsToTime(expectedDuration)}
                        • السرعة الجديدة: ${"%.2f".format(requiredSpeed)}x
                    """.trimIndent()
                    
                    AlertDialog.Builder(this@DubbingStudioActivity)
                        .setTitle("المزامنة الذكية")
                        .setMessage(message)
                        .setPositiveButton("حسناً", null)
                        .show()
                } else {
                    Toast.makeText(this@DubbingStudioActivity, "فشل قياس المدة الفعلية", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                viewModel.setExportingState(false)
                Toast.makeText(this@DubbingStudioActivity, "خطأ في المزامنة: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

    private fun loadSyncPreferences() {
        val preferredSpeed = SyncPreferences.getPreferredSpeed(this)
        val strategy = SyncPreferences.getSyncStrategy(this)
        Log.d(TAG, "تم تحميل التفضيلات - السرعة: $preferredSpeed, الاستراتيجية: $strategy")
    }

    private fun setupExportReceiver() {
        exportReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                viewModel.setExportingState(false)
                
                when (intent.action) {
                    AudioExportService.ACTION_EXPORT_COMPLETE -> {
                        val filePath = intent.getStringExtra("file_path")
                        val errorMessage = intent.getStringExtra("error_message")
                        
                        Log.d("ExportResult", "تم استلام نتيجة التصدير")
                        
                        if (filePath != null) {
                            val file = File(filePath)
                            
                            if (file.exists() && file.length() > 0) {
                                showExportSuccess("تم التصدير بنجاح: ${file.name}")
                            } else {
                                showExportError("الملف غير موجود أو فارغ")
                            }
                        } else {
                            showExportError(errorMessage ?: "فشل التصدير")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerExportReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterExportReceiver()
    }

    private fun registerExportReceiver() {
        if (!isExportReceiverRegistered) {
            val filter = IntentFilter(AudioExportService.ACTION_EXPORT_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(exportReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(exportReceiver, filter)
            }
            isExportReceiverRegistered = true
            Log.d("ExportResult", "تم تسجيل مستقبل التصدير")
        }
    }

    private fun unregisterExportReceiver() {
        if (isExportReceiverRegistered) {
            unregisterReceiver(exportReceiver)
            isExportReceiverRegistered = false
            Log.d("ExportResult", "تم إلغاء تسجيل مستقبل التصدير")
        }
    }

    private fun showExportSuccess(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showExportError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "خطأ: $message", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            
            val currentEngine = tts?.defaultEngine ?: "غير معروف"
            Log.d(TAG, "✅ تم تهيئة TTS بنجاح مع المحرك: $currentEngine")
            
            loadVocalizerVoices(selectedLanguageCode)
            setupTtsLanguage()
            setupTtsListeners()
            
            Toast.makeText(this, getCurrentEngineInfo(), Toast.LENGTH_LONG).show()
            
        } else {
            Log.e(TAG, "❌ فشل تهيئة محرك الصوت: $status")
            
            if (status == TextToSpeech.ERROR) {
                Handler(Looper.getMainLooper()).postDelayed({
                    tryFallbackEngine()
                }, 1000)
            }
        }
    }

    private fun setupTtsListeners() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { 
                    utteranceId?.let { 
                        viewModel.setCardPlayingState(it, true)
                        durationMeasurer.startMeasuring(it)
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread { 
                    Log.e(TAG, "❌ خطأ TTS للجملة: $utteranceId")
                    viewModel.resetAllPlayingStates() 
                }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread { 
                    Log.e(TAG, "❌ خطأ TTS للجملة $utteranceId: $errorCode")
                    viewModel.resetAllPlayingStates() 
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread { 
                    utteranceId?.let { cardId ->
                        val actualDuration = durationMeasurer.stopMeasuring(cardId)
                        if (actualDuration > 0) {
                            viewModel.updateCardActualDuration(cardId, actualDuration)
                        }
                    }
                    viewModel.resetAllPlayingStates() 
                }
            }
        })
    }

    private fun setupTtsLanguage() {
        Log.d(TAG, "=== إعداد لغة TTS ===")
        Log.d(TAG, "اللغة المطلوبة: $selectedLanguageCode")
        
        val localesToTry = mutableListOf<Locale>()
        
        when (selectedLanguageCode) {
            "ar" -> {
                localesToTry.add(Locale("ar", "SA"))
                localesToTry.add(Locale("ar", "EG"))
                localesToTry.add(Locale("ar"))
            }
            "en" -> {
                localesToTry.add(Locale.ENGLISH)
                localesToTry.add(Locale.US)
                localesToTry.add(Locale.UK)
            }
            "fr" -> {
                localesToTry.add(Locale.FRENCH)
                localesToTry.add(Locale.FRANCE)
                localesToTry.add(Locale.CANADA_FRENCH)
            }
            "es" -> {
                localesToTry.add(Locale("es", "ES"))
                localesToTry.add(Locale("es", "MX"))
                localesToTry.add(Locale("es"))
            }
            else -> localesToTry.add(Locale("ar"))
        }
        
        localesToTry.add(Locale.ENGLISH)
        localesToTry.add(Locale.getDefault())
        
        var languageSet = false
        
        for (locale in localesToTry) {
            try {
                val result = tts?.setLanguage(locale)
                Log.d(TAG, "محاولة تعيين اللغة: ${locale.displayLanguage} (${locale.language}_${locale.country}) - النتيجة: $result")
                
                if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    Log.d(TAG, "✅ تم تعيين اللغة بنجاح: ${locale.displayLanguage}")
                    languageSet = true
                    selectedLanguageCode = locale.language
                    break
                } else {
                    Log.w(TAG, "❌ اللغة غير متاحة: ${locale.displayLanguage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في تعيين اللغة ${locale.displayLanguage}: ${e.message}")
            }
        }
        
        if (!languageSet) {
            Log.e(TAG, "❌ فشل تعيين أي لغة متاحة")
            Toast.makeText(this, "اللغة المختارة غير مدعومة، جاري استخدام اللغة الافتراضية", Toast.LENGTH_LONG).show()
        }
    }

    private fun detectAndSetupVocalizerTTS(): Boolean {
        Log.d(TAG, "=== بدء اكتشاف وتهيئة محركات الصوت ===")
        
        val availableEngines = getAvailableTtsEngines()
        
        if (availableEngines.isEmpty()) {
            Log.w(TAG, "⚠️ لم يتم العثور على أي محركات TTS")
            tts = TextToSpeech(this, this)
            return false
        }
        
        for (engine in availableEngines) {
            try {
                Log.d(TAG, "🔧 محاولة استخدام المحرك: $engine")
                
                val checkIntent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                checkIntent.setPackage(engine)
                
                val resolveInfo = packageManager.resolveActivity(checkIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo != null) {
                    tts = TextToSpeech(this, this, engine)
                    Log.d(TAG, "✅ تم تهيئة المحرك بنجاح: $engine")
                    return true
                } else {
                    Log.w(TAG, "⚠️ المحرك لا يدعم البيانات المطلوبة: $engine")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ فشل استخدام المحرك $engine: ${e.message}")
            }
        }
        
        Log.w(TAG, "🔁 استخدام المحرك الافتراضي")
        tts = TextToSpeech(this, this)
        return false
    }

    private fun getAvailableTtsEngines(): List<String> {
        Log.d(TAG, "=== البحث عن محركات TTS المتاحة ===")
        
        val packageManager = packageManager
        val availableEngines = mutableListOf<String>()
        
        val preferredEngines = listOf(
            "es.codefactory.vocalizertts",
            "com.google.android.tts",
            "com.samsung.SMT", 
            "com.ivona.tts",
            "com.svox.pico",
            "org.nobody.multitts"
        )
        
        preferredEngines.forEach { enginePackage ->
            try {
                packageManager.getPackageInfo(enginePackage, 0)
                availableEngines.add(enginePackage)
                Log.d(TAG, "✅ المحرك متوفر: $enginePackage")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "❌ المحرك غير مثبت: $enginePackage")
            }
        }
        
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            resolveInfos.forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (!availableEngines.contains(packageName) && !packageName.contains("android")) {
                    availableEngines.add(packageName)
                    Log.d(TAG, "🔍 محرك إضافي مكتشف: $packageName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في البحث عن محركات إضافية: ${e.message}")
        }
        
        Log.d(TAG, "إجمالي المحركات المتاحة: ${availableEngines.size}")
        return availableEngines
    }

    private fun switchTtsEngine(enginePackage: String): Boolean {
        Log.d(TAG, "🔄 محاولة التبديل إلى المحرك: $enginePackage")
        
        try {
            tts?.stop()
            tts?.shutdown()
            
            tts = TextToSpeech(this, this, enginePackage)
            isTtsInitialized = false
            
            var attempts = 0
            while (!isTtsInitialized && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            
            if (isTtsInitialized) {
                Log.d(TAG, "✅ تم التبديل بنجاح إلى المحرك: $enginePackage")
                Toast.makeText(this, "تم التبديل إلى المحرك: ${getEngineDisplayName(enginePackage)}", Toast.LENGTH_SHORT).show()
                return true
            } else {
                Log.e(TAG, "❌ فشل تهيئة المحرك الجديد: $enginePackage")
                Toast.makeText(this, "فشل التبديل إلى المحرك المطلوب", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في التبديل إلى المحرك $enginePackage: ${e.message}")
            Toast.makeText(this, "خطأ في التبديل: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun getCurrentEngineInfo(): String {
        return if (tts != null && isTtsInitialized) {
            val engine = tts?.defaultEngine ?: "غير معروف"
            "المحرك الحالي: ${getEngineDisplayName(engine)}"
        } else {
            "المحرك غير مهيأ"
        }
    }

    private fun tryFallbackEngine() {
        val availableEngines = getAvailableTtsEngines()
        val currentEngine = tts?.defaultEngine
        
        availableEngines.forEach { engine ->
            if (engine != currentEngine) {
                Log.d(TAG, "🔄 محاولة التبديل التلقائي إلى: $engine")
                if (switchTtsEngine(engine)) {
                    return
                }
            }
        }
        
        Log.e(TAG, "❌ فشل جميع محاولات التبديل التلقائي")
        Toast.makeText(this, "فشل تهيئة جميع محركات الصوت المتاحة", Toast.LENGTH_LONG).show()
    }

    private fun checkVocalizerInstallation(): Boolean {
        return try {
            packageManager.getPackageInfo("es.codefactory.vocalizertts", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun showVocalizerInstallPrompt() {
        AlertDialog.Builder(this)
            .setTitle("محرك الصوت الموصى به")
            .setMessage("لتحقيق أفضل جودة للدبلجة، نوصي بتثبيت محرك Vocalizer TTS. هل تريد تثبيته الآن؟")
            .setPositiveButton("تثبيت") { dialog, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=es.codefactory.vocalizertts")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=es.codefactory.vocalizertts")
                    }
                    startActivity(intent)
                }
                dialog.dismiss()
            }
            .setNegativeButton("لاحقاً") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadVocalizerVoices(languageCode: String = "ar") {
        Log.d(TAG, "=== تحميل الأصوات مع دعم Vocalizer ===")
        
        if (tts == null) {
            Log.e(TAG, "❌ TTS غير مهيأ")
            return
        }
        
        if (tts!!.engines.isEmpty()) {
            Log.w(TAG, "⏳ TTS ليس جاهزاً بعد، إعادة المحاولة...")
            Handler(Looper.getMainLooper()).postDelayed({
                loadVocalizerVoices(languageCode)
            }, 500)
            return
        }
        
        val allVoices = tts!!.voices.toList()
        Log.d(TAG, "🔊 إجمالي الأصوات المكتشفة: ${allVoices.size}")
        
        allVoices.forEachIndexed { index, voice ->
            Log.d(TAG, "🔊 الصوت #$index: '${voice.name}' - اللغة: '${voice.locale}'")
        }
        
        val filteredVoices = detectVoicesWithStrategies(allVoices, languageCode)
        
        Log.d(TAG, "✅ الأصوات المصفاة: ${filteredVoices.size}")
        filteredVoices.forEach { voice ->
            Log.d(TAG, "🎯 الصوت المختار: ${voice.name} (${voice.locale})")
        }
        
        viewModel.loadAvailableVoices(filteredVoices)
    }

    private fun detectVoicesWithStrategies(allVoices: List<Voice>, languageCode: String): List<Voice> {
        val results = mutableListOf<Voice>()
        
        val vocalizerVoices = allVoices.filter { voice ->
            voice.name.contains("vocalizer", ignoreCase = true) ||
            getEngineName(voice).contains("vocalizer", ignoreCase = true) ||
            voice.locale?.language?.equals(languageCode, ignoreCase = true) == true
        }
        
        if (vocalizerVoices.isNotEmpty()) {
            Log.d(TAG, "🎯 تم العثور على ${vocalizerVoices.size} صوت من Vocalizer")
            results.addAll(vocalizerVoices)
        }
        
        val languageVoices = allVoices.filter { voice ->
            voice.locale?.language?.equals(languageCode, ignoreCase = true) == true
        }
        
        if (languageVoices.isNotEmpty()) {
            Log.d(TAG, "🎯 تم العثور على ${languageVoices.size} صوت للغة $languageCode")
            results.addAll(languageVoices)
        }
        
        val arabicVoices = allVoices.filter { voice ->
            voice.name.contains("arabic", ignoreCase = true) ||
            voice.name.contains("عربي", ignoreCase = true) ||
            voice.name.contains("العربية", ignoreCase = true) ||
            voice.locale?.displayName?.contains("arabic", ignoreCase = true) == true
        }
        
        if (arabicVoices.isNotEmpty()) {
            Log.d(TAG, "🎯 تم العثور على ${arabicVoices.size} صوت عربي")
            results.addAll(arabicVoices)
        }
        
        if (results.isEmpty() && allVoices.isNotEmpty()) {
            Log.w(TAG, "⚠️ لم يتم العثور على أصوات مناسبة، استخدام الأصوات المتاحة")
            results.addAll(allVoices.take(4))
        }
        
        return results.distinctBy { it.name }
    }

    private fun getEngineName(voice: Voice): String {
        return try {
            voice.javaClass.getDeclaredField("mEngine").let { field ->
                field.isAccessible = true
                field.get(voice) as? String ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun onPlayClicked(card: DialogueCard) {
        if (!isTtsInitialized) {
            Toast.makeText(this, "محرك الصوت ليس جاهزاً بعد", Toast.LENGTH_SHORT).show()
            return
        }

        if (card.isPlaying) {
            tts?.stop()
            viewModel.resetAllPlayingStates()
        } else {
            viewModel.resetAllPlayingStates()
            viewModel.setCardPlayingState(card.id, true)

            tts?.let { engine ->
                try {
                    card.selectedVoice?.let { voice ->
                        val setVoiceResult = engine.setVoice(voice)
                        if (setVoiceResult != TextToSpeech.SUCCESS) {
                            Log.w(TAG, "فشل تعيين الصوت: ${voice.name}")
                        } else {
                            Log.d(TAG, "تم تعيين الصوت: ${voice.name}")
                        }
                    }
                    
                    engine.setSpeechRate(card.speed)
                    engine.setPitch(card.pitch)
                    
                    Log.d(TAG, "التشغيل بالإعدادات - الصوت: ${card.selectedVoice?.name ?: "افتراضي"}, السرعة: ${card.speed}, حدة الصوت: ${card.pitch}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "خطأ في تطبيق إعدادات TTS", e)
                }
                
                val speakResult = engine.speak(card.text, TextToSpeech.QUEUE_FLUSH, null, card.id)
                if (speakResult != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "فشل تشغيل النص، النتيجة: $speakResult")
                    viewModel.resetAllPlayingStates()
                }
            }
        }
    }

    override fun onVoiceSelected(cardId: String, voice: Voice) {
        viewModel.updateCardVoice(cardId, voice)
        Log.d(TAG, "تم اختيار الصوت: ${voice.name} للبطاقة: $cardId")
    }

    override fun onSpeedChanged(cardId: String, speed: Float) {
        viewModel.updateCardSpeed(cardId, speed)
    }

    override fun onPitchChanged(cardId: String, pitch: Float) {
        viewModel.updateCardPitch(cardId, pitch)
    }

    override fun onTextChanged(cardId: String, newText: String) {
        viewModel.updateCardText(cardId, newText)
    }

    override fun onTimingChanged(cardId: String, startTime: Long, endTime: Long) {
        viewModel.updateCardTiming(cardId, startTime, endTime)
    }

    override fun onPreviewClicked(card: DialogueCard) {
        if (!isTtsInitialized) {
            Toast.makeText(this, "محرك الصوت ليس جاهزاً بعد", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.let { engine ->
            try {
                card.selectedVoice?.let { voice ->
                    engine.setVoice(voice)
                }
                
                val requiredSpeed = calculateRequiredSpeed(card.actualDuration, card.getDuration(), card.speed)
                engine.setSpeechRate(requiredSpeed)
                engine.setPitch(card.pitch)
                
                Log.d(TAG, "المعاينة بالسرعة: $requiredSpeed")
                
                engine.speak(card.text, TextToSpeech.QUEUE_FLUSH, null, "preview_${card.id}")
                
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في المعاينة", e)
            }
        }
    }

    override fun onCardDeleted(card: DialogueCard) {
        // يتم التعامل مع الحذف في onSwiped
    }

    override fun onAutoSyncClicked(card: DialogueCard) {
        if (card.actualDuration == 0L) {
            Toast.makeText(this, "يجب تشغيل المقطع أولاً لحساب المدة الفعلية", Toast.LENGTH_SHORT).show()
            return
        }

        showSyncStrategyDialog(card)
    }
    
    private fun showSyncStrategyDialog(card: DialogueCard) {
        val strategies = arrayOf(
            "حساب تلقائي للسرعة",
            "إدخال يدوي للسرعة", 
            "تقطيع الصوت",
            "تعديل التوقيتات"
        )
        
        AlertDialog.Builder(this)
            .setTitle("اختر طريقة المزامنة")
            .setItems(strategies) { dialog, which ->
                when (which) {
                    0 -> applyAutoSync(card)
                    1 -> showManualSpeedDialog(card)
                    2 -> applyTrimSync(card)
                    3 -> applyTimingAdjustment(card)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun applyAutoSync(card: DialogueCard) {
        val expectedDuration = card.getDuration()
        val actualDuration = card.actualDuration
        val requiredSpeed = calculateRequiredSpeed(actualDuration, expectedDuration, card.speed)
        
        SyncPreferences.savePreferredSpeed(this, requiredSpeed)
        
        val message = """
            المدة الفعلية: ${formatMsToTime(actualDuration)}
            المدة المطلوبة: ${formatMsToTime(expectedDuration)}
            السرعة الجديدة: ${String.format("%.2f", requiredSpeed)}x
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("المزامنة التلقائية")
            .setMessage(message)
            .setPositiveButton("تطبيق") { _, _ ->
                viewModel.applyAutoSync(card.id, requiredSpeed)
                Toast.makeText(this, "تم تطبيق المزامنة بنجاح", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun showManualSpeedDialog(card: DialogueCard) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(String.format("%.2f", card.speed))
        
        AlertDialog.Builder(this)
            .setTitle("أدخل السرعة المطلوبة (0.5 - 2.5)")
            .setView(input)
            .setPositiveButton("تطبيق") { _, _ ->
                val speed = input.text.toString().toFloatOrNull() ?: card.speed
                val clampedSpeed = speed.coerceIn(0.5f, 2.5f)
                viewModel.applyAutoSync(card.id, clampedSpeed)
                Toast.makeText(this, "تم تطبيق السرعة اليدوية", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun applyTrimSync(card: DialogueCard) {
        Toast.makeText(this, "ميزة تقطيع الصوت قيد التطوير", Toast.LENGTH_SHORT).show()
    }
    
    private fun applyTimingAdjustment(card: DialogueCard) {
        Toast.makeText(this, "ميزة تعديل التوقيتات قيد التطوير", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        dialogueAdapter = DialogueAdapter(this)
        binding.recyclerViewDialogues.apply {
            adapter = dialogueAdapter
            layoutManager = LinearLayoutManager(this@DubbingStudioActivity)
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val cardToDelete = dialogueAdapter.currentList[position]
                    viewModel.removeCard(cardToDelete)
                    
                    onCardDeleted(cardToDelete)

                    Snackbar.make(binding.root, "تم حذف البطاقة", Snackbar.LENGTH_LONG)
                        .setAction("تراجع") {
                            viewModel.addCardAtIndex(position, cardToDelete)
                        }
                        .show()
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewDialogues)
    }

    private fun addNewDialogueCard() {
        val cards = viewModel.dialogueCards.value
        val lastCard = cards?.lastOrNull()
        val startTime = lastCard?.endTimeMs ?: 0L
        val endTime = startTime + 5000L
        
        val newCard = DialogueCard(
            id = System.currentTimeMillis().toString(), 
            text = "نص جديد", 
            selectedVoice = null,
            startTimeMs = startTime,
            endTimeMs = endTime,
            speed = 1.0f,
            pitch = 1.0f,
            isPlaying = false,
            needsSync = false,
            actualDuration = 0L
        )
        viewModel.addNewCard(newCard)
    }

    private fun startExportServiceWithFallback() {
        val cardsToExport = viewModel.dialogueCards.value ?: emptyList()
        if (cardsToExport.isEmpty()) {
            Toast.makeText(this, "لا يوجد مقاطع لتصديرها", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.setExportingState(true)
        
        try {
            val serviceIntent = Intent(this, AudioExportService::class.java).apply {
                putParcelableArrayListExtra(AudioExportService.EXTRA_DIALOGUE_CARDS, ArrayList(cardsToExport))
                putExtra("force_export", true)
                putExtra("selected_language_code", selectedLanguageCode)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, "جاري التصدير في الخلفية...", Toast.LENGTH_LONG).show()
            AppLogger.logExportProgress("Starting export service", 0, cardsToExport.size, "With force_export=true")
            
        } catch (e: Exception) {
            AppLogger.error("Failed to start export service", e)
            viewModel.setExportingState(false)
            Toast.makeText(this, "فشل بدء خدمة التصدير: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatMsToTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        val remainingMs = ms % 1000
        return String.format("%02d:%02d.%03d", minutes, remainingSeconds, remainingMs)
    }

    private fun calculateRequiredSpeed(actualDuration: Long, expectedDuration: Long, currentSpeed: Float): Float {
        if (expectedDuration <= 0 || actualDuration <= 0) return currentSpeed
        val requiredSpeed = (actualDuration.toFloat() / expectedDuration.toFloat()) * currentSpeed
        return requiredSpeed.coerceIn(0.5f, 2.5f)
    }

    private fun setupSmartSyncButtons() {
    binding.btn_smart_sync.setOnClickListener {  // ✅ تصحيح: btn_smart_sync بدلاً من btnSmartSync
        showSmartSyncOptions()
    }
    
    binding.btn_add_card.setOnClickListener {
        addNewDialogueCard()
    }
    
    binding.btn_export_final.setOnClickListener { 
        startExportServiceWithFallback() 
    }
}

    private fun showSmartSyncOptions() {
        val options = arrayOf(
            "🔄 مزامنة جميع البطاقات",
            "🎯 مزامنة البطاقات التي تحتاج تعديل",
            "📊 عرض تقرير الجودة",
            "⚡ مزامنة سريعة (تقديرية)"
        )
        
        AlertDialog.Builder(this)
            .setTitle("اختر نوع المزامنة الذكية")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> startSmartSyncAllCards()
                    1 -> smartSyncNeededCards()
                    2 -> showSyncQualityReport()
                    3 -> quickSyncAllCards()
                }
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun smartSyncNeededCards() {
        val allCards = viewModel.dialogueCards.value ?: emptyList()
        val neededCards = allCards.filter { it.needsSync }
        
        if (neededCards.isEmpty()) {
            Toast.makeText(this, "🎉 جميع البطاقات متزامنة بالفعل!", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "جاري مزامنة ${neededCards.size} بطاقة تحتاج تعديل...", Toast.LENGTH_LONG).show()
        startSmartSyncAllCards()
    }

    private fun quickSyncAllCards() {
        val cards = viewModel.dialogueCards.value ?: emptyList()
        if (cards.isEmpty()) {
            Toast.makeText(this, "لا توجد بطاقات للمزامنة", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.setExportingState(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncedCards = SmartSyncManager.quickEstimateSync(cards)
                
                runOnUiThread {
                    viewModel.updateCards(syncedCards)
                    viewModel.setExportingState(false)
                    
                    val syncedCount = syncedCards.count { !it.needsSync }
                    Toast.makeText(
                        this@DubbingStudioActivity, 
                        "تم المزامنة السريعة لـ ${cards.size} بطاقة ($syncedCount متزامنة)", 
                        Toast.LENGTH_LONG
                    ).show()
                    
                    showQuickSyncReport(syncedCards)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    viewModel.setExportingState(false)
                    Toast.makeText(
                        this@DubbingStudioActivity, 
                        "فشل المزامنة السريعة: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startSmartSyncAllCards() {
        val cards = viewModel.dialogueCards.value ?: emptyList()
        if (cards.isEmpty()) {
            Toast.makeText(this, "لا توجد بطاقات للمزامنة", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.setExportingState(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncedCards = SmartSyncManager.smartSyncAllCards(cards, tts!!) { progress, total ->
                    runOnUiThread {
                        val percent = (progress * 100) / total
                        binding.progressBar.progress = percent
                    }
                }
                
                runOnUiThread {
                    viewModel.updateCards(syncedCards)
                    viewModel.setExportingState(false)
                    
                    val perfectlySynced = syncedCards.count { !it.needsSync }
                    Toast.makeText(
                        this@DubbingStudioActivity, 
                        "تم المزامنة الذكية لـ ${cards.size} بطاقة ($perfectlySynced متزامنة)", 
                        Toast.LENGTH_LONG
                    ).show()
                    
                    showSyncQualityReport()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    viewModel.setExportingState(false)
                    Toast.makeText(
                        this@DubbingStudioActivity, 
                        "فشل المزامنة الذكية: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showSyncQualityReport() {
        val cards = viewModel.dialogueCards.value ?: emptyList()
        val report = SmartSyncManager.getSyncQualityReport(cards)
        
        AlertDialog.Builder(this)
            .setTitle("تقرير جودة المزامنة")
            .setMessage(report)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun showQuickSyncReport(cards: List<DialogueCard>) {
        val syncedCount = cards.count { !it.needsSync }
        val totalCount = cards.size
        
        AlertDialog.Builder(this)
            .setTitle("نتيجة المزامنة السريعة")
            .setMessage("تمت مزامنة $syncedCount من أصل $totalCount بطاقة")
            .setPositiveButton("حسناً", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        unregisterExportReceiver()
        Log.d(TAG, "تم تنظيف TTS والموارد في onDestroy")
    }
}