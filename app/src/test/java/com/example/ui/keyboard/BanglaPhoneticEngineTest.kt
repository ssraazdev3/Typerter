package com.example.ui.keyboard

import com.example.ui.viewmodel.KeystrokeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaPhoneticEngineTest {

    @Test
    fun commonBanglaWordsTransliterateAccurately() {
        assertEquals("আমি", BanglaPhoneticEngine.transliterate("ami"))
        assertEquals("বাংলাদেশ", BanglaPhoneticEngine.transliterate("bangladesh"))
        assertEquals("কেমন", BanglaPhoneticEngine.transliterate("kemon"))
        assertEquals("ধন্যবাদ", BanglaPhoneticEngine.transliterate("dhonnobad"))
        assertEquals("ভালোবাসা", BanglaPhoneticEngine.transliterate("bhalobasha"))
    }

    @Test
    fun ruleBasedTransliterationHandlesInherentVowelConjunctsRephAndFolas() {
        assertEquals("কলম", BanglaPhoneticEngine.transliterate("kolom"))
        assertEquals("কথা", BanglaPhoneticEngine.transliterate("kotha"))
        assertEquals("বো", BanglaPhoneticEngine.transliterate("bO"))
        assertEquals("১২৩।", BanglaPhoneticEngine.transliterate("123."))
        assertEquals("কর্ম", BanglaPhoneticEngine.transliterate("kormo"))
        assertEquals("ধর্ম", BanglaPhoneticEngine.transliterate("dhormo"))
        assertEquals("প্রথম", BanglaPhoneticEngine.transliterate("prothom"))
        assertEquals("ক্ষমা", BanglaPhoneticEngine.transliterate("kshoma"))
        assertEquals("স্বাধীন", BanglaPhoneticEngine.transliterate("swadhin"))
        assertEquals("শান্তি", BanglaPhoneticEngine.transliterate("shanti"))
    }

    @Test
    fun candidatesProvideGboardSuggestionsVariantsAndPrefixCompletions() {
        val amiCandidates = BanglaPhoneticEngine.candidatesFor("ami")
        assertTrue(amiCandidates.contains("আমি"))
        assertTrue(amiCandidates.contains("অমি"))

        val moroCandidates = BanglaPhoneticEngine.candidatesFor("moro")
        assertTrue(moroCandidates.contains("মর"))
        assertTrue(moroCandidates.contains("মরো"))

        val prefixCandidates = BanglaPhoneticEngine.candidatesFor("dhon")
        assertTrue(prefixCandidates.contains("ধন্যবাদ"))

        val chCandidates = BanglaPhoneticEngine.candidatesFor("chal")
        assertTrue(chCandidates.contains("চাল"))
        assertTrue(chCandidates.contains("ছাল"))
    }

    @Test
    fun isInsideBoldSpanTracksMarkdownBoldCorrectly() {
        val text = "Hello **world** today"
        assertFalse(KeystrokeViewModel.isInsideBoldSpan(text, 2))
        assertTrue(KeystrokeViewModel.isInsideBoldSpan(text, 10))
        assertFalse(KeystrokeViewModel.isInsideBoldSpan(text, 18))
    }

    @Test
    fun findWordAndSentenceRangesSelectExpectedBounds() {
        val text = "First sentence. আমি বাংলায় লিখি। Third!"
        val wordRange = KeystrokeViewModel.findWordRange(text, 8)
        assertEquals("sentence.", text.substring(wordRange!!.start, wordRange.end))

        val banglaSentence = KeystrokeViewModel.findSentenceRange(text, 22)
        assertEquals("আমি বাংলায় লিখি।", text.substring(banglaSentence!!.start, banglaSentence.end))
    }
}
