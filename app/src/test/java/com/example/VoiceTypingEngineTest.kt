package com.example

import android.os.Bundle
import android.speech.SpeechRecognizer
import com.example.voice.VoiceTypingEngine
import com.example.voice.VoiceTypingLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceTypingEngineTest {

    @Test
    fun `voice typing supports only English and Bangla`() {
        assertEquals(
            listOf(VoiceTypingLanguage.ENGLISH, VoiceTypingLanguage.BANGLA),
            VoiceTypingLanguage.entries
        )
        assertEquals("en-US", VoiceTypingLanguage.ENGLISH.localeTag)
        assertEquals("bn-BD", VoiceTypingLanguage.BANGLA.localeTag)
        assertEquals(VoiceTypingLanguage.ENGLISH, VoiceTypingLanguage.fromTag("fr-FR"))
    }

    @Test
    fun `english spoken punctuation and capitalization are normalized`() {
        val normalized = VoiceTypingEngine.normalizeVoiceText(
            raw = "hello world comma how are you question mark new line i am fine period",
            language = VoiceTypingLanguage.ENGLISH,
            isPartial = false
        )
        assertEquals("Hello world, how are you?\ni am fine.", normalized)
    }

    @Test
    fun `bangla spoken punctuation and line breaks are normalized`() {
        val normalized = VoiceTypingEngine.normalizeVoiceText(
            raw = "আমি বাংলায় লিখছি দাঁড়ি তুমি কেমন আছো প্রশ্নবোধক নতুন লাইন ভালো আছি কমা ধন্যবাদ",
            language = VoiceTypingLanguage.BANGLA,
            isPartial = false
        )
        assertEquals("আমি বাংলায় লিখছি। তুমি কেমন আছো?\nভালো আছি, ধন্যবাদ", normalized)
    }

    @Test
    fun `confidence scores select highest ranking hypothesis`() {
        val bundle = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("write a noot", "write a note", "right a note")
            )
            putFloatArray(
                SpeechRecognizer.CONFIDENCE_SCORES,
                floatArrayOf(0.42f, 0.94f, 0.61f)
            )
        }
        assertEquals("write a note", VoiceTypingEngine.selectBestHypothesis(bundle))
    }

    @Test
    fun `smart spacing inserts leading space only between words`() {
        assertEquals(
            " world",
            VoiceTypingEngine.formatInsertionWithSpacing("Hello", 5, "world")
        )
        assertEquals(
            ".",
            VoiceTypingEngine.formatInsertionWithSpacing("Hello", 5, ".")
        )
        assertEquals(
            "Hello",
            VoiceTypingEngine.formatInsertionWithSpacing("", 0, "Hello")
        )
        assertEquals(
            "আমি",
            VoiceTypingEngine.formatInsertionWithSpacing("নমস্কার ", 8, "আমি")
        )
    }
}
