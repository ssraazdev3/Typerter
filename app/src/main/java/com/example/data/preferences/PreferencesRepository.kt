package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AmbientTrack
import com.example.data.model.EditorPreferences
import com.example.data.model.KeyResistance
import com.example.data.model.PageColorPreset
import com.example.data.model.SwitchProfile
import com.example.data.model.ThemeMode
import com.example.data.model.TypewriterFont
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.editorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keystroke_editor_prefs"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_PAPER_MODE = booleanPreferencesKey("dark_paper_mode")
        val PAGE_COLOR_ID = stringPreferencesKey("page_color_id")
        val FOCUS_MODE = booleanPreferencesKey("focus_mode")
        val SPOTLIGHT_ENABLED = booleanPreferencesKey("spotlight_enabled")
        val SPOTLIGHT_WIDTH = floatPreferencesKey("spotlight_width")
        val SPOTLIGHT_DIM = floatPreferencesKey("spotlight_dim")
        val SHOW_TYPING_CURSOR = booleanPreferencesKey("show_typing_cursor")
        val TOUCH_TO_EDIT = booleanPreferencesKey("touch_to_edit")
        val LEVER_ANIMATION = booleanPreferencesKey("lever_animation")
        val USE_MECHANICAL_KEYBOARD = booleanPreferencesKey("use_mechanical_keyboard")
        val KEYBOARD_LANGUAGE = stringPreferencesKey("keyboard_language")
        val KEYBOARD_HEIGHT_SCALE = floatPreferencesKey("keyboard_height_scale")
        val SWITCH_PROFILE = stringPreferencesKey("switch_profile")
        val KEY_RESISTANCE = stringPreferencesKey("key_resistance")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DEFAULT_FONT = stringPreferencesKey("default_font")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT_MULT = floatPreferencesKey("line_height_mult")
        val LETTER_SPACING_EM = floatPreferencesKey("letter_spacing_em")
        val PAPER_WIDTH_FRACTION = floatPreferencesKey("paper_width_fraction")
        val TYPEWRITER_SCROLL = booleanPreferencesKey("typewriter_scroll")
        val FREE_CURSOR_PLACEMENT = booleanPreferencesKey("free_cursor_placement")
        val AMBIENT_TRACK = stringPreferencesKey("ambient_track")
        val AMBIENT_VOLUME = floatPreferencesKey("ambient_volume")
        val DEFAULT_WORD_GOAL = intPreferencesKey("default_word_goal")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val LOCK_ON_BACKGROUND = booleanPreferencesKey("lock_on_background")
        val APP_PASSWORD_SALT = stringPreferencesKey("app_password_salt")
        val APP_PASSWORD_HASH = stringPreferencesKey("app_password_hash")
        val SPOTLIGHT_DEPTH = floatPreferencesKey("spotlight_depth")
        val VOICE_LANGUAGE_TAG = stringPreferencesKey("voice_language_tag")
        val USE_LOCAL_STORAGE_MODE = booleanPreferencesKey("use_local_storage_mode")
    }

    private fun decodePreferences(prefs: Preferences): EditorPreferences = EditorPreferences(
        themeMode = ThemeMode.fromId(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK_DESK.id),
        darkPaperMode = prefs[Keys.DARK_PAPER_MODE] ?: false,
        pageColorId = prefs[Keys.PAGE_COLOR_ID] ?: PageColorPreset.UNCOATED_BOND.id,
        focusMode = prefs[Keys.FOCUS_MODE] ?: false,
        spotlightEnabled = prefs[Keys.SPOTLIGHT_ENABLED] ?: true,
        spotlightWidth = prefs[Keys.SPOTLIGHT_WIDTH] ?: 0.55f,
        spotlightDim = prefs[Keys.SPOTLIGHT_DIM] ?: 0.68f,
        spotlightDepth = prefs[Keys.SPOTLIGHT_DEPTH] ?: 0.80f,
        showTypingCursor = prefs[Keys.SHOW_TYPING_CURSOR] ?: true,
        touchToEditEnabled = prefs[Keys.TOUCH_TO_EDIT] ?: true,
        leverAnimationEnabled = prefs[Keys.LEVER_ANIMATION] ?: true,
        useMechanicalKeyboard = prefs[Keys.USE_MECHANICAL_KEYBOARD] ?: true,
        keyboardLanguageId = prefs[Keys.KEYBOARD_LANGUAGE] ?: "en",
        voiceLanguageTag = prefs[Keys.VOICE_LANGUAGE_TAG] ?: "en-US",
        keyboardHeightScale = prefs[Keys.KEYBOARD_HEIGHT_SCALE] ?: 1.0f,
        switchProfile = SwitchProfile.fromId(
            prefs[Keys.SWITCH_PROFILE] ?: SwitchProfile.VINTAGE_OLIVETTI.id
        ),
        keyResistance = KeyResistance.fromId(
            prefs[Keys.KEY_RESISTANCE] ?: KeyResistance.TACTILE_55G.id
        ),
        soundVolume = prefs[Keys.SOUND_VOLUME] ?: 1.0f,
        hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
        defaultFont = TypewriterFont.fromId(
            prefs[Keys.DEFAULT_FONT] ?: TypewriterFont.SPECIAL_ELITE.id
        ),
        fontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: 17f,
        lineHeightMultiplier = prefs[Keys.LINE_HEIGHT_MULT] ?: 1.55f,
        letterSpacingEm = prefs[Keys.LETTER_SPACING_EM] ?: 0.02f,
        paperWidthFraction = prefs[Keys.PAPER_WIDTH_FRACTION] ?: 0.92f,
        typewriterScroll = prefs[Keys.TYPEWRITER_SCROLL] ?: true,
        freeCursorPlacement = prefs[Keys.FREE_CURSOR_PLACEMENT] ?: true,
        ambientTrack = AmbientTrack.fromId(
            prefs[Keys.AMBIENT_TRACK] ?: AmbientTrack.NONE.id
        ),
        ambientVolume = prefs[Keys.AMBIENT_VOLUME] ?: 0.45f,
        defaultWordGoal = prefs[Keys.DEFAULT_WORD_GOAL] ?: 3000,
        appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
        biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false,
        lockOnBackground = prefs[Keys.LOCK_ON_BACKGROUND] ?: true,
        appPasswordSalt = prefs[Keys.APP_PASSWORD_SALT] ?: "",
        appPasswordHash = prefs[Keys.APP_PASSWORD_HASH] ?: "",
        useLocalStorageMode = prefs[Keys.USE_LOCAL_STORAGE_MODE] ?: false
    )

    val preferencesFlow: Flow<EditorPreferences> = context.editorDataStore.data.map(::decodePreferences)

    suspend fun update(transform: (EditorPreferences) -> EditorPreferences) {
        context.editorDataStore.edit { prefs ->
            val next = transform(decodePreferences(prefs))
            prefs[Keys.THEME_MODE] = next.themeMode.id
            prefs[Keys.DARK_PAPER_MODE] = next.darkPaperMode
            prefs[Keys.PAGE_COLOR_ID] = next.pageColorId
            prefs[Keys.FOCUS_MODE] = next.focusMode
            prefs[Keys.SPOTLIGHT_ENABLED] = next.spotlightEnabled
            prefs[Keys.SPOTLIGHT_WIDTH] = next.spotlightWidth
            prefs[Keys.SPOTLIGHT_DIM] = next.spotlightDim
            prefs[Keys.SHOW_TYPING_CURSOR] = next.showTypingCursor
            prefs[Keys.TOUCH_TO_EDIT] = next.touchToEditEnabled
            prefs[Keys.LEVER_ANIMATION] = next.leverAnimationEnabled
            prefs[Keys.USE_MECHANICAL_KEYBOARD] = next.useMechanicalKeyboard
            prefs[Keys.KEYBOARD_LANGUAGE] = next.keyboardLanguageId
            prefs[Keys.KEYBOARD_HEIGHT_SCALE] = next.keyboardHeightScale
            prefs[Keys.SWITCH_PROFILE] = next.switchProfile.id
            prefs[Keys.KEY_RESISTANCE] = next.keyResistance.id
            prefs[Keys.SOUND_VOLUME] = next.soundVolume
            prefs[Keys.HAPTICS_ENABLED] = next.hapticsEnabled
            prefs[Keys.DEFAULT_FONT] = next.defaultFont.id
            prefs[Keys.FONT_SIZE_SP] = next.fontSizeSp
            prefs[Keys.LINE_HEIGHT_MULT] = next.lineHeightMultiplier
            prefs[Keys.LETTER_SPACING_EM] = next.letterSpacingEm
            prefs[Keys.PAPER_WIDTH_FRACTION] = next.paperWidthFraction
            prefs[Keys.TYPEWRITER_SCROLL] = next.typewriterScroll
            prefs[Keys.FREE_CURSOR_PLACEMENT] = next.freeCursorPlacement
            prefs[Keys.AMBIENT_TRACK] = next.ambientTrack.id
            prefs[Keys.AMBIENT_VOLUME] = next.ambientVolume
            prefs[Keys.DEFAULT_WORD_GOAL] = next.defaultWordGoal
            prefs[Keys.APP_LOCK_ENABLED] = next.appLockEnabled
            prefs[Keys.BIOMETRIC_LOCK_ENABLED] = next.biometricLockEnabled
            prefs[Keys.LOCK_ON_BACKGROUND] = next.lockOnBackground
            prefs[Keys.APP_PASSWORD_SALT] = next.appPasswordSalt
            prefs[Keys.APP_PASSWORD_HASH] = next.appPasswordHash
            prefs[Keys.SPOTLIGHT_DEPTH] = next.spotlightDepth
            prefs[Keys.VOICE_LANGUAGE_TAG] = next.voiceLanguageTag
            prefs[Keys.USE_LOCAL_STORAGE_MODE] = next.useLocalStorageMode
        }
    }
}
