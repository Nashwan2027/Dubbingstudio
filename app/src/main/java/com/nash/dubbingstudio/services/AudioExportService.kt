package com.nash.dubbingstudio.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nash.dubbingstudio.model.DialogueCard
import com.nash.dubbingstudio.utils.AppLogger
import com.nash.dubbingstudio.utils.AudioExporter
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import com.nash.dubbingstudio.utils.EnhancedAudioExporter

class AudioExportService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "AudioExportChannel"
        const val NOTIFICATION_ID = 101
        const val EXTRA_DIALOGUE_CARDS = "EXTRA_DIALOGUE_CARDS"
        const val ACTION_EXPORT_COMPLETE = "AUDIO_EXPORT_COMPLETE"
        const val ACTION_EXPORT_PROGRESS = "AUDIO_EXPORT_PROGRESS"
        const val ACTION_EXPORT_ERROR = "AUDIO_EXPORT_ERROR"
    }
    
    private var tts: TextToSpeech? = null
    private var cardsToExport: ArrayList<DialogueCard>? = null
    private var isTtsInitialized = false
    private lateinit var notificationManager: NotificationManager
    private var exportJob: Job? = null
    private var totalCards = 0
    private var processedCards = 0
    private var isForegroundStarted = false

    // ✅ المتغيرات الجديدة - أضيفت بشكل صحيح
    private var serviceSelectedLanguageCode: String = "ar"
    private var forceExport: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.logEnter("AudioExportService", "onStartCommand")
        
        try {
            startForegroundServiceImmediately()

            if (intent == null || !intent.hasExtra(EXTRA_DIALOGUE_CARDS)) {
                AppLogger.error("Invalid intent or no cards provided")
                stopForegroundService()
                return START_NOT_STICKY
            }

            extractCardsFromIntent(intent)
            
            // ✅ قراءة الإعدادات الجديدة بشكل صحيح
            forceExport = intent.getBooleanExtra("force_export", false)
            serviceSelectedLanguageCode = intent.getStringExtra("selected_language_code") ?: "ar"
            
            AppLogger.logExport("Export settings", "Language: $serviceSelectedLanguageCode, Force: $forceExport")
            
            initializeTts()
            
        } catch (e: Exception) {
            AppLogger.error("Failed in onStartCommand", e)
            sendErrorBroadcast("Service startup failed: ${e.message}")
            stopForegroundService()
        }
        
        AppLogger.logExit("AudioExportService", "onStartCommand")
        return START_NOT_STICKY
    }

    private fun startForegroundServiceImmediately() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        val notification = createNotification("جاري تهيئة خدمة التصدير...", 0)
        startForeground(NOTIFICATION_ID, notification)
        isForegroundStarted = true
        
        AppLogger.logExport("Foreground service", "Started immediately")
    }

    private fun stopForegroundService() {
        try {
            if (isForegroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForegroundStarted = false
                AppLogger.logExport("Foreground service", "Stopped safely")
            }
            stopSelf()
        } catch (e: Exception) {
            AppLogger.error("Error stopping foreground service", e)
            stopSelf()
        }
    }
    
    private fun extractCardsFromIntent(intent: Intent) {
        cardsToExport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_DIALOGUE_CARDS, DialogueCard::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_DIALOGUE_CARDS)
        }
        
        totalCards = cardsToExport?.size ?: 0
        AppLogger.logExport("Cards extracted", "Found $totalCards cards")
    }
    
    private fun initializeTts() {
        updateNotification("جاري تهيئة محرك الصوت...", 10)
        
        try {
            tts = TextToSpeech(this, this)
            AppLogger.logTts("Initialization started")
        } catch (e: Exception) {
            AppLogger.logTts("Initialization failed", false, e.message ?: "Unknown error")
            sendErrorBroadcast("TTS initialization failed: ${e.message}")
            stopForegroundService()
        }
    }

    // ✅ دالة onInit المصححة بالكامل
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            AppLogger.logTts("Initialization", true, "TTS engine ready")
            
            // ✅ محاولة إعداد اللغة مع استراتيجية متسامحة
            val languageSupported = setupTtsLanguageService()
            
            if (!languageSupported && !forceExport) {
                // ❌ إذا فشلت اللغة وليس لدينا force_export، نتوقف
                AppLogger.logTts("Language setup", false, "Language not supported and force_export=false")
                sendErrorBroadcast("اللغة العربية غير مدعومة في محرك الصوت")
                stopForegroundService()
                return
            } else if (!languageSupported) {
                // ✅ إذا فشلت اللغة ولكن لدينا force_export، نستمر
                AppLogger.logTts("Language setup", false, "Continuing with default language (force_export=true)")
                val defaultLanguage = tts?.defaultVoice?.locale?.displayLanguage ?: "Unknown"
                AppLogger.logTts("Default language", true, "Using: $defaultLanguage")
                
                updateNotification("جاري التصدير باللغة الافتراضية للمحرك...", 30)
                showToast("⚠️ اللغة العربية غير مدعومة، جاري استخدام اللغة الافتراضية")
            }
            
            // ✅ المهم: نستمر في التصدير
            startExportProcess()
            
        } else {
            AppLogger.logTts("Initialization", false, "Status: $status")
            sendErrorBroadcast("فشل تهيئة محرك الصوت: $status")
            stopForegroundService()
        }
    }
    
    // ✅ دالة setupTtsLanguageService المصححة (تم تغيير الاسم لتجنب التعارض)
    private fun setupTtsLanguageService(): Boolean {
        return try {
            val result = tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_MISSING_DATA
            
            when (result) {
                TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                    AppLogger.logTts("Language setup", true, "Arabic language set successfully")
                    true
                }
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    AppLogger.logTts("Language setup", true, "Arabic variant available")
                    true
                }
                TextToSpeech.LANG_MISSING_DATA -> {
                    AppLogger.logTts("Language setup", false, "Arabic language data missing")
                    false
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    AppLogger.logTts("Language setup", false, "Arabic language not supported")
                    false
                }
                else -> {
                    AppLogger.logTts("Language setup", false, "Unknown result: $result")
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.logTts("Language setup", false, e.message ?: "Unknown error")
            false
        }
    }

    private fun startExportProcess() {
        if (!isTtsInitialized || cardsToExport.isNullOrEmpty()) {
            AppLogger.error("Prerequisites not met for export")
            sendErrorBroadcast("TTS not ready or no cards available")
            stopForegroundService()
            return
        }
        
        updateNotification("جاري بدء التصدير...", 20)
        AppLogger.logExportProgress("Starting export process", 0, totalCards, "With $totalCards cards")

        exportJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                executeExport()
            } catch (e: Exception) {
                handleExportError(e)
            } finally {
                cleanupResources()
            }
        }
    }
    
    private suspend fun executeExport() {
    val enhancedExporter = EnhancedAudioExporter(applicationContext, tts)
    val validCards = cardsToExport!!.filter { card -> 
        card.text.isNotBlank() && card.endTimeMs > card.startTimeMs 
    }
    
    if (validCards.isEmpty()) {
        AppLogger.error("No valid cards found after filtering")
        sendErrorBroadcast("No valid cards to export")
        return
    }
    
    AppLogger.logExportProgress("Starting smart export", 0, validCards.size, 
        "Enhanced exporter with ${validCards.size} cards")

    // ✅ التصحيح: استخدام callback متوافق
    val exportResult = enhancedExporter.exportWithSmartSync(validCards) { progress, total ->
        processedCards = progress
        
        // ✅ حساب النسبة المئوية
        val percent = when {
            total == 0 -> 0
            else -> 30 + (progress * 60 / total)
        }
        
        // ✅ إنشاء رسالة حالة ذكية
        val statusMessage = when {
            progress == 0 -> "جاري البدء..."
            progress == total -> "جاري الانتهاء..."
            progress >= total * 0.8 -> "جاري المرحلة النهائية..."
            progress >= total * 0.5 -> "جاري منتصف العملية..."
            else -> "جاري المعالجة..."
        }
        
        updateNotification("$statusMessage ($progress/$total)", percent)
        sendProgressBroadcast(progress, total)
        
        AppLogger.logExportProgress("Export progress", progress, total, statusMessage)
    }
    
    if (exportResult != null) {
        handleExportSuccess(exportResult)
    } else {
        handleExportFailure()
    }
}
    
    private fun handleExportSuccess(outputFile: File) {
        AppLogger.logExport("Export completed", "File: ${outputFile.name}")
        
        if (outputFile.exists() && outputFile.length() > 0) {
            val fileSizeKB = outputFile.length() / 1024
            AppLogger.logExport("File verified", "Size: ${fileSizeKB}KB, Path: ${outputFile.absolutePath}")
            updateNotification("تم التصدير بنجاح! (${fileSizeKB}KB)", 100)
            showToast("✅ تم التصدير بنجاح!\nالحجم: ${fileSizeKB}KB")
        } else {
            AppLogger.error("File doesn't exist or is empty")
            updateNotification("فشل التصدير - الملف غير موجود", 0)
            showToast("❌ فشل التصدير: الملف غير موجود")
        }
        
        sendSuccessBroadcast(outputFile.absolutePath)
        
        Handler(Looper.getMainLooper()).postDelayed({
            stopForegroundService()
        }, 2000)
    }
    
    private fun handleExportFailure() {
        AppLogger.logExport("Export failed", "Output file is invalid")
        updateNotification("فشل التصدير", 0)
        sendErrorBroadcast("Output file is invalid")
        showToast("❌ فشل التصدير")
        stopForegroundService()
    }
    
    private fun handleExportError(exception: Exception) {
        AppLogger.error("Export error occurred", exception)
        updateNotification("حدث خطأ أثناء التصدير", 0)
        sendErrorBroadcast("Export error: ${exception.message}")
        showToast("❌ حدث خطأ: ${exception.message}")
        stopForegroundService()
    }
    
    private fun sendSuccessBroadcast(filePath: String) {
        val intent = Intent(ACTION_EXPORT_COMPLETE).apply {
            putExtra("file_path", filePath)
            putExtra("success", true)
            putExtra("processed_cards", processedCards)
            putExtra("total_cards", totalCards)
        }
        sendBroadcast(intent)
    }
    
    private fun sendProgressBroadcast(progress: Int, total: Int) {
        val intent = Intent(ACTION_EXPORT_PROGRESS).apply {
            putExtra("progress", progress)
            putExtra("total", total)
            putExtra("percentage", (progress * 100 / total))
        }
        sendBroadcast(intent)
    }
    
    private fun sendErrorBroadcast(errorMessage: String) {
        val intent = Intent(ACTION_EXPORT_ERROR).apply {
            putExtra("error_message", errorMessage)
            putExtra("success", false)
        }
        sendBroadcast(intent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "قناة خدمة تصدير الصوت",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة للإشعارات أثناء عملية تصدير الصوت"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updateNotification(contentText: String, progress: Int) {
        val notification = createNotification(contentText, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(contentText: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("استوديو الدبلجة - تصدير الصوت")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }
    
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun cleanupResources() {
        try {
            tts?.shutdown()
            exportJob?.cancel()
            AppLogger.debug("Service resources cleaned up")
        } catch (e: Exception) {
            AppLogger.error("Error during cleanup", e)
        }
    }

    override fun onDestroy() {
        AppLogger.logLifecycle("AudioExportService", "onDestroy")
        cleanupResources()
        super.onDestroy()
    }
}