package com.example.ui.editor

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AmbientTrack
import com.example.data.model.EditorPreferences
import com.example.data.model.ExportFormat
import com.example.data.model.KeyResistance
import com.example.data.model.PageColorPreset
import com.example.data.model.SwitchProfile
import com.example.data.model.ThemeMode
import com.example.data.model.TypewriterFont
import com.example.ui.keyboard.KeyboardLanguage
import com.example.ui.theme.toComposeFontFamily
import com.example.ui.viewmodel.SessionStats
import kotlin.math.roundToInt

enum class ActiveEditorSheet {
    SETTINGS,
    TYPOGRAPHY,
    TACTILE,
    AMBIENT_FOCUS,
    LANGUAGE,
    EXPORT
}

private enum class SettingsTab(val label: String) {
    TYPE("Type & Page"),
    TACTILE("Tactile & Keys"),
    LIGHT_AUDIO("Light & Audio"),
    EXPORT("Export")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorBottomSheetHost(
    activeSheet: ActiveEditorSheet?,
    preferences: EditorPreferences,
    currentFont: TypewriterFont,
    wordGoal: Long,
    sessionStats: SessionStats,
    onDismiss: () -> Unit,
    onOpenFolio: () -> Unit,
    onSelectFont: (TypewriterFont) -> Unit,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit,
    onTestKeystroke: () -> Unit,
    onUpdateWordGoal: (Long) -> Unit,
    onSaveExport: (ExportFormat) -> Unit,
    onShareExport: (ExportFormat) -> Unit
) {
    if (activeSheet == null) return
    val context = LocalContext.current

    var selectedTab by remember(activeSheet) {
        mutableStateOf(
            when (activeSheet) {
                ActiveEditorSheet.TACTILE -> SettingsTab.TACTILE
                ActiveEditorSheet.AMBIENT_FOCUS -> SettingsTab.LIGHT_AUDIO
                ActiveEditorSheet.EXPORT -> SettingsTab.EXPORT
                else -> SettingsTab.TYPE
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (activeSheet == ActiveEditorSheet.LANGUAGE) {
                LanguageSheetContent(
                    preferences = preferences,
                    context = context,
                    onUpdatePreferences = onUpdatePreferences,
                    onDismiss = onDismiss
                )
            } else {
                // Unified Settings Sheet Header + All Notes Shortcut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Typewriter Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenFolio()
                        },
                        modifier = Modifier.testTag("settings_all_notes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "All Notes",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Notes Folio")
                    }
                }

                // Category Tabs inside Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("settings_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                when (selectedTab) {
                    SettingsTab.TYPE -> TypographySettingsContent(
                        preferences = preferences,
                        currentFont = currentFont,
                        onSelectFont = onSelectFont,
                        onUpdatePreferences = onUpdatePreferences
                    )
                    SettingsTab.TACTILE -> TactileSettingsContent(
                        preferences = preferences,
                        onUpdatePreferences = onUpdatePreferences,
                        onTestKeystroke = onTestKeystroke
                    )
                    SettingsTab.LIGHT_AUDIO -> LightAndAudioSettingsContent(
                        preferences = preferences,
                        wordGoal = wordGoal,
                        sessionStats = sessionStats,
                        onUpdatePreferences = onUpdatePreferences,
                        onUpdateWordGoal = onUpdateWordGoal
                    )
                    SettingsTab.EXPORT -> ExportSettingsContent(
                        onSaveExport = onSaveExport,
                        onShareExport = onShareExport
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TypographySettingsContent(
    preferences: EditorPreferences,
    currentFont: TypewriterFont,
    onSelectFont: (TypewriterFont) -> Unit,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = preferences.themeMode == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onUpdatePreferences { it.copy(themeMode = mode) } }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("theme_mode_${mode.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (mode == ThemeMode.DARK_DESK) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    contentDescription = mode.displayName,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = mode.displayName,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }

    Text("Page Color & Paper Finish", style = MaterialTheme.typography.titleMedium)
    val currentPageColor = if (preferences.darkPaperMode) {
        PageColorPreset.CHARCOAL_DARK
    } else {
        PageColorPreset.fromId(preferences.pageColorId)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageColorPreset.entries.forEach { preset ->
            val selected = currentPageColor == preset
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onUpdatePreferences {
                            it.copy(
                                pageColorId = preset.id,
                                darkPaperMode = preset.isDark
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("page_color_${preset.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(preset.paperHex))
                        .border(1.dp, Color(preset.inkHex).copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected ${preset.displayName}",
                            tint = Color(preset.inkHex),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }

    ToggleRow(
        title = "Whole Page Dark Paper",
        subtitle = "Keep the paper sheet dark charcoal with luminous ink",
        checked = preferences.darkPaperMode,
        onCheckedChange = { checked ->
            onUpdatePreferences {
                it.copy(
                    darkPaperMode = checked,
                    pageColorId = if (checked) PageColorPreset.CHARCOAL_DARK.id
                    else if (it.pageColorId == PageColorPreset.CHARCOAL_DARK.id) PageColorPreset.UNCOATED_BOND.id
                    else it.pageColorId
                )
            }
        }
    )

    Text("Typeface", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TypewriterFont.entries.forEach { font ->
            val selected = currentFont == font
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectFont(font) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("font_option_${font.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = font.displayName,
                        fontFamily = font.toComposeFontFamily(),
                        fontSize = 17.sp
                    )
                    Text(
                        text = font.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    LabeledSlider(
        label = "Font Size",
        valueLabel = "${preferences.fontSizeSp.roundToInt()} sp",
        value = preferences.fontSizeSp,
        range = 14f..26f,
        onValueChange = { v -> onUpdatePreferences { it.copy(fontSizeSp = v) } }
    )
    LabeledSlider(
        label = "Line Spacing",
        valueLabel = "${String.format("%.2f", preferences.lineHeightMultiplier)}x",
        value = preferences.lineHeightMultiplier,
        range = 1.2f..2.0f,
        onValueChange = { v -> onUpdatePreferences { it.copy(lineHeightMultiplier = v) } }
    )
    LabeledSlider(
        label = "Page Width",
        valueLabel = "${(preferences.paperWidthFraction * 100).roundToInt()}%",
        value = preferences.paperWidthFraction,
        range = 0.72f..1.0f,
        onValueChange = { v -> onUpdatePreferences { it.copy(paperWidthFraction = v) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TactileSettingsContent(
    preferences: EditorPreferences,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit,
    onTestKeystroke: () -> Unit
) {
    ToggleRow(
        title = "Vintage Mechanical Keyboard",
        subtitle = "Switch off to use Google Gboard / System Keyboard",
        checked = preferences.useMechanicalKeyboard,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(useMechanicalKeyboard = checked) }
        }
    )

    Text("Mechanical Switch & Lever Sound", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchProfile.entries.forEach { profile ->
            val selected = preferences.switchProfile == profile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onUpdatePreferences { it.copy(switchProfile = profile) }
                        onTestKeystroke()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("switch_profile_${profile.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Preview sound",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Text("Key Actuation Resistance", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KeyResistance.entries.forEach { resistance ->
            val selected = preferences.keyResistance == resistance
            FilterChip(
                selected = selected,
                onClick = {
                    onUpdatePreferences { it.copy(keyResistance = resistance) }
                    onTestKeystroke()
                },
                label = { Text(resistance.displayName) },
                modifier = Modifier.testTag("resistance_${resistance.id}")
            )
        }
    }

    LabeledSlider(
        label = "Keystroke Sound Volume",
        valueLabel = "${(preferences.soundVolume * 100).roundToInt()}%",
        value = preferences.soundVolume,
        range = 0f..1f,
        onValueChange = { v -> onUpdatePreferences { it.copy(soundVolume = v) } }
    )

    LabeledSlider(
        label = "Keyboard Size (Portrait & Landscape)",
        valueLabel = "${(preferences.keyboardHeightScale * 100).roundToInt()}%",
        value = preferences.keyboardHeightScale,
        range = 0.65f..1.20f,
        onValueChange = { v -> onUpdatePreferences { it.copy(keyboardHeightScale = v) } }
    )

    ToggleRow(
        title = "Typewriter Key Lever Strike",
        subtitle = "Animate mechanical typebar levers swinging onto the paper",
        checked = preferences.leverAnimationEnabled,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(leverAnimationEnabled = checked) }
        }
    )

    ToggleRow(
        title = "Show Blinking Cursor & Strike Guide",
        subtitle = "Display blinking ink cursor where words are typed",
        checked = preferences.showTypingCursor,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(showTypingCursor = checked) }
        }
    )

    ToggleRow(
        title = "Touch Page to Edit",
        subtitle = "Tap anywhere on the paper to jump the cursor and edit",
        checked = preferences.touchToEditEnabled,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(touchToEditEnabled = checked) }
        }
    )

    ToggleRow(
        title = "Free-Carriage Placement",
        subtitle = "Allow placing cursor in open space beyond line ends",
        checked = preferences.freeCursorPlacement,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(freeCursorPlacement = checked) }
        }
    )

    ToggleRow(
        title = "Haptic Actuation Feedback",
        subtitle = "Calibrated vibration pulse scaled by key resistance",
        checked = preferences.hapticsEnabled,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(hapticsEnabled = checked) }
        }
    )

    ToggleRow(
        title = "Typewriter Paper Feed Scroll",
        subtitle = "Paper advances upward to keep active line above platen",
        checked = preferences.typewriterScroll,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(typewriterScroll = checked) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightAndAudioSettingsContent(
    preferences: EditorPreferences,
    wordGoal: Long,
    sessionStats: SessionStats,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit,
    onUpdateWordGoal: (Long) -> Unit
) {
    ToggleRow(
        title = "High-Contrast Focus Mode",
        subtitle = "Steady illuminated paper with minimal chrome",
        checked = preferences.focusMode,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(focusMode = checked) }
        }
    )

    ToggleRow(
        title = "Focus Light Spotlight",
        subtitle = "Smooth fixed lamp beam over dark room vignette",
        checked = preferences.spotlightEnabled,
        onCheckedChange = { checked ->
            onUpdatePreferences { it.copy(spotlightEnabled = checked) }
        }
    )

    Text("Focus Beam Width", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Narrow" to 0.28f, "Medium" to 0.55f, "Wide" to 0.90f).forEach { (label, w) ->
            FilterChip(
                selected = kotlin.math.abs(preferences.spotlightWidth - w) < 0.12f,
                onClick = {
                    onUpdatePreferences { it.copy(spotlightEnabled = true, spotlightWidth = w) }
                },
                label = { Text(label) },
                modifier = Modifier.testTag("beam_preset_${label.lowercase()}")
            )
        }
    }
    LabeledSlider(
        label = "Narrow ↔ Wide Beam",
        valueLabel = "${(preferences.spotlightWidth * 100).roundToInt()}%",
        value = preferences.spotlightWidth,
        range = 0.22f..1.0f,
        onValueChange = { v -> onUpdatePreferences { it.copy(spotlightEnabled = true, spotlightWidth = v) } }
    )

    LabeledSlider(
        label = "Surrounding Dim Control",
        valueLabel = "${(preferences.spotlightDim * 100).roundToInt()}%",
        value = preferences.spotlightDim,
        range = 0.10f..0.95f,
        onValueChange = { v -> onUpdatePreferences { it.copy(spotlightDim = v) } }
    )

    Text("Light Deep (Fog-Light Contrast)", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Soft" to 0.42f, "Deep" to 0.78f, "Pitch Dark" to 0.96f).forEach { (label, d) ->
            FilterChip(
                selected = kotlin.math.abs(preferences.spotlightDepth - d) < 0.14f,
                onClick = {
                    onUpdatePreferences { it.copy(spotlightEnabled = true, spotlightDepth = d) }
                },
                label = { Text(label) },
                modifier = Modifier.testTag("light_deep_preset_${label.lowercase().replace(" ", "_")}")
            )
        }
    }
    LabeledSlider(
        label = "Light Deep",
        valueLabel = "${(preferences.spotlightDepth * 100).roundToInt()}%",
        value = preferences.spotlightDepth,
        range = 0.25f..1.0f,
        onValueChange = { v -> onUpdatePreferences { it.copy(spotlightEnabled = true, spotlightDepth = v) } }
    )

    ToggleRow(
        title = "Whole Page Dark Paper",
        subtitle = "Keep the entire sheet dark under the smooth focus beam",
        checked = preferences.darkPaperMode,
        onCheckedChange = { checked ->
            onUpdatePreferences {
                it.copy(
                    darkPaperMode = checked,
                    pageColorId = if (checked) PageColorPreset.CHARCOAL_DARK.id
                    else if (it.pageColorId == PageColorPreset.CHARCOAL_DARK.id) PageColorPreset.UNCOATED_BOND.id
                    else it.pageColorId
                )
            }
        }
    )

    HorizontalDivider()

    Text("Ambient Soundscape (Offline DSP)", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmbientTrack.entries.forEach { track ->
            val selected = preferences.ambientTrack == track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onUpdatePreferences { it.copy(ambientTrack = track) } }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("ambient_track_${track.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(track.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        track.mood,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    LabeledSlider(
        label = "Ambient Volume",
        valueLabel = "${(preferences.ambientVolume * 100).roundToInt()}%",
        value = preferences.ambientVolume,
        range = 0f..1f,
        onValueChange = { v -> onUpdatePreferences { it.copy(ambientVolume = v) } }
    )

    Text("Page Word Goal", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(500L, 1000L, 3000L, 5000L).forEach { goal ->
            FilterChip(
                selected = wordGoal == goal,
                onClick = { onUpdateWordGoal(goal) },
                label = { Text("${goal}w") }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatBlock("Session Time", formatDuration(sessionStats.elapsedSeconds))
        StatBlock("Session Words", "+${sessionStats.wordsWrittenInSession}")
        StatBlock("Speed", "${sessionStats.wpm} WPM")
    }
}

@Composable
private fun LanguageSheetContent(
    preferences: EditorPreferences,
    context: Context,
    onUpdatePreferences: ((EditorPreferences) -> EditorPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = "Keyboard Languages & Google Gboard",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = "Choose a built-in vintage typewriter layout (including Abc → বাংলা phonetic) or use Google Gboard for all system languages.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                onUpdatePreferences { it.copy(useMechanicalKeyboard = false) }
                onDismiss()
            },
            modifier = Modifier.weight(1f).testTag("use_gboard_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Use Gboard")
        }
        OutlinedButton(
            onClick = {
                onUpdatePreferences { it.copy(useMechanicalKeyboard = false) }
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            },
            modifier = Modifier.weight(1f).testTag("system_ime_picker_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Switch IME")
        }
    }

    HorizontalDivider()

    Text("Built-in Vintage Typewriter Layouts", style = MaterialTheme.typography.titleMedium)
    val currentLang = KeyboardLanguage.fromId(preferences.keyboardLanguageId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyboardLanguage.entries.forEach { lang ->
            val selected = preferences.useMechanicalKeyboard && currentLang == lang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        onUpdatePreferences {
                            it.copy(
                                useMechanicalKeyboard = true,
                                keyboardLanguageId = lang.id
                            )
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .testTag("lang_option_${lang.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lang.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        lang.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportSettingsContent(
    onSaveExport: (ExportFormat) -> Unit,
    onShareExport: (ExportFormat) -> Unit
) {
    Text(
        text = "Save directly to your device storage or share to other apps.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExportFormat.entries.forEach { format ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(format.label, fontWeight = FontWeight.Medium)
                        Text(
                            format.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = { onSaveExport(format) },
                        modifier = Modifier.testTag("export_save_${format.extension}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save ${format.label}"
                        )
                    }
                    IconButton(
                        onClick = { onShareExport(format) },
                        modifier = Modifier.testTag("export_share_${format.extension}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share ${format.label}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return String.format("%02d:%02d", mins, secs)
}
