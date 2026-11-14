package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Note(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val wordCount: Long = 0L,
    val charCount: Long = 0L,
    val wordGoal: Long = 1000L,
    val fontId: String = TypewriterFont.SPECIAL_ELITE.id,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WritingSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val noteId: String = "",
    val wordsWritten: Long = 0L,
    val durationSeconds: Long = 0L,
    val wpm: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class TypewriterFont(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    SPECIAL_ELITE("special_elite", "Special Elite", "Vintage inked ribbon"),
    COURIER_PRIME("courier_prime", "Courier Prime", "Crisp screenplay mono"),
    IBM_PLEX_MONO("ibm_plex_mono", "IBM Plex Mono", "Precision mechanical"),
    SPACE_MONO("space_mono", "Space Mono", "Geometric editorial"),
    NEWSREADER("newsreader", "Newsreader", "Warm literary serif"),
    INTER("inter", "Inter", "Clean minimalist sans");

    companion object {
        fun fromId(id: String): TypewriterFont =
            entries.firstOrNull { it.id == id } ?: SPECIAL_ELITE
    }
}

enum class SwitchProfile(
    val id: String,
    val displayName: String,
    val description: String
) {
    CHERRY_BLUE("cherry_blue", "Clicky Blue", "Sharp tactile click & snap"),
    TACTILE_BROWN("tactile_brown", "Tactile Brown", "Soft bump, subdued clack"),
    TOPRE_THOCK("topre_thock", "Topre Thock", "Deep damped PBT thock"),
    VINTAGE_OLIVETTI("vintage_olivetti", "Vintage Hammer", "Mechanical keybar & bell");

    companion object {
        fun fromId(id: String): SwitchProfile =
            entries.firstOrNull { it.id == id } ?: VINTAGE_OLIVETTI
    }
}

enum class KeyResistance(
    val id: String,
    val displayName: String,
    val forceGrams: Int,
    val hapticDurationMs: Long,
    val hapticAmplitude: Int,
    val pressDepthDp: Float,
    val springStiffness: Float
) {
    LIGHT_35G("light_35g", "Feather 35g", 35, 9L, 65, 2.0f, 1100f),
    TACTILE_55G("tactile_55g", "Tactile 55g", 55, 16L, 140, 3.2f, 820f),
    HEAVY_75G("heavy_75g", "Heavy 75g", 75, 24L, 210, 4.4f, 560f),
    VINTAGE_90G("vintage_90g", "Mechanical 90g", 90, 34L, 255, 5.5f, 380f);

    companion object {
        fun fromId(id: String): KeyResistance =
            entries.firstOrNull { it.id == id } ?: TACTILE_55G
    }
}

enum class AmbientTrack(
    val id: String,
    val displayName: String,
    val mood: String
) {
    NONE("none", "Silence", "Pure keystrokes only"),
    SNOWFALL("snowfall", "Snowfall Pad", "Warm nocturnal ambient"),
    RAIN("rain", "Midnight Rain", "Windowpane rainfall"),
    FIREPLACE("fireplace", "Candlelit Hearth", "Soft ember crackle"),
    CAFE("cafe", "Late Night Study", "Quiet room murmur");

    companion object {
        fun fromId(id: String): AmbientTrack =
            entries.firstOrNull { it.id == id } ?: SNOWFALL
    }
}

enum class ThemeMode(val id: String, val displayName: String) {
    DARK_DESK("dark_desk", "Dark Desk"),
    LIGHT_STUDIO("light_studio", "Light Studio");

    companion object {
        fun fromId(id: String): ThemeMode =
            entries.firstOrNull { it.id == id } ?: DARK_DESK
    }
}

enum class PageColorPreset(
    val id: String,
    val displayName: String,
    val paperHex: Long,
    val inkHex: Long,
    val isDark: Boolean = false
) {
    UNCOATED_BOND("uncoated_bond", "Unoffset Natural", 0xFFF2ECE0, 0xFF1D1B18, false),
    CLASSIC_CREAM("classic_cream", "Warm Cream", 0xFFFAF6EB, 0xFF1E1C18, false),
    CRISP_WHITE("crisp_white", "Bright White", 0xFFFCFBF8, 0xFF171614, false),
    AGED_PARCHMENT("aged_parchment", "Vintage Paper", 0xFFE8DCC2, 0xFF261F17, false),
    SOFT_SAGE("soft_sage", "Soft Sage", 0xFFE4ECE5, 0xFF18221A, false),
    CHARCOAL_DARK("charcoal_dark", "Dark Sheet", 0xFF16161B, 0xFFEDE6D8, true);

    companion object {
        fun fromId(id: String): PageColorPreset =
            entries.firstOrNull { it.id == id } ?: UNCOATED_BOND
    }
}

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
    val subtitle: String
) {
    MARKDOWN("md", "text/markdown", "Markdown (.md)", "Structured plain text with heading"),
    PDF("pdf", "application/pdf", "PDF Document (.pdf)", "Paginated typeset manuscript"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document (.docx)", "Editable Office Open XML"),
    TXT("txt", "text/plain", "Plain Text (.txt)", "Universal raw text file")
}

data class EditorPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK_DESK,
    val darkPaperMode: Boolean = false,
    val pageColorId: String = PageColorPreset.UNCOATED_BOND.id,
    val focusMode: Boolean = false,
    val spotlightEnabled: Boolean = true,
    val spotlightWidth: Float = 0.55f,
    val spotlightDim: Float = 0.68f,
    val spotlightDepth: Float = 0.80f,
    val showTypingCursor: Boolean = true,
    val touchToEditEnabled: Boolean = true,
    val leverAnimationEnabled: Boolean = true,
    val useMechanicalKeyboard: Boolean = true,
    val keyboardLanguageId: String = "en",
    val voiceLanguageTag: String = "en-US",
    val keyboardHeightScale: Float = 1.0f,
    val switchProfile: SwitchProfile = SwitchProfile.VINTAGE_OLIVETTI,
    val keyResistance: KeyResistance = KeyResistance.TACTILE_55G,
    val soundVolume: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val defaultFont: TypewriterFont = TypewriterFont.SPECIAL_ELITE,
    val fontSizeSp: Float = 17f,
    val lineHeightMultiplier: Float = 1.55f,
    val letterSpacingEm: Float = 0.02f,
    val paperWidthFraction: Float = 0.92f,
    val typewriterScroll: Boolean = true,
    val freeCursorPlacement: Boolean = true,
    val ambientTrack: AmbientTrack = AmbientTrack.NONE,
    val ambientVolume: Float = 0.45f,
    val defaultWordGoal: Int = 3000,
    val appLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    val appPasswordSalt: String = "",
    val appPasswordHash: String = "",
    val useLocalStorageMode: Boolean = false
)
