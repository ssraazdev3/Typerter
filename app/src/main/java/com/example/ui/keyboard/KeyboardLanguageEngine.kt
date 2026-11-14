package com.example.ui.keyboard

enum class KeyboardLanguage(
    val id: String,
    val shortLabel: String,
    val displayName: String,
    val subtitle: String
) {
    ENGLISH("en", "EN", "English", "Classic QWERTY"),
    BANGLA_PHONETIC("bn_phonetic", "Abc→বাং", "Abc → বাংলা (Phonetic)", "Gboard-style Abc to Bangla transliteration"),
    BANGLA_DIRECT("bn_direct", "বাংলা", "বাংলা (Bangla Direct)", "Native Bengali script keys"),
    HINDI("hi", "हिन्दी", "हिन्दी (Hindi)", "Devanagari script layout"),
    ARABIC("ar", "عر", "العربية (Arabic)", "Right-to-left Arabic script"),
    SPANISH("es", "ES", "Español", "QWERTY with ñ and accents"),
    FRENCH("fr", "FR", "Français", "AZERTY with French accents"),
    GERMAN("de", "DE", "Deutsch", "QWERTZ with umlauts & ß");

    val isLatin: Boolean
        get() = this == ENGLISH || this == BANGLA_PHONETIC || this == SPANISH || this == FRENCH || this == GERMAN

    companion object {
        fun fromId(id: String): KeyboardLanguage =
            entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

/**
 * High-accuracy Google Gboard / Avro-style phonetic Abc -> Bangla transliteration engine
 * supporting consonant conjuncts (যুক্তবর্ণ), reph (র্), r-fola (্র), jo-fola (্য), bo-fola (্ব),
 * rich everyday vocabulary, prefix suggestions, and ranked alternative candidates.
 */
object BanglaPhoneticEngine {

    private val commonWords: Map<String, List<String>> = mapOf(
        // Pronouns & Core Words
        "ami" to listOf("আমি", "অমি"),
        "amar" to listOf("আমার", "অমর"),
        "amake" to listOf("আমাকে"),
        "amader" to listOf("আমাদের"),
        "amra" to listOf("আমরা"),
        "tumi" to listOf("তুমি"),
        "tomar" to listOf("তোমার"),
        "tomake" to listOf("তোমাকে"),
        "tomader" to listOf("তোমাদের"),
        "tomra" to listOf("তোমরা"),
        "apni" to listOf("আপনি"),
        "apnar" to listOf("আপনার"),
        "apnake" to listOf("আপনাকে"),
        "apnara" to listOf("আপনারা"),
        "tui" to listOf("তুই"),
        "tor" to listOf("তোর"),
        "she" to listOf("সে", "শে"),
        "se" to listOf("সে"),
        "shei" to listOf("সেই"),
        "sei" to listOf("সেই"),
        "tar" to listOf("তার", "তাঁর"),
        "take" to listOf("তাকে", "তাঁকে"),
        "tara" to listOf("তারা"),
        "tader" to listOf("তাদের"),
        "ora" to listOf("ওরা"),
        "uni" to listOf("উনি"),
        "tini" to listOf("তিনি"),
        "ei" to listOf("এই"),
        "oi" to listOf("ওই", "ঐ"),
        "eta" to listOf("এটা"),
        "ota" to listOf("ওটা"),
        "sheta" to listOf("সেটা"),
        "seta" to listOf("সেটা"),
        "ekhane" to listOf("এখানে"),
        "okhane" to listOf("ওখানে"),
        "shekhane" to listOf("সেখানে"),
        "sekhane" to listOf("সেখানে"),

        // Questions & Connectors
        "ki" to listOf("কি", "কী"),
        "ke" to listOf("কে"),
        "kar" to listOf("কার"),
        "kake" to listOf("কাকে"),
        "keno" to listOf("কেন", "কেনো"),
        "kemon" to listOf("কেমন"),
        "kothay" to listOf("কোথায়"),
        "kothai" to listOf("কোথায়"),
        "kokhon" to listOf("কখন"),
        "koto" to listOf("কত", "কতো"),
        "kivabe" to listOf("কিভাবে", "কীভাবে"),
        "kibhabe" to listOf("কীভাবে", "কিভাবে"),
        "kon" to listOf("কোন", "কোনো"),
        "kono" to listOf("কোনো", "কোন"),
        "ar" to listOf("আর"),
        "ebong" to listOf("এবং"),
        "kintu" to listOf("কিন্তু"),
        "tobe" to listOf("তবে"),
        "jodi" to listOf("যদি"),
        "tahole" to listOf("তাহলে"),
        "karon" to listOf("কারণ"),
        "jonno" to listOf("জন্য"),
        "jonne" to listOf("জন্যে"),
        "sathe" to listOf("সাথে"),
        "shathe" to listOf("সাথে"),
        "theke" to listOf("থেকে"),
        "diye" to listOf("দিয়ে"),
        "niye" to listOf("নিয়ে"),
        "kore" to listOf("করে"),
        "hoye" to listOf("হয়ে"),
        "hoy" to listOf("হয়"),
        "na" to listOf("না"),
        "nai" to listOf("নাই"),
        "nei" to listOf("নেই"),
        "haan" to listOf("হ্যাঁ"),
        "ha" to listOf("হ্যাঁ", "হা"),
        "hyan" to listOf("হ্যাঁ"),
        "ekdom" to listOf("একদম"),
        "ekhon" to listOf("এখন"),
        "tokhon" to listOf("তখন"),
        "aj" to listOf("আজ"),
        "ajke" to listOf("আজকে"),
        "kal" to listOf("কাল", "কল"),
        "kalke" to listOf("কালকে"),
        "sob" to listOf("সব"),
        "shob" to listOf("সব"),
        "sobai" to listOf("সবাই"),
        "shobai" to listOf("সবাই"),
        "kichu" to listOf("কিছু"),
        "onek" to listOf("অনেক"),
        "khub" to listOf("খুব"),
        "aro" to listOf("আরো", "আরও"),
        "matro" to listOf("মাত্র"),
        "shudhu" to listOf("শুধু"),
        "sudhu" to listOf("শুধু"),

        // Verbs & Common Phrases
        "acho" to listOf("আছো", "আছ"),
        "achi" to listOf("আছি"),
        "achen" to listOf("আছেন"),
        "ache" to listOf("আছে"),
        "chilo" to listOf("ছিল"),
        "chhilo" to listOf("ছিল"),
        "hobe" to listOf("হবে"),
        "holo" to listOf("হলো", "হল"),
        "koro" to listOf("করো", "কর"),
        "korbo" to listOf("করবো", "করব"),
        "korchi" to listOf("করছি"),
        "korche" to listOf("করছে"),
        "korte" to listOf("করতে"),
        "jani" to listOf("জানি"),
        "jano" to listOf("জানো"),
        "jabo" to listOf("যাবো", "যাব"),
        "jacchi" to listOf("যাচ্ছি"),
        "asho" to listOf("আসো", "এসো"),
        "ashbo" to listOf("আসবো", "আসব"),
        "dekho" to listOf("দেখো", "দেখ"),
        "dekhi" to listOf("দেখি"),
        "bolo" to listOf("বলো", "বল"),
        "bolchi" to listOf("বলছি"),
        "bolbo" to listOf("বলবো", "বলব"),
        "bhalo" to listOf("ভালো", "ভাল"),
        "valo" to listOf("ভালো", "ভাল"),
        "bhalobashi" to listOf("ভালোবাসি"),
        "valobashi" to listOf("ভালোবাসি"),
        "bhalobasha" to listOf("ভালোবাসা"),
        "valobasha" to listOf("ভালোবাসা"),
        "chai" to listOf("চাই"),
        "lagbe" to listOf("লাগবে"),
        "lage" to listOf("লাগে"),
        "parbo" to listOf("পারবো", "পারব"),
        "pari" to listOf("পারি"),
        "paro" to listOf("পারো"),
        "thako" to listOf("থাকো", "থাক"),
        "thakbo" to listOf("থাকবো", "থাকব"),
        "bujhi" to listOf("বুঝি"),
        "bujhlam" to listOf("বুঝলাম"),
        "likhi" to listOf("লিখি"),
        "likhchi" to listOf("লিখছি"),
        "lekha" to listOf("লেখা"),
        "likho" to listOf("লিখো"),
        "pora" to listOf("পড়া"),
        "pori" to listOf("পড়ি"),
        "khao" to listOf("খাও"),
        "khabo" to listOf("খাবো", "খাব"),
        "dorkar" to listOf("দরকার"),
        "proyojon" to listOf("প্রয়োজন"),
        "thik" to listOf("ঠিক"),
        "sotti" to listOf("সত্যি"),
        "shotti" to listOf("সত্যি"),
        "mittha" to listOf("মিথ্যা"),

        // Greetings, People & Writing
        "bangla" to listOf("বাংলা", "বাঙলা"),
        "bangladesh" to listOf("বাংলাদেশ"),
        "dhonnobad" to listOf("ধন্যবাদ"),
        "shuvo" to listOf("শুভ"),
        "shuveccha" to listOf("শুভেচ্ছা"),
        "nomoshkar" to listOf("নমস্কার"),
        "salam" to listOf("সালাম"),
        "khuushi" to listOf("খুশি"),
        "khushi" to listOf("খুশি"),
        "sundor" to listOf("সুন্দর"),
        "shundor" to listOf("সুন্দর"),
        "manush" to listOf("মানুষ"),
        "bondhu" to listOf("বন্ধু"),
        "ma" to listOf("মা"),
        "baba" to listOf("বাবা"),
        "bhai" to listOf("ভাই"),
        "bon" to listOf("বোন"),
        "ghor" to listOf("ঘর"),
        "bari" to listOf("বাড়ি"),
        "desh" to listOf("দেশ"),
        "bhasha" to listOf("ভাষা"),
        "vasa" to listOf("ভাষা"),
        "kobita" to listOf("কবিতা"),
        "golpo" to listOf("গল্প"),
        "gaan" to listOf("গান"),
        "mon" to listOf("মন"),
        "hridoy" to listOf("হৃদয়"),
        "jibon" to listOf("জীবন"),
        "somoy" to listOf("সময়"),
        "shomoy" to listOf("সময়"),
        "akash" to listOf("আকাশ"),
        "batash" to listOf("বাতাস"),
        "brishti" to listOf("বৃষ্টি"),
        "bristi" to listOf("বৃষ্টি"),
        "megh" to listOf("মেঘ"),
        "chand" to listOf("চাঁদ"),
        "surjo" to listOf("সূর্য"),
        "shurjo" to listOf("সূর্য"),
        "tara" to listOf("তারা"),
        "alo" to listOf("আলো"),
        "andhar" to listOf("আঁধার"),
        "ondhokar" to listOf("অন্ধকার"),
        "raat" to listOf("রাত"),
        "din" to listOf("দিন"),
        "sokal" to listOf("সকাল"),
        "shokal" to listOf("সকাল"),
        "dupur" to listOf("দুপুর"),
        "bikal" to listOf("বিকাল"),
        "shondha" to listOf("সন্ধ্যা"),
        "sondha" to listOf("সন্ধ্যা"),
        "boi" to listOf("বই"),
        "kagoj" to listOf("কাগজ"),
        "kolom" to listOf("কলম"),
        "chithi" to listOf("চিঠি"),
        "smriti" to listOf("স্মৃতি"),
        "shopno" to listOf("স্বপ্ন"),
        "sopno" to listOf("স্বপ্ন"),
        "prithibi" to listOf("পৃথিবী"),
        "pritibi" to listOf("পৃথিবী"),
        "nodi" to listOf("নদী"),
        "pahar" to listOf("পাহাড়"),
        "ful" to listOf("ফুল"),
        "gach" to listOf("গাছ"),
        "pakhi" to listOf("পাখি"),
        "anondo" to listOf("আনন্দ"),
        "dukkho" to listOf("দুঃখ"),
        "shanti" to listOf("শান্তি"),
        "asha" to listOf("আশা"),
        "biswas" to listOf("বিশ্বাস"),
        "bishwas" to listOf("বিশ্বাস"),
        "swadhin" to listOf("স্বাধীন"),
        "utsab" to listOf("উৎসব"),
        "hotath" to listOf("হঠাৎ"),
        "notun" to listOf("নতুন"),
        "purono" to listOf("পুরোনো", "পুরানো"),

        // Numbers
        "ek" to listOf("এক"),
        "dui" to listOf("দুই"),
        "tin" to listOf("তিন"),
        "char" to listOf("চার"),
        "panch" to listOf("পাঁচ"),
        "choy" to listOf("ছয়"),
        "sat" to listOf("সাত", "সৎ"),
        "aat" to listOf("আট"),
        "noy" to listOf("নয়"),
        "dosh" to listOf("দশ")
    )

    private val independentVowels = listOf(
        "ou" to "ঔ",
        "oi" to "ঐ",
        "oo" to "ঊ",
        "aa" to "আ",
        "ee" to "ঈ",
        "ri" to "ঋ",
        "A" to "আ",
        "I" to "ঈ",
        "U" to "ঊ",
        "O" to "ও",
        "E" to "এ",
        "a" to "অ",
        "i" to "ই",
        "u" to "উ",
        "e" to "এ",
        "o" to "অ"
    )

    private val vowelSigns = listOf(
        "ou" to "ৌ",
        "oi" to "ৈ",
        "oo" to "ূ",
        "aa" to "া",
        "ee" to "ী",
        "ri" to "ৃ",
        "a" to "া",
        "i" to "ি",
        "I" to "ী",
        "u" to "ু",
        "U" to "ূ",
        "e" to "ে",
        "E" to "ে",
        "o" to "ো",
        "O" to "ো"
    )

    // Conjunct clusters sorted longest-first for greedy Avro/Gboard matching
    private val conjuncts = listOf(
        "ksh" to "ক্ষ",
        "ShT" to "ষ্ট",
        "sht" to "ষ্ট",
        "sth" to "স্থ",
        "shr" to "শ্র",
        "shw" to "শ্ব",
        "shm" to "শ্ম",
        "ngg" to "ঙ্গ",
        "ngk" to "ঙ্ক",
        "ndh" to "ন্ধ",
        "nth" to "ন্থ",
        "mbh" to "ম্ভ",
        "dhw" to "ধ্ব",
        "bhr" to "ভ্র",
        "chh" to "ছ",
        "kt" to "ক্ত",
        "kr" to "ক্র",
        "kl" to "ক্ল",
        "ks" to "ক্স",
        "kk" to "ক্ক",
        "gr" to "গ্র",
        "gn" to "গ্ন",
        "gg" to "জ্ঞ",
        "nc" to "ঞ্চ",
        "nj" to "ঞ্জ",
        "nd" to "ন্দ",
        "nt" to "ন্ত",
        "np" to "ম্প",
        "mp" to "ম্প",
        "mb" to "ম্ব",
        "st" to "স্ত",
        "sp" to "স্প",
        "sk" to "স্ক",
        "sm" to "স্ম",
        "sn" to "স্ন",
        "sr" to "স্র",
        "sw" to "স্ব",
        "tr" to "ত্র",
        "tt" to "ত্ত",
        "tm" to "ত্ম",
        "tw" to "ত্ব",
        "dr" to "দ্র",
        "dd" to "দ্দ",
        "dw" to "দ্ব",
        "pr" to "প্র",
        "pl" to "প্ল",
        "pp" to "প্প",
        "fr" to "ফ্র",
        "br" to "ব্র",
        "bl" to "ব্ল",
        "bb" to "ব্ব",
        "mr" to "ম্র",
        "mm" to "ম্ম",
        "cc" to "চ্চ",
        "jj" to "জ্জ",
        "nn" to "ন্ন",
        "ll" to "ল্ল",
        "hm" to "হ্ম",
        "hr" to "হ্র",
        "hy" to "হ্য"
    )

    private val singleConsonants = listOf(
        "Sh" to "ষ",
        "sh" to "শ",
        "kh" to "খ",
        "gh" to "ঘ",
        "ch" to "চ",
        "jh" to "ঝ",
        "Th" to "ঠ",
        "Dh" to "ঢ",
        "th" to "থ",
        "dh" to "ধ",
        "ph" to "ফ",
        "bh" to "ভ",
        "Rh" to "ঢ়",
        "ng" to "ং",
        "k" to "ক",
        "g" to "গ",
        "c" to "চ",
        "j" to "জ",
        "T" to "ট",
        "D" to "ড",
        "N" to "ণ",
        "t" to "ত",
        "d" to "দ",
        "n" to "ন",
        "p" to "প",
        "f" to "ফ",
        "b" to "ব",
        "v" to "ভ",
        "m" to "ম",
        "z" to "য",
        "r" to "র",
        "l" to "ল",
        "s" to "স",
        "h" to "হ",
        "R" to "ড়",
        "y" to "য়",
        "Y" to "য",
        "w" to "ও",
        "q" to "ক",
        "x" to "ক্স"
    )

    private val digits = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )

    fun candidatesFor(latinBuffer: String): List<String> {
        if (latinBuffer.isEmpty()) return emptyList()
        val lower = latinBuffer.lowercase()
        val results = linkedSetOf<String>()

        // 1. Exact vocabulary hits first (highest accuracy, like Gboard)
        commonWords[lower]?.let { results.addAll(it) }

        // 2. Primary phonetic transliteration
        val primary = ruleBasedTransliterate(latinBuffer)
        if (primary.isNotEmpty()) results.add(primary)

        // 3. Initial 'a' -> 'আ' variant (users often type 'a' for আ at word start, e.g. 'amra'/'aj')
        if (latinBuffer.startsWith("a") && !latinBuffer.startsWith("aa")) {
            val aaStart = ruleBasedTransliterate("aa" + latinBuffer.drop(1))
            if (aaStart.isNotEmpty()) results.add(aaStart)
        }

        // 4. Case-normalized variant if mixed case entered
        if (latinBuffer != lower) {
            val lowerTx = ruleBasedTransliterate(lower)
            if (lowerTx.isNotEmpty()) results.add(lowerTx)
        }

        // 5. Common ambiguous consonant & trailing 'o' ('ো') variants
        if (lower.contains("ch") && !lower.contains("chh")) {
            results.add(ruleBasedTransliterate(latinBuffer.replace("ch", "chh", ignoreCase = true)))
        }
        if (latinBuffer.endsWith("o") && latinBuffer.length >= 2) {
            val withOkar = ruleBasedTransliterate(latinBuffer.dropLast(1) + "O")
            if (withOkar.isNotEmpty()) results.add(withOkar)
        }

        // 6. Top prefix dictionary completions (for partial typing like "dhon" -> "ধন্যবাদ")
        if (lower.length >= 3) {
            commonWords.entries
                .asSequence()
                .filter { (key, _) -> key.startsWith(lower) && key != lower }
                .take(3)
                .forEach { (_, vals) -> vals.firstOrNull()?.let { results.add(it) } }
        }

        return results.filter { it.isNotBlank() }.toList().take(5)
    }

    fun transliterate(input: String): String {
        if (input.isEmpty()) return ""
        val lower = input.lowercase()
        commonWords[lower]?.firstOrNull()?.let { return it }
        return ruleBasedTransliterate(input)
    }

    private fun ruleBasedTransliterate(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        var prevWasConsonant = false

        while (i < input.length) {
            val ch = input[i]

            digits[ch]?.let {
                sb.append(it)
                i++
                prevWasConsonant = false
                continue
            }
            when (ch) {
                '.' -> {
                    sb.append("।")
                    i++
                    prevWasConsonant = false
                    continue
                }
                '^' -> {
                    sb.append("ঁ")
                    i++
                    prevWasConsonant = false
                    continue
                }
                ':' -> {
                    sb.append("ঃ")
                    i++
                    prevWasConsonant = false
                    continue
                }
                '`' -> {
                    if (prevWasConsonant) sb.append("্")
                    i++
                    prevWasConsonant = false
                    continue
                }
            }

            // R-fola (C + r + vowel or cluster) vs Jo-fola (C + y/Y) vs Bo-fola (C + w)
            if (prevWasConsonant) {
                if (ch == 'r' && (i + 1 >= input.length || isVowelStart(input[i + 1]))) {
                    sb.append("্র")
                    i++
                    prevWasConsonant = true
                    continue
                }
                if ((ch == 'y' || ch == 'Y') && i + 1 < input.length && isVowelStart(input[i + 1])) {
                    sb.append("্য")
                    i++
                    prevWasConsonant = true
                    continue
                }
                if (ch == 'w') {
                    sb.append("্ব")
                    i++
                    prevWasConsonant = true
                    continue
                }
            } else {
                // Reph (র্) when 'r' appears after a vowel and right before another consonant (not 'r'/'h'/'y'/vowel)
                if (ch == 'r' && i > 0 && i + 1 < input.length && isConsonantStart(input[i + 1]) &&
                    input[i + 1] !in charArrayOf('r', 'y', 'Y', 'h')
                ) {
                    sb.append("র্")
                    i++
                    prevWasConsonant = false
                    continue
                }
            }

            // 1. Check multi-consonant conjuncts first
            val conjMatch = conjuncts.firstOrNull { (k, _) ->
                input.regionMatches(i, k, 0, k.length, ignoreCase = false)
            }
            if (conjMatch != null) {
                sb.append(conjMatch.second)
                i += conjMatch.first.length
                prevWasConsonant = true
                continue
            }

            // 2. Check single consonants & aspirated digraphs
            val consMatch = singleConsonants.firstOrNull { (k, _) ->
                input.regionMatches(i, k, 0, k.length, ignoreCase = false)
            }
            if (consMatch != null) {
                sb.append(consMatch.second)
                i += consMatch.first.length
                // Anusvara (ং) doesn't take vowel signs
                prevWasConsonant = consMatch.first != "ng"
                continue
            }

            // 3. Check vowels / vowel signs
            if (prevWasConsonant) {
                val signMatch = vowelSigns.firstOrNull { (k, _) ->
                    input.regionMatches(i, k, 0, k.length, ignoreCase = false)
                }
                if (signMatch != null) {
                    // Lowercase 'o' after a consonant is inherent vowel অ; uppercase 'O' produces 'ো'
                    if (signMatch.first != "o") {
                        sb.append(signMatch.second)
                    }
                    i += signMatch.first.length
                    prevWasConsonant = false
                    continue
                }
            } else {
                val indepMatch = independentVowels.firstOrNull { (k, _) ->
                    input.regionMatches(i, k, 0, k.length, ignoreCase = false)
                }
                if (indepMatch != null) {
                    sb.append(indepMatch.second)
                    i += indepMatch.first.length
                    prevWasConsonant = false
                    continue
                }
            }

            sb.append(ch)
            i++
            prevWasConsonant = false
        }
        return sb.toString()
    }

    private fun isVowelStart(c: Char): Boolean =
        c in charArrayOf('a', 'A', 'e', 'E', 'i', 'I', 'o', 'O', 'u', 'U')

    private fun isConsonantStart(c: Char): Boolean =
        (c in 'a'..'z' || c in 'A'..'Z') && !isVowelStart(c)
}
