package com.nash.dubbingstudio.utils

import android.content.Context
import android.media.*
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.coroutines.resume

// ✅ تعريف type alias خارج الكلاس

class AudioExporter(private val context: Context, private val tts: TextToSpeech?) {

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val BITS_PER_SAMPLE = 16
        private const val NUM_CHANNELS = 1
        private const val BYTE_RATE = SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)
    }
    

    suspend fun exportWithSrtSync(
        cards: List<com.nash.dubbingstudio.model.DialogueCard>,
        progressCallback: ProgressCallback? = null
    ): File? = withContext(Dispatchers.IO) {
        AppLogger.logEnter("AudioExporter", "exportWithSrtSync")
        
        try {
            // ✅ التحقق الأساسي
            if (tts == null) {
                AppLogger.error("TTS engine is null")
                return@withContext null
            }

            if (cards.isEmpty()) {
                AppLogger.error("No cards to export")
                return@withContext null
            }

            // ✅ تصفية البطاقات الصالحة
            val validCards = cards.filter { card -> 
                card.text.isNotBlank() && card.endTimeMs > card.startTimeMs 
            }
            
            if (validCards.isEmpty()) {
                AppLogger.error("No valid cards after filtering")
                return@withContext null
            }

            AppLogger.logAudio("Processing cards", 0, "${validCards.size} valid cards")

            // ✅ معالجة البطاقات
            val tempFiles = mutableListOf<File>()
            var currentPosition = 0L

            for ((index, card) in validCards.sortedBy { it.startTimeMs }.withIndex()) {
                // ✅ تحديث التقدم
                progressCallback?.invoke(index + 1, validCards.size)
                AppLogger.logExportProgress("Processing card", index + 1, validCards.size, "${card.text.take(30)}...")

                // ✅ إضافة صمت إذا لزم الأمر
                val silenceBefore = card.startTimeMs - currentPosition
                if (silenceBefore > 0) {
                    createSilenceWav(silenceBefore, "silence_before_$index")?.let {
                        tempFiles.add(it)
                        currentPosition += silenceBefore
                    }
                }

                // ✅ إنشاء ملف الكلام
                val speechFile = createSpeechWav(card, index)
                if (speechFile != null && speechFile.exists() && speechFile.length() > 44) {
                    tempFiles.add(speechFile)
                    val speechDuration = getWavDuration(speechFile)
                    currentPosition += speechDuration
                    card.actualDuration = speechDuration
                } else {
                    AppLogger.warn("Failed to create speech file, adding silence")
                    val expectedDuration = card.getDuration()
                    createSilenceWav(expectedDuration, "failed_silence_$index")?.let {
                        tempFiles.add(it)
                        currentPosition += expectedDuration
                    }
                }
            }

            AppLogger.logAudio("All cards processed", currentPosition, "Total duration: ${currentPosition}ms")

            // ✅ دمج الملفات
            val finalFile = mergeWavFiles(tempFiles)
            
            // ✅ إظهار موقع الملف للمستخدم
            finalFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    showFileLocationToast(file)
                }
            }
            
            // ✅ تنظيف الملفات المؤقتة
            tempFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    AppLogger.warn("Failed to delete temp file: ${file.name}")
                }
            }

            return@withContext finalFile

        } catch (e: Exception) {
            AppLogger.error("Export process failed", e)
            return@withContext null
        } finally {
            AppLogger.logExit("AudioExporter", "exportWithSrtSync")
        }
    }

    private suspend fun createSpeechWav(card: com.nash.dubbingstudio.model.DialogueCard, index: Int): File? {
        return suspendCancellableCoroutine { continuation ->
            if (tts == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val tempFile = File.createTempFile("tts_${index}_", ".wav", context.cacheDir)
            val utteranceId = "tts_$index"

            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    AppLogger.logTts("Synthesis started", true, "Utterance: $utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    AppLogger.logTts("Synthesis error", false, "Utterance: $utteranceId")
                    if (utteranceId == utteranceId && continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == utteranceId && continuation.isActive) {
                        if (tempFile.exists() && tempFile.length() > 44) {
                            AppLogger.logTts("Synthesis completed", true, "${tempFile.length()} bytes")
                            continuation.resume(tempFile)
                        } else {
                            AppLogger.logTts("Synthesis completed", false, "File is invalid")
                            continuation.resume(null)
                        }
                    }
                }
            }

            tts.setOnUtteranceProgressListener(listener)

            try {
                // ✅ تطبيق إعدادات الصوت
                card.selectedVoice?.let { voice ->
                    tts.voice = voice
                }
                tts.setSpeechRate(card.speed)
                tts.setPitch(card.pitch)

                // ✅ الإصلاح: استخدم null بدلاً من HashMap
                val result = tts.synthesizeToFile(card.text, null, tempFile, utteranceId)
                
                if (result != TextToSpeech.SUCCESS) {
                    AppLogger.logTts("synthesizeToFile", false, "Result: $result")
                    if (continuation.isActive) continuation.resume(null)
                }

            } catch (e: Exception) {
                AppLogger.error("TTS synthesis error", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun getWavDuration(wavFile: File): Long {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(wavFile.absolutePath)
            val format = extractor.getTrackFormat(0)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            durationUs / 1000
        } catch (e: Exception) {
            // ✅ طريقة احتياطية
            val fileSize = wavFile.length()
            if (fileSize > 44) {
                val dataSize = fileSize - 44
                (dataSize * 1000L) / BYTE_RATE
            } else {
                0L
            }
        } finally {
            extractor?.release()
        }
    }

    private fun createSilenceWav(durationMs: Long, filename: String): File? {
        if (durationMs <= 0) return null
        
        return try {
            val file = File.createTempFile(filename, ".wav", context.cacheDir)
            val numSamples = (durationMs * SAMPLE_RATE / 1000).toInt()
            val dataSize = numSamples * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)
            
            val header = createWavHeader(dataSize.toLong())
            
            FileOutputStream(file).use { outputStream ->
                outputStream.write(header)
                val silenceData = ByteArray(dataSize)
                outputStream.write(silenceData)
            }
            
            file
        } catch (e: Exception) {
            AppLogger.error("Silence file creation failed", e)
            null
        }
    }

    private fun mergeWavFiles(files: List<File>): File? {
        if (files.isEmpty()) return null

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dubbingDir = File(musicDir, "Dubbing")
        if (!dubbingDir.exists()) {
            dubbingDir.mkdirs()
        }
        
        val outputFile = File(dubbingDir, "dubbed_audio_${System.currentTimeMillis()}.wav")
        
        return try {
            var totalAudioLen: Long = 0
            files.forEach { file ->
                if (file.exists() && file.length() > 44) {
                    totalAudioLen += (file.length() - 44)
                }
            }

            if (totalAudioLen == 0L) {
                AppLogger.error("No audio data to merge")
                return null
            }

            FileOutputStream(outputFile).use { out ->
                val header = createWavHeader(totalAudioLen)
                out.write(header)
                
                files.forEach { file ->
                    if (file.exists() && file.length() > 44) {
                        FileInputStream(file).use { fis ->
                            fis.skip(44L)
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
            }

            if (outputFile.exists() && outputFile.length() > 44) {
                AppLogger.logAudio("Merge completed", 0, outputFile.absolutePath)
                outputFile
            } else {
                AppLogger.error("Merged file is invalid")
                null
            }

        } catch (e: Exception) {
            AppLogger.error("File merging failed", e)
            null
        }
    }
    
    private fun createWavHeader(totalAudioLen: Long): ByteArray {
        val totalDataLen = totalAudioLen + 36
        val header = ByteArray(44)
        
        ByteBuffer.wrap(header).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(NUM_CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(BYTE_RATE)
            putShort((NUM_CHANNELS * BITS_PER_SAMPLE / 8).toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen.toInt())
        }
        
        return header
    }

    // ✅ دالة مساعدة لعرض موقع الملف
    private fun showFileLocationToast(file: File) {
        Handler(Looper.getMainLooper()).post {
            val fileSizeKB = file.length() / 1024
            val message = "✅ تم التصدير بنجاح\nالحجم: ${fileSizeKB}KB\nالمكان: ${file.parentFile?.name}"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.d("AudioExporter", "📁 الملف المصدر: ${file.absolutePath}")
        }
    }
}