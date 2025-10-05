package com.nash.dubbingstudio.ui.studio.adapter

import android.speech.tts.Voice
import com.nash.dubbingstudio.model.DialogueCard

interface OnDialogueCardInteraction {
    fun onPlayClicked(card: DialogueCard)
    fun onVoiceSelected(cardId: String, voice: Voice)
    fun onSpeedChanged(cardId: String, speed: Float)
    fun onPitchChanged(cardId: String, pitch: Float)
    fun onTextChanged(cardId: String, newText: String)
    fun onCardDeleted(card: DialogueCard)
    fun onAutoSyncClicked(card: DialogueCard)
    fun onTimingChanged(cardId: String, startTime: Long, endTime: Long)
    fun onPreviewClicked(card: DialogueCard)
}