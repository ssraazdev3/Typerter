package com.example.ui.keyboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KeyResistance
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.InterFamily
import com.example.voice.VoiceTypingState
import kotlinx.coroutines.delay

private enum class ShiftState { OFF, ONCE, CAPS }

private val EmojiCategories = listOf(
    "Smileys" to listOf("😀", "😊", "😌", "🤔", "🥹", "😍", "😎", "🥳", "😴", "🤯", "✍️", "🙏"),
    "Writing" to listOf("📝", "📖", "📚", "🖋️", "⌨️", "🕯️", "☕", "🌙", "✨", "💡", "📌", "🔖"),
    "Nature" to listOf("🌧️", "❄️", "🔥", "🌿", "🍂", "🌸", "🌊", "⭐", "☁️", "🌲", "🕊️", "🦋"),
    "Symbols" to listOf("❤️", "🖤", "🤍", "💫", "🎵", "🎶", "⚡", "✔️", "—", "…", "•", "§")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MechanicalKeyboard(
    resistance: KeyResistance,
    language: KeyboardLanguage,
    heightScale: Float,
    maxKeyboardHeight: Dp,
    isBoldActive: Boolean,
    voiceState: VoiceTypingState,
    phoneticBuffer: String,
    phoneticCandidates: List<String>,
    onCharKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleVoiceTyping: () -> Unit,
    onToggleVoiceLanguage: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onSwitchToGboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shiftState by remember { mutableStateOf(ShiftState.OFF) }
    var symbolLayer by remember { mutableStateOf(false) }
    var emojiPanelOpen by remember { mutableStateOf(false) }
    var selectedEmojiCat by remember { mutableStateOf(0) }

    val layoutRows = remember(language, symbolLayer, shiftState) {
        buildKeyRows(
            language = language,
            symbolLayer = symbolLayer,
            shifted = shiftState != ShiftState.OFF
        )
    }

    // Responsive sizing: fits comfortably in landscape AND respects user keyboardHeightScale
    val accessoryHeight = 36.dp
    val verticalChrome = 16.dp
    val availableForKeys = (maxKeyboardHeight - accessoryHeight - verticalChrome).coerceAtLeast(116.dp)
    val baseKeyHeight = (50.dp * heightScale).coerceIn(30.dp, 60.dp)
    val rowHeight = minOf(baseKeyHeight, (availableForKeys - 18.dp) / 4f).coerceAtLeast(28.dp)
    val isCompact = rowHeight < 40.dp

    fun emitChar(raw: String) {
        val output = if (!symbolLayer && shiftState != ShiftState.OFF && language != KeyboardLanguage.BANGLA_PHONETIC) {
            raw.uppercase()
        } else {
            raw
        }
        onCharKey(output)
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0D))
            .navigationBarsPadding()
    ) {
        // Perforated steel typewriter chassis mesh background (matching uploaded photo)
        Canvas(modifier = Modifier.matchParentSize()) {
            val dotSpacing = 14f
            val dotRadius = 2.1f
            var y = 6f
            while (y < size.height) {
                var x = 6f
                while (x < size.width) {
                    drawCircle(
                        color = Color(0xFF1F2026),
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    x += dotSpacing
                }
                y += dotSpacing
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
        ) {
            // Accessory Bar: Phonetic candidates (when typing Abc->Bangla) OR Quick Gboard-style toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(accessoryHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151519).copy(alpha = 0.94f))
                    .border(0.75.dp, Color(0xFF2D2D34), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (voiceState.isListening) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccessoryPillButton(
                            icon = Icons.Rounded.Stop,
                            label = "Stop",
                            isActive = true,
                            testTag = "kb_voice_stop_button",
                            onClick = onToggleVoiceTyping
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(BrassAmber.copy(alpha = 0.22f))
                                .clickable { onToggleVoiceLanguage() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("kb_voice_lang_chip")
                        ) {
                            Text(
                                text = voiceState.language.shortLabel,
                                color = BrassAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = voiceState.partialText.ifBlank { "Listening (${voiceState.language.displayName})..." },
                            color = Color(0xFFF3EFE6),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (phoneticCandidates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = phoneticBuffer,
                            color = BrassAmber.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        phoneticCandidates.forEachIndexed { idx, candidate ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (idx == 0) BrassAmber.copy(alpha = 0.22f) else Color(0xFF25252C))
                                    .clickable { onSelectCandidate(candidate) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = candidate,
                                    color = if (idx == 0) BrassAmber else Color(0xFFF3EFE6),
                                    fontSize = 13.sp,
                                    fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Bold button
                        AccessoryPillButton(
                            icon = Icons.Rounded.FormatBold,
                            label = "Bold",
                            isActive = isBoldActive,
                            testTag = "kb_bold_button",
                            onClick = onToggleBold
                        )
                        // Emoji button
                        AccessoryPillButton(
                            icon = Icons.Rounded.SentimentSatisfiedAlt,
                            label = "Emoji",
                            isActive = emojiPanelOpen,
                            testTag = "kb_emoji_toggle",
                            onClick = { emojiPanelOpen = !emojiPanelOpen }
                        )
                        // Voice Typing button (Bangla & English)
                        AccessoryPillButton(
                            icon = Icons.Rounded.Mic,
                            label = voiceState.language.shortLabel,
                            isActive = voiceState.isListening,
                            testTag = "kb_voice_button",
                            onClick = onToggleVoiceTyping
                        )
                        // Quick EN <-> বাংলা Voice Language switch chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF222229))
                                .clickable { onToggleVoiceLanguage() }
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                                .testTag("kb_voice_lang_toggle")
                        ) {
                            Text(
                                text = "EN/বাং",
                                color = BrassAmber.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // Language button (like Gboard)
                        AccessoryPillButton(
                            icon = Icons.Rounded.Language,
                            label = language.shortLabel,
                            isActive = language != KeyboardLanguage.ENGLISH,
                            testTag = "kb_language_button",
                            onClick = onOpenLanguagePicker
                        )
                    }
                }

                // Switch to Google Gboard (All system languages / Abc->Bangla Gboard)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF24242C))
                        .clickable { onSwitchToGboard() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("kb_gboard_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Use Google Gboard",
                        tint = BrassAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Gboard",
                        color = Color(0xFFE7E1D6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            if (emojiPanelOpen) {
                // Integrated Emoji Drawer matching the exact keyboard height
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight * 4f + 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141418))
                        .border(0.75.dp, Color(0xFF2B2B33), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EmojiCategories.forEachIndexed { idx, (name, _) ->
                            val selected = selectedEmojiCat == idx
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF111114) else Color(0xFFB5AFA6),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) BrassAmber else Color(0xFF23232A))
                                    .clickable { selectedEmojiCat = idx }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "ABC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrassAmber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { emojiPanelOpen = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EmojiCategories[selectedEmojiCat].second.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF222229))
                                    .clickable { onInsertEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            } else {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    layoutRows.row1.forEach { key ->
                        val capLabel = if (!symbolLayer && language.isLatin) key.uppercase() else key
                        VintageTypewriterKeycap(
                            label = capLabel,
                            resistance = resistance,
                            rowHeight = rowHeight,
                            isCompact = isCompact,
                            weight = 1f,
                            testTag = "key_$key",
                            onPress = { emitChar(key) }
                        )
                    }
                }

                // Row 2 (staggered like vintage typewriter bank)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (layoutRows.row2.size <= 9) 12.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    layoutRows.row2.forEach { key ->
                        val capLabel = if (!symbolLayer && language.isLatin) key.uppercase() else key
                        VintageTypewriterKeycap(
                            label = capLabel,
                            resistance = resistance,
                            rowHeight = rowHeight,
                            isCompact = isCompact,
                            weight = 1f,
                            testTag = "key_$key",
                            onPress = { emitChar(key) }
                        )
                    }
                }

                // Row 3: Shift + Row 3 keys + Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VintageIconKeycap(
                        icon = Icons.Rounded.KeyboardCapslock,
                        contentDescription = "Shift",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isHighlighted = !symbolLayer && shiftState != ShiftState.OFF,
                        accentGlow = shiftState == ShiftState.CAPS,
                        weight = 1.35f,
                        testTag = "key_shift",
                        onPress = {
                            shiftState = when (shiftState) {
                                ShiftState.OFF -> ShiftState.ONCE
                                ShiftState.ONCE -> ShiftState.CAPS
                                ShiftState.CAPS -> ShiftState.OFF
                            }
                        }
                    )
                    layoutRows.row3.forEach { key ->
                        val capLabel = if (!symbolLayer && language.isLatin) key.uppercase() else key
                        VintageTypewriterKeycap(
                            label = capLabel,
                            resistance = resistance,
                            rowHeight = rowHeight,
                            isCompact = isCompact,
                            weight = 1f,
                            testTag = "key_$key",
                            onPress = { emitChar(key) }
                        )
                    }
                    VintageIconKeycap(
                        icon = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isDarkControl = true,
                        repeatOnHold = true,
                        weight = 1.35f,
                        testTag = "key_backspace",
                        onPress = onBackspace
                    )
                }

                // Row 4: 123 | Globe Language | Emoji | Spacebar | Period | Return (NO arrow keys!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VintageTypewriterKeycap(
                        label = if (symbolLayer) "ABC" else "123",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isCompact = isCompact,
                        isDarkControl = true,
                        fontSizeSp = if (isCompact) 11f else 12.5f,
                        weight = 1.15f,
                        testTag = "key_symbols",
                        onPress = { symbolLayer = !symbolLayer }
                    )
                    VintageIconKeycap(
                        icon = Icons.Rounded.Language,
                        contentDescription = "Switch Language",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isDarkControl = true,
                        weight = 0.9f,
                        testTag = "key_globe",
                        onPress = onOpenLanguagePicker
                    )
                    VintageIconKeycap(
                        icon = Icons.Rounded.SentimentSatisfiedAlt,
                        contentDescription = "Emoji",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isDarkControl = true,
                        weight = 0.9f,
                        testTag = "key_emoji",
                        onPress = { emojiPanelOpen = !emojiPanelOpen }
                    )
                    VintageTypewriterKeycap(
                        label = if (language == KeyboardLanguage.BANGLA_PHONETIC) "স্পেস · SPACE" else "SPACE",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isCompact = isCompact,
                        fontSizeSp = if (isCompact) 10f else 11f,
                        weight = 3.6f,
                        testTag = "key_space",
                        onPress = { onCharKey(" ") }
                    )
                    VintageTypewriterKeycap(
                        label = if (language == KeyboardLanguage.BANGLA_DIRECT || language == KeyboardLanguage.BANGLA_PHONETIC) "।" else ".",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        isCompact = isCompact,
                        weight = 0.85f,
                        testTag = "key_period",
                        onPress = {
                            val dot = if (language == KeyboardLanguage.BANGLA_DIRECT || language == KeyboardLanguage.BANGLA_PHONETIC) "।" else "."
                            onCharKey(dot)
                        }
                    )
                    VintageIconKeycap(
                        icon = Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = "Carriage Return",
                        resistance = resistance,
                        rowHeight = rowHeight,
                        accentGlow = true,
                        weight = 1.45f,
                        testTag = "key_return",
                        onPress = { onCharKey("\n") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessoryPillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) BrassAmber.copy(alpha = 0.22f) else Color(0xFF222229))
            .border(
                0.75.dp,
                if (isActive) BrassAmber.copy(alpha = 0.7f) else Color(0xFF33333D),
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) BrassAmber else Color(0xFFE5DFD4),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = if (isActive) BrassAmber else Color(0xFFE5DFD4),
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Vintage cream-ivory chunky rounded-squircle keycap sitting on a metallic stem post,
 * matching the uploaded vintage typewriter close-up image.
 */
@Composable
private fun RowScope.VintageTypewriterKeycap(
    label: String,
    resistance: KeyResistance,
    rowHeight: Dp,
    isCompact: Boolean,
    isDarkControl: Boolean = false,
    fontSizeSp: Float = if (isCompact) 14f else 17f,
    weight: Float = 1f,
    testTag: String,
    onPress: () -> Unit
) {
    var fingerDown by remember { mutableStateOf(false) }
    var tapPulseSeq by remember { mutableLongStateOf(0L) }
    var visualLatched by remember { mutableStateOf(false) }
    LaunchedEffect(tapPulseSeq) {
        if (tapPulseSeq > 0L) {
            visualLatched = true
            delay(75L)
            visualLatched = false
        }
    }
    val isPressed = fingerDown || visualLatched
    val latestOnPress by rememberUpdatedState(onPress)

    val stemHeight = if (isCompact) 4.dp else 6.dp
    val maxTravel = ((rowHeight * 0.20f) + (resistance.pressDepthDp * 0.65f).dp)
        .coerceIn(5.dp, if (isCompact) 7.dp else 10.dp)
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) maxTravel else 0.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = resistance.springStiffness),
        label = "vintage_key_travel"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = resistance.springStiffness),
        label = "vintage_key_scale"
    )

    val skirtHeight = if (isCompact) 5.dp else 7.dp
    val capBodyHeight = (rowHeight - stemHeight).coerceAtLeast(22.dp)

    val topFaceBrush = when {
        isDarkControl && isPressed -> Brush.verticalGradient(listOf(Color(0xFF232329), Color(0xFF1A1A1F)))
        isDarkControl -> Brush.verticalGradient(listOf(Color(0xFF383840), Color(0xFF25252B)))
        isPressed -> Brush.verticalGradient(listOf(Color(0xFFE6DEC9), Color(0xFFD5CCB5), Color(0xFFC9C0A8)))
        else -> Brush.verticalGradient(listOf(Color(0xFFFAF7EE), Color(0xFFEDE6D6), Color(0xFFE4DCC9)))
    }
    val skirtColor = if (isDarkControl) Color(0xFF141418) else Color(0xFFB5AC9E)
    val topRimColor = when {
        isPressed && isDarkControl -> Color(0xFF363640)
        isDarkControl -> Color(0xFF4C4C55)
        isPressed -> Color(0xFFD6CEBC)
        else -> Color(0xFFFFFFFF).copy(alpha = 0.88f)
    }
    val legendColor = if (isDarkControl) Color(0xFFECE6DA) else Color(0xFF141311)

    Box(
        modifier = Modifier
            .weight(weight)
            .height(rowHeight)
            .testTag(testTag)
            .pointerInput(resistance) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    fingerDown = true
                    tapPulseSeq += 1L
                    latestOnPress()
                    waitForUpOrCancellation()
                    fingerDown = false
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Metallic stem post visible underneath the keycap (shortens as key sinks!)
        val visibleStem = (stemHeight + 3.dp - pressOffset * 0.65f).coerceAtLeast(1.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .height(visibleStem)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF202025), Color(0xFF70707C), Color(0xFF1B1B20))
                    )
                )
        )

        // 3D Cylindrical Skirt + Concave Top Face physically sinking down into deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(capBodyHeight)
                .offset(y = pressOffset)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(if (isPressed) 0.5.dp else 5.dp, RoundedCornerShape(11.dp))
                    .clip(RoundedCornerShape(11.dp))
                    .background(skirtColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((capBodyHeight - (if (isPressed) skirtHeight * 0.72f else skirtHeight)).coerceAtLeast(16.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(topFaceBrush)
                    .border(0.8.dp, topRimColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSizeSp.sp,
                    color = legendColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.VintageIconKeycap(
    icon: ImageVector,
    contentDescription: String,
    resistance: KeyResistance,
    rowHeight: Dp,
    isDarkControl: Boolean = false,
    isHighlighted: Boolean = false,
    accentGlow: Boolean = false,
    repeatOnHold: Boolean = false,
    weight: Float = 1f,
    testTag: String,
    onPress: () -> Unit
) {
    var fingerDown by remember { mutableStateOf(false) }
    var tapPulseSeq by remember { mutableLongStateOf(0L) }
    var visualLatched by remember { mutableStateOf(false) }
    val latestOnPress by rememberUpdatedState(onPress)
    LaunchedEffect(tapPulseSeq) {
        if (tapPulseSeq > 0L) {
            visualLatched = true
            delay(75L)
            visualLatched = false
        }
    }
    LaunchedEffect(fingerDown, repeatOnHold) {
        if (fingerDown && repeatOnHold) {
            delay(360L)
            var repeats = 0
            while (fingerDown) {
                latestOnPress()
                repeats++
                delay(if (repeats > 8) 38L else 56L)
            }
        }
    }
    val isPressed = fingerDown || visualLatched

    val isCompact = rowHeight < 40.dp
    val stemHeight = if (isCompact) 4.dp else 6.dp
    val maxTravel = ((rowHeight * 0.20f) + (resistance.pressDepthDp * 0.65f).dp)
        .coerceIn(5.dp, if (isCompact) 7.dp else 10.dp)
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed) maxTravel else 0.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = resistance.springStiffness),
        label = "vintage_icon_travel"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = resistance.springStiffness),
        label = "vintage_icon_scale"
    )

    val skirtHeight = if (isCompact) 5.dp else 7.dp
    val capBodyHeight = (rowHeight - stemHeight).coerceAtLeast(22.dp)

    val topBrush = when {
        accentGlow && isPressed -> Brush.verticalGradient(listOf(Color(0xFFD09E48), Color(0xFFA87B2D)))
        accentGlow -> Brush.verticalGradient(listOf(BrassAmber, Color(0xFFC59342)))
        isHighlighted -> Brush.verticalGradient(listOf(Color(0xFFFCE6BC), Color(0xFFE7C484)))
        isDarkControl && isPressed -> Brush.verticalGradient(listOf(Color(0xFF232329), Color(0xFF1A1A1F)))
        isDarkControl -> Brush.verticalGradient(listOf(Color(0xFF383840), Color(0xFF25252B)))
        isPressed -> Brush.verticalGradient(listOf(Color(0xFFE6DEC9), Color(0xFFD2C8B1)))
        else -> Brush.verticalGradient(listOf(Color(0xFFFAF7EE), Color(0xFFE8E1D0)))
    }
    val skirtColor = when {
        accentGlow -> Color(0xFF7B571D)
        isDarkControl -> Color(0xFF141418)
        else -> Color(0xFFB5AC9E)
    }
    val tint = when {
        accentGlow -> Color(0xFF15120D)
        isHighlighted -> Color(0xFF1A160E)
        isDarkControl -> Color(0xFFECE6DA)
        else -> Color(0xFF141311)
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(rowHeight)
            .testTag(testTag)
            .pointerInput(resistance) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    fingerDown = true
                    tapPulseSeq += 1L
                    latestOnPress()
                    waitForUpOrCancellation()
                    fingerDown = false
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val visibleStem = (stemHeight + 3.dp - pressOffset * 0.65f).coerceAtLeast(1.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .height(visibleStem)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF202025), Color(0xFF70707C), Color(0xFF1B1B20))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(capBodyHeight)
                .offset(y = pressOffset)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(if (isPressed) 0.5.dp else 5.dp, RoundedCornerShape(11.dp))
                    .clip(RoundedCornerShape(11.dp))
                    .background(skirtColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((capBodyHeight - (if (isPressed) skirtHeight * 0.72f else skirtHeight)).coerceAtLeast(16.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(topBrush)
                    .border(
                        0.8.dp,
                        if (isDarkControl && !accentGlow) Color(0xFF4C4C55) else Color.White.copy(alpha = 0.7f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(if (isCompact) 15.dp else 18.dp)
                )
            }
        }
    }
}

private data class KeyboardLayoutRows(
    val row1: List<String>,
    val row2: List<String>,
    val row3: List<String>
)

private fun buildKeyRows(
    language: KeyboardLanguage,
    symbolLayer: Boolean,
    shifted: Boolean
): KeyboardLayoutRows {
    if (symbolLayer) {
        return KeyboardLayoutRows(
            row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
            row3 = listOf(",", "?", "!", "'", "*", "#", "%")
        )
    }
    return when (language) {
        KeyboardLanguage.ENGLISH, KeyboardLanguage.BANGLA_PHONETIC -> {
            val r1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            val r2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
            val r3 = listOf("Z", "X", "C", "V", "B", "N", "M")
            val transform: (String) -> String = { if (shifted) it else if (language == KeyboardLanguage.BANGLA_PHONETIC) it.lowercase() else it }
            // Keep uppercase keycap legends on vintage keys like the uploaded photo, emit lowercase unless shifted
            KeyboardLayoutRows(
                row1 = if (shifted) r1 else r1.map { it.lowercase() },
                row2 = if (shifted) r2 else r2.map { it.lowercase() },
                row3 = if (shifted) r3 else r3.map { it.lowercase() }
            )
        }
        KeyboardLanguage.BANGLA_DIRECT -> if (!shifted) {
            KeyboardLayoutRows(
                row1 = listOf("া", "ি", "ু", "ে", "ো", "ক", "খ", "গ", "ঘ", "চ"),
                row2 = listOf("ত", "থ", "দ", "ধ", "ন", "প", "ফ", "ব", "ভ"),
                row3 = listOf("ম", "য", "র", "ল", "শ", "স", "হ")
            )
        } else {
            KeyboardLayoutRows(
                row1 = listOf("অ", "আ", "ই", "ঈ", "উ", "এ", "ও", "ঙ", "ছ", "জ"),
                row2 = listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ী", "ূ", "ৈ", "ৌ"),
                row3 = listOf("ষ", "ড়", "য়", "ৎ", "ং", "ঁ", "্")
            )
        }
        KeyboardLanguage.HINDI -> if (!shifted) {
            KeyboardLayoutRows(
                row1 = listOf("ा", "ि", "ु", "े", "ो", "क", "ख", "ग", "घ", "च"),
                row2 = listOf("त", "थ", "द", "ध", "न", "प", "फ", "ब", "भ"),
                row3 = listOf("म", "य", "र", "ल", "व", "श", "स")
            )
        } else {
            KeyboardLayoutRows(
                row1 = listOf("अ", "आ", "इ", "ई", "उ", "ए", "ओ", "ज", "ट", "ठ"),
                row2 = listOf("ड", "ढ", "ण", "ी", "ू", "ै", "ौ", "ह", "ं"),
                row3 = listOf("ष", "ड़", "त्र", "ज्ञ", "क्ष", "्", "ः")
            )
        }
        KeyboardLanguage.ARABIC -> KeyboardLayoutRows(
            row1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"),
            row2 = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م"),
            row3 = listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة")
        )
        KeyboardLanguage.SPANISH -> KeyboardLayoutRows(
            row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { if (shifted) it.uppercase() else it },
            row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ").map { if (shifted) it.uppercase() else it },
            row3 = listOf("z", "x", "c", "v", "b", "n", "m").map { if (shifted) it.uppercase() else it }
        )
        KeyboardLanguage.FRENCH -> KeyboardLayoutRows(
            row1 = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p").map { if (shifted) it.uppercase() else it },
            row2 = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m").map { if (shifted) it.uppercase() else it },
            row3 = listOf("w", "x", "c", "v", "b", "n", "é").map { if (shifted) it.uppercase() else it }
        )
        KeyboardLanguage.GERMAN -> KeyboardLayoutRows(
            row1 = listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p").map { if (shifted) it.uppercase() else it },
            row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ü").map { if (shifted) it.uppercase() else it },
            row3 = listOf("y", "x", "c", "v", "b", "n", "m").map { if (shifted) it.uppercase() else it }
        )
    }
}
