package com.example.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class VoiceTypingLanguage(
    val localeTag: String,
    val shortLabel: String,
    val displayName: String
) {
    ENGLISH("en-US", "EN", "English"),
    BANGLA("bn-BD", "বাং", "বাংলা (Bangla)");

    companion object {
        fun fromTag(tag: String): VoiceTypingLanguage =
            entries.firstOrNull { it.localeTag == tag } ?: ENGLISH
    }
}

data class VoiceTypingState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val language: VoiceTypingLanguage = VoiceTypingLanguage.ENGLISH,
    val rmsDb: Float = 0f,
    val errorMessage: String? = null
)

/**
 * Google Speech Recognition engine restricted cleanly to English (en-US) and Bangla (bn-BD)
 * with partial streaming, confidence-ranked hypothesis selection, and punctuation post-processing.
 */
class VoiceTypingEngine(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onStatusChanged: (listening: Boolean, error: String?) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var activeLanguage: VoiceTypingLanguage = VoiceTypingLanguage.ENGLISH
    private var isCurrentlyListening = false

    fun createGoogleRecognitionIntent(language: VoiceTypingLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${language.displayName}...")
        }
    }

    fun startListening(language: VoiceTypingLanguage): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatusChanged(false, null)
            return false
        }

        stopListeningInternal()
        activeLanguage = language

        val googleService = findGoogleRecognitionComponent(context)
        val instance = if (googleService != null) {
            SpeechRecognizer.createSpeechRecognizer(context, googleService)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isCurrentlyListening = true
                onStatusChanged(true, null)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged(rmsdB.coerceIn(0f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                onRmsChanged(0f)
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                onRmsChanged(0f)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to speak again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic to dictate."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network needed for best Google voice accuracy."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer busy, try again."
                    else -> null
                }
                onStatusChanged(false, msg)
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                onRmsChanged(0f)
                val best = selectBestHypothesis(results)
                if (best.isNotBlank()) {
                    onFinalResult(normalizeVoiceText(best, activeLanguage))
                }
                onStatusChanged(false, null)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topPartial = matches?.firstOrNull()?.trim().orEmpty()
                if (topPartial.isNotEmpty()) {
                    onPartialResult(normalizeVoiceText(topPartial, activeLanguage, isPartial = true))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer = instance
        isCurrentlyListening = true
        onStatusChanged(true, null)
        return try {
            instance.startListening(createGoogleRecognitionIntent(language))
            true
        } catch (_: Exception) {
            isCurrentlyListening = false
            onStatusChanged(false, null)
            false
        }
    }

    fun stopListening() {
        if (isCurrentlyListening) {
            runCatching { recognizer?.stopListening() }
            isCurrentlyListening = false
            onStatusChanged(false, null)
        }
    }

    fun destroy() {
        stopListeningInternal()
    }

    private fun stopListeningInternal() {
        isCurrentlyListening = false
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    companion object {
        private val GOOGLE_PACKAGES = listOf(
            "com.google.android.as",
            "com.google.android.googlequicksearchbox",
            "com.google.android.tts"
        )

        fun findGoogleRecognitionComponent(context: Context): ComponentName? {
            return runCatching {
                val pm = context.packageManager
                val intent = Intent("android.speech.RecognitionService")
                val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentServices(intent, 0)
                }
                val match = GOOGLE_PACKAGES.firstNotNullOfOrNull { pkg ->
                    services.firstOrNull { it.serviceInfo?.packageName == pkg }
                } ?: services.firstOrNull()
                match?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
            }.getOrNull()
        }

        fun selectBestHypothesis(bundle: Bundle?): String {
            if (bundle == null) return ""
            val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (matches.isEmpty()) return ""
            val scores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            if (scores == null || scores.size != matches.size) {
                return matches.first().trim()
            }
            var bestIdx = 0
            var bestScore = scores[0]
            for (i in 1 until scores.size) {
                if (scores[i] > bestScore) {
                    bestScore = scores[i]
                    bestIdx = i
                }
            }
            return matches[bestIdx].trim()
        }

        fun normalizeVoiceText(
            raw: String,
            language: VoiceTypingLanguage,
            isPartial: Boolean = false
        ): String {
            var text = raw.trim().replace(Regex("\\s+"), " ")
            if (text.isEmpty()) return ""

            if (language == VoiceTypingLanguage.ENGLISH) {
                text = text
                    .replace(Regex("\\b(full stop|period)\\b", RegexOption.IGNORE_CASE), ".")
                    .replace(Regex("\\bcomma\\b", RegexOption.IGNORE_CASE), ",")
                    .replace(Regex("\\bquestion mark\\b", RegexOption.IGNORE_CASE), "?")
                    .replace(Regex("\\bexclamation mark\\b", RegexOption.IGNORE_CASE), "!")
                    .replace(Regex("\\bnew line\\b", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("\\s+([.,?!])"), "$1")
                    .replace(Regex(" *\\n *"), "\n")
                if (!isPartial && text.isNotEmpty() && text[0].isLowerCase()) {
                    text = text[0].uppercaseChar() + text.substring(1)
                }
            } else {
                // Bangla spoken punctuation cleanup
                text = text
                    .replace(Regex("\\s*(দাঁড়ি|দাড়ি)\\s*"), "। ")
                    .replace(Regex("\\s*কমা\\s*"), ", ")
                    .replace(Regex("\\s*প্রশ্নবোধক\\s*"), "? ")
                    .replace(Regex("\\s*নতুন লাইন\\s*"), "\n")
                    .replace(Regex(" +"), " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .trim()
            }
            return text
        }

        fun formatInsertionWithSpacing(
            existingText: String,
            insertStart: Int,
            dictated: String
        ): String {
            if (dictated.isEmpty()) return ""
            val safeStart = insertStart.coerceIn(0, existingText.length)
            val charBefore = if (safeStart > 0) existingText[safeStart - 1] else '\n'
            val startsWithPunctuation = dictated[0] in charArrayOf('.', ',', '?', '!', '।', '\n', ';', ':')
            val needsLeadingSpace = safeStart > 0 &&
                !charBefore.isWhitespace() &&
                charBefore !in charArrayOf('(', '[', '"', '“') &&
                !startsWithPunctuation
            return if (needsLeadingSpace) " $dictated" else dictated
        }
    }
}
