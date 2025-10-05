package com.nash.dubbingstudio.utils

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.nash.dubbingstudio.model.DialogueCard
import java.util.*

object SrtParser {

    // ✅ الدالة الجديدة: لقراءة وتحليل ملف SRT من خلال Uri
    fun parse(contentResolver: ContentResolver, uri: Uri): List<DialogueCard> {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                parseSrtContent(content)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("SrtParser", "Error reading URI content: ${e.message}", e)
            emptyList()
        }
    }

    fun parseSrtContent(content: String): List<DialogueCard> {
        val cards = mutableListOf<DialogueCard>()
        
        val srtBlocks = content.trim().split(Regex("(\\r\\n|\\n){2,}"))
        Log.d("SrtParser", "Found ${srtBlocks.size} SRT blocks")

        try {
            for (blockIndex in srtBlocks.indices) {
                val blockText = srtBlocks[blockIndex]
                val lines = blockText.trim().split(Regex("(\\r\\n|\\n)"))

                if (lines.size >= 3) {
                    try {
                        // البحث عن سطر التوقيت (قد يكون في أي موضع)
                        var timeLine: String? = null
                        for (line in lines) {
                            if (line.contains("-->")) {
                                timeLine = line
                                break
                            }
                        }

                        if (timeLine != null) {
                            val times = timeLine.split("-->").map { it.trim() }
                            
                            if (times.size == 2) {
                                val startTime = parseTimeToMillis(times[0])
                                val endTime = parseTimeToMillis(times[1])

                                // التأكد من أن التوقيتات صحيحة
                                if (startTime >= 0 && endTime > startTime) {
                                    // استخراج النص (جميع الأسطر ما عدا سطر الرقم وسطر التوقيت)
                                    val textLines = mutableListOf<String>()
                                    var foundTimeLine = false
                                    
                                    for (line in lines) {
                                        if (line.contains("-->")) {
                                            foundTimeLine = true
                                            continue
                                        }
                                        if (foundTimeLine && line.isNotBlank()) {
                                            textLines.add(line)
                                        }
                                    }

                                    val text = textLines.joinToString(" ").trim()

                                    if (text.isNotBlank()) {
                                        val id = UUID.randomUUID().toString()
                                        val card = DialogueCard(
                                            id = id, 
                                            startTimeMs = startTime, 
                                            endTimeMs = endTime, 
                                            text = text
                                        )
                                        cards.add(card)
                                        Log.d("SrtParser", "✅ Added card $blockIndex: '$text' [$startTime-$endTime ms]")
                                    } else {
                                        Log.w("SrtParser", "❌ Empty text in block $blockIndex")
                                    }
                                } else {
                                    Log.w("SrtParser", "❌ Invalid times in block $blockIndex: $startTime-$endTime")
                                }
                            }
                        } else {
                            Log.w("SrtParser", "❌ No time line found in block $blockIndex")
                        }
                    } catch (e: Exception) {
                        Log.e("SrtParser", "❌ Error parsing block $blockIndex: ${e.message}")
                    }
                } else {
                    Log.w("SrtParser", "❌ Block $blockIndex has insufficient lines: ${lines.size}")
                }
            }
        } catch (e: Exception) {
            Log.e("SrtParser", "❌ Error parsing SRT content: ${e.message}")
        }
        
        Log.d("SrtParser", "🎯 Successfully parsed ${cards.size} cards from ${srtBlocks.size} blocks")
        return cards
    }

    private fun parseTimeToMillis(timeStr: String): Long {
        return try {
            var cleanTime = timeStr.trim()
            
            // معالجة كل من الفاصلة والنقطة
            cleanTime = cleanTime.replace(',', '.')
            
            // إزالة أي أحرف غير مرغوب فيها
            cleanTime = cleanTime.replace(Regex("[^0-9:.]"), "")
            
            val parts = cleanTime.split(':')
            
            if (parts.size >= 3) {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secondsPart = parts[2]
                
                val secondsAndMillis = secondsPart.split('.')
                val seconds = secondsAndMillis[0].toLongOrNull() ?: 0L
                
                val millis = if (secondsAndMillis.size > 1) {
                    val millisStr = secondsAndMillis[1].padEnd(3, '0').take(3)
                    millisStr.toLongOrNull() ?: 0L
                } else {
                    0L
                }
                
                val totalMillis = hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
                Log.d("SrtParser", "⏱️ Parsed '$timeStr' -> $totalMillis ms")
                totalMillis
            } else {
                Log.w("SrtParser", "⚠️ Invalid time format: '$timeStr'")
                0L
            }
        } catch (e: Exception) {
            Log.e("SrtParser", "❌ Error parsing time '$timeStr': ${e.message}")
            0L
        }
    }
}
