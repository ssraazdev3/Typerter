package com.example.ui.editor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.EditorPreferences
import com.example.data.model.PageColorPreset
import com.example.data.model.ThemeMode
import com.example.ui.keyboard.KeyboardLanguage
import com.example.ui.keyboard.MechanicalKeyboard
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.DarkDeskBackground
import com.example.ui.theme.LightStudioBackground
import com.example.ui.theme.PlatenDark
import com.example.ui.theme.PlatenHighlight
import com.example.ui.theme.toComposeFontFamily
import com.example.ui.viewmodel.EditorFocusField
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.KeystrokeViewModel
import com.example.ui.viewmodel.LeverStrikeEvent
import com.example.ui.viewmodel.SessionStats
import com.example.voice.VoiceTypingEngine
import com.example.voice.VoiceTypingLanguage
import com.example.voice.VoiceTypingState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 1:1 VisualTransformation rendering `**bold**` Markdown spans in bold weight
 * without altering character offsets for cursor/selection/Gboard IME composition.
 */
private class BoldMarkdownVisualTransformation(
    private val markerColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            var idx = 0
            while (idx < raw.length - 1) {
                val open = raw.indexOf("**", idx)
                if (open < 0) break
                val close = raw.indexOf("**", open + 2)
                if (close < 0) break
                addStyle(SpanStyle(color = markerColor, fontWeight = FontWeight.Light), open, open + 2)
                if (close > open + 2) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), open + 2, close)
                }
                addStyle(SpanStyle(color = markerColor, fontWeight = FontWeight.Light), close, close + 2)
                idx = close + 2
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val QuickSysEmojis = listOf("😊", "✨", "❤️", "📝", "🔥", "🌙", "☕", "👍", "🙏", "🎉", "—", "…")

@Composable
fun TypewriterEditorScreen(
    editorState: EditorUiState,
    preferences: EditorPreferences,
    sessionStats: SessionStats,
    leverStrike: LeverStrikeEvent,
    onBackToFolio: () -> Unit,
    onSetActiveField: (EditorFocusField) -> Unit,
    onTitleValueChange: (TextFieldValue) -> Unit,
    onTitleSelectionChange: (TextRange) -> Unit,
    onSystemTextChange: (TextFieldValue) -> Unit,
    onMechanicalChar: (String) -> Unit,
    onMechanicalBackspace: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onToggleBold: () -> Unit,
    onPlaceCarriageAt: (Int, Int) -> Unit,
    onMoveCursorOffset: (Int) -> Unit,
    onUpdateSelection: (TextRange) -> Unit,
    onSelectWord: () -> Unit,
    onSelectSentence: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearDocument: () -> Unit,
    onOpenSheet: (ActiveEditorSheet) -> Unit,
    onToggleKeyboardMode: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onSwitchToGboard: () -> Unit,
    onVoiceDictatedText: (String) -> Unit,
    onUpdateVoiceLanguage: (VoiceTypingLanguage) -> Unit,
    onResizeKeyboardScale: (Float) -> Unit,
    onSuppressAutoLock: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkRoom = preferences.themeMode == ThemeMode.DARK_DESK || preferences.focusMode
    val pagePreset = remember(preferences.darkPaperMode, preferences.pageColorId) {
        if (preferences.darkPaperMode) PageColorPreset.CHARCOAL_DARK
        else PageColorPreset.fromId(preferences.pageColorId)
    }
    val isDarkPaper = pagePreset.isDark
    val paperBase = Color(pagePreset.paperHex)
    val inkColor = Color(pagePreset.inkHex)
    val roomBackground = when {
        preferences.focusMode -> Color(0xFF040406)
        isDarkRoom || isDarkPaper -> DarkDeskBackground
        else -> LightStudioBackground
    }

    val paperScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var paperViewportHeightPx by remember { mutableFloatStateOf(0f) }
    var paperViewportWidthPx by remember { mutableFloatStateOf(1f) }
    var titleBlockHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val systemFocusRequester = remember { FocusRequester() }
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectionToolbarPinned by remember { mutableStateOf(false) }
    var confirmClearDoc by remember { mutableStateOf(false) }
    var sysEmojiOpen by remember { mutableStateOf(false) }
    var isResizingKeyboard by remember { mutableStateOf(false) }

    val voiceLanguage = remember(preferences.voiceLanguageTag) {
        VoiceTypingLanguage.fromTag(preferences.voiceLanguageTag)
    }
    var voiceState by remember(voiceLanguage) {
        mutableStateOf(VoiceTypingState(language = voiceLanguage))
    }
    val currentVoiceDictationCallback by rememberUpdatedState(onVoiceDictatedText)
    val currentVoiceLanguage by rememberUpdatedState(voiceLanguage)

    val voiceEngine = remember(context) {
        VoiceTypingEngine(
            context = context,
            onPartialResult = { partial ->
                voiceState = voiceState.copy(partialText = partial, errorMessage = null)
            },
            onFinalResult = { finalText ->
                voiceState = voiceState.copy(partialText = "")
                currentVoiceDictationCallback(finalText)
            },
            onRmsChanged = { rms ->
                voiceState = voiceState.copy(rmsDb = rms)
            },
            onStatusChanged = { listening, error ->
                voiceState = voiceState.copy(
                    isListening = listening,
                    partialText = if (listening) voiceState.partialText else "",
                    errorMessage = error
                )
            }
        )
    }

    DisposableEffect(voiceEngine) {
        onDispose { voiceEngine.destroy() }
    }

    val speechFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        voiceState = voiceState.copy(isListening = false, partialText = "")
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val best = matches?.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                val normalized = VoiceTypingEngine.normalizeVoiceText(best, currentVoiceLanguage)
                currentVoiceDictationCallback(normalized)
            }
        }
    }

    fun startVoiceRecognitionNow() {
        val lang = currentVoiceLanguage
        voiceState = voiceState.copy(language = lang, errorMessage = null)
        val started = voiceEngine.startListening(lang)
        if (!started) {
            val intent = voiceEngine.createGoogleRecognitionIntent(lang)
            if (intent.resolveActivity(context.packageManager) != null) {
                onSuppressAutoLock()
                speechFallbackLauncher.launch(intent)
            } else {
                Toast.makeText(
                    context,
                    "Google Speech Recognition is unavailable on this device.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognitionNow()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice typing.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleVoiceTyping() {
        if (voiceState.isListening) {
            voiceEngine.stopListening()
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            startVoiceRecognitionNow()
        } else {
            onSuppressAutoLock()
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleVoiceLanguage() {
        val next = if (voiceLanguage == VoiceTypingLanguage.ENGLISH) {
            VoiceTypingLanguage.BANGLA
        } else {
            VoiceTypingLanguage.ENGLISH
        }
        onUpdateVoiceLanguage(next)
        voiceState = voiceState.copy(language = next)
        if (voiceState.isListening) {
            voiceEngine.stopListening()
            voiceEngine.startListening(next)
        }
    }

    val hasSelection = !editorState.content.selection.collapsed
    val showSelectionTools = selectionToolbarPinned || hasSelection

    // Explicit blinking cursor state for both Document Title and Body
    var cursorBlinkVisible by remember { mutableStateOf(true) }
    LaunchedEffect(
        editorState.content.selection,
        editorState.content.text.length,
        editorState.titleSelection,
        editorState.title.length,
        editorState.activeField,
        preferences.showTypingCursor
    ) {
        if (!preferences.showTypingCursor) {
            cursorBlinkVisible = false
            return@LaunchedEffect
        }
        cursorBlinkVisible = true
        while (true) {
            delay(520L)
            cursorBlinkVisible = !cursorBlinkVisible
        }
    }

    // Key lever mechanical swing animation (smooth dark-metallic arm without bright flashing halo)
    val leverProgress = remember { Animatable(0f) }
    LaunchedEffect(leverStrike.sequence) {
        if (leverStrike.sequence > 0L && preferences.leverAnimationEnabled) {
            leverProgress.snapTo(0.18f)
            leverProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 56, easing = FastOutSlowInEasing)
            )
            leverProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
            )
        }
    }

    // Smoothly interpolated spotlight controls
    val smoothBeamWidth by animateFloatAsState(
        targetValue = preferences.spotlightWidth.coerceIn(0.22f, 1.0f),
        animationSpec = tween(durationMillis = 280),
        label = "smooth_beam_width"
    )
    val smoothDimStrength by animateFloatAsState(
        targetValue = preferences.spotlightDim.coerceIn(0.10f, 0.95f),
        animationSpec = tween(durationMillis = 280),
        label = "smooth_dim_strength"
    )
    val smoothLightDepth by animateFloatAsState(
        targetValue = preferences.spotlightDepth.coerceIn(0.25f, 1.0f),
        animationSpec = tween(durationMillis = 280),
        label = "smooth_light_depth"
    )

    val (cursorRow, cursorCol) = remember(editorState.content.text, editorState.content.selection) {
        KeystrokeViewModel.computeLineColForOffset(
            editorState.content.text,
            editorState.content.selection.start
        )
    }

    val isBoldActive = remember(editorState.content.text, editorState.content.selection) {
        KeystrokeViewModel.isInsideBoldSpan(
            editorState.content.text,
            editorState.content.selection.start
        )
    }

    val currentLanguage = remember(preferences.keyboardLanguageId) {
        KeyboardLanguage.fromId(preferences.keyboardLanguageId)
    }

    val boldTransformation = remember(inkColor) {
        BoldMarkdownVisualTransformation(markerColor = inkColor.copy(alpha = 0.28f))
    }

    val selectionColors = remember(inkColor) {
        TextSelectionColors(
            handleColor = BrassAmber,
            backgroundColor = BrassAmber.copy(alpha = 0.30f)
        )
    }

    if (confirmClearDoc) {
        AlertDialog(
            onDismissRequest = { confirmClearDoc = false },
            title = { Text("Delete Whole Document?") },
            text = { Text("This will clear all text on this page so you can start fresh.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearDoc = false
                        selectionToolbarPinned = false
                        onClearDocument()
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDoc = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(roomBackground)
            .statusBarsPadding()
            .then(if (!preferences.useMechanicalKeyboard) Modifier.imePadding() else Modifier)
    ) {
        val isLandscape = maxWidth > maxHeight || maxHeight < 500.dp
        val outerPaperTopPad = when {
            isLandscape && preferences.focusMode -> 4.dp
            isLandscape -> 36.dp
            preferences.focusMode -> 10.dp
            else -> 46.dp
        }
        val innerPaperTopPad = if (isLandscape) 20.dp else 72.dp
        val innerPaperBottomPad = if (isLandscape) 34.dp else 92.dp
        val innerPaperSidePad = if (isLandscape) 24.dp else 30.dp

        val maxKbHeight = if (isLandscape) {
            (maxHeight * 0.45f).coerceIn(132.dp, 210.dp)
        } else {
            (maxHeight * 0.46f).coerceIn(220.dp, 360.dp)
        }

        // Typewriter paper feed scroll keeping the active cursor line comfortably visible above platen
        LaunchedEffect(
            editorState.content.selection.start,
            editorState.content.text.length,
            preferences.typewriterScroll,
            paperViewportHeightPx,
            isLandscape
        ) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            if (!preferences.typewriterScroll || paperViewportHeightPx <= 0f) return@LaunchedEffect
            val offset = editorState.content.selection.start.coerceIn(0, editorState.content.text.length)
            val cursorRect = try {
                layout.getCursorRect(offset)
            } catch (_: Exception) {
                return@LaunchedEffect
            }
            val topPadPx = with(density) { innerPaperTopPad.toPx() }
            val anchorRatio = if (isLandscape) 0.68f else 0.72f
            val desiredAnchorY = (paperViewportHeightPx * anchorRatio).coerceAtLeast(80f)
            val absoluteCursorBottom = topPadPx + titleBlockHeightPx + cursorRect.bottom
            val targetScroll = (absoluteCursorBottom - desiredAnchorY).toInt().coerceAtLeast(0)
            paperScrollState.animateScrollTo(targetScroll)
        }

        val carriageXFraction = remember(editorState.content.selection, textLayoutResult, editorState.content.text) {
            val layout = textLayoutResult
            if (layout == null || layout.size.width <= 0) {
                (cursorCol / 52f).coerceIn(0.05f, 0.95f)
            } else {
                val offset = editorState.content.selection.start.coerceIn(0, editorState.content.text.length)
                try {
                    val rect = layout.getCursorRect(offset)
                    (rect.left / layout.size.width.toFloat()).coerceIn(0.04f, 0.96f)
                } catch (_: Exception) {
                    0.5f
                }
            }
        }

        val strikePointInPaper = remember(
            editorState.content.selection,
            textLayoutResult,
            paperScrollState.value,
            titleBlockHeightPx,
            paperViewportWidthPx,
            paperViewportHeightPx,
            isLandscape
        ) {
            val layout = textLayoutResult
            val sidePx = with(density) { innerPaperSidePad.toPx() }
            val topPx = with(density) { innerPaperTopPad.toPx() }
            if (layout != null && paperViewportHeightPx > 0f) {
                val offset = editorState.content.selection.start.coerceIn(0, editorState.content.text.length)
                try {
                    val rect = layout.getCursorRect(offset)
                    val x = (sidePx + rect.left).coerceIn(sidePx, (paperViewportWidthPx - sidePx).coerceAtLeast(sidePx + 10f))
                    val y = (topPx + titleBlockHeightPx + rect.bottom - paperScrollState.value).coerceIn(24f, paperViewportHeightPx - 12f)
                    Offset(x, y)
                } catch (_: Exception) {
                    Offset(paperViewportWidthPx * carriageXFraction, paperViewportHeightPx * 0.7f)
                }
            } else {
                Offset(paperViewportWidthPx * carriageXFraction, paperViewportHeightPx * 0.7f)
            }
        }

        // Smoothly glide the Focus Mode fog-light beam from the typing toggle bar to the active typing word (no flicker!)
        val smoothFocusTypingY by animateFloatAsState(
            targetValue = strikePointInPaper.y.coerceIn(
                (paperViewportHeightPx * 0.24f).coerceAtLeast(60f),
                (paperViewportHeightPx * 0.86f).coerceAtLeast(90f)
            ),
            animationSpec = tween(durationMillis = 340),
            label = "focus_typing_y"
        )
        val smoothFocusWordX by animateFloatAsState(
            targetValue = strikePointInPaper.x.coerceIn(
                (paperViewportWidthPx * 0.12f),
                (paperViewportWidthPx * 0.88f).coerceAtLeast(100f)
            ),
            animationSpec = tween(durationMillis = 380),
            label = "focus_word_x"
        )
        val smoothBarOriginX by animateFloatAsState(
            targetValue = (paperViewportWidthPx * carriageXFraction.coerceIn(0.18f, 0.82f)),
            animationSpec = tween(durationMillis = 420),
            label = "focus_bar_origin_x"
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Upper Stage: Original Unoffset Textured Paper + Focus Spotlight + Blinking Cursor + Top Icon Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Smooth outer desk ambient glow behind paper
                if ((isDarkRoom || isDarkPaper) && preferences.spotlightEnabled) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BrassAmber.copy(alpha = (0.04f + smoothBeamWidth * 0.07f)),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.76f),
                                radius = size.maxDimension * (0.40f + smoothBeamWidth * 0.38f)
                            )
                        )
                    }
                }

                // Original Unoffset Bond Paper Sheet Container
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(preferences.paperWidthFraction)
                        .widthIn(max = 760.dp)
                        .padding(top = outerPaperTopPad)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                            ambientColor = Color.Black,
                            spotColor = BrassAmber.copy(alpha = 0.20f)
                        )
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(paperBase)
                        .border(
                            width = 0.8.dp,
                            color = inkColor.copy(alpha = if (isDarkPaper) 0.14f else 0.10f),
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        )
                        .onSizeChanged {
                            paperViewportWidthPx = it.width.toFloat().coerceAtLeast(1f)
                            paperViewportHeightPx = it.height.toFloat()
                        }
                ) {
                    // Authentic Unoffset Uncoated Paper Texture (cotton fiber grain + fine margin rule + soft sheet bevel)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val grainAlpha = if (isDarkPaper) 0.025f else 0.038f
                        val marginLineColor = if (isDarkPaper) BrassAmber.copy(alpha = 0.12f) else Color(0xFFB55D52).copy(alpha = 0.16f)

                        // Subtle horizontal paper mill-grain fibers
                        var fy = 18f
                        var rowIdx = 0
                        while (fy < size.height) {
                            drawLine(
                                color = inkColor.copy(alpha = if (rowIdx % 2 == 0) grainAlpha * 0.5f else grainAlpha * 0.28f),
                                start = Offset(0f, fy),
                                end = Offset(size.width, fy),
                                strokeWidth = 0.8f
                            )
                            fy += 28f
                            rowIdx++
                        }

                        // Subtle cotton fiber speckles across sheet
                        val stepX = 44f
                        val stepY = 44f
                        var y = 16f
                        var seed = 17
                        while (y < size.height) {
                            var x = 16f
                            while (x < size.width) {
                                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                                val jx = x + ((seed % 21) - 10)
                                val jy = y + (((seed shr 8) % 21) - 10)
                                drawCircle(
                                    color = inkColor.copy(alpha = grainAlpha),
                                    radius = if ((seed and 3) == 0) 1.3f else 0.85f,
                                    center = Offset(jx, jy)
                                )
                                x += stepX
                            }
                            y += stepY
                        }

                        // Classic unoffset manuscript margin rule on left
                        val marginX = 20.dp.toPx()
                        drawLine(
                            color = marginLineColor,
                            start = Offset(marginX, 0f),
                            end = Offset(marginX, size.height),
                            strokeWidth = 1.2f
                        )
                    }

                    val paperFontFamily = editorState.font.toComposeFontFamily()
                    val fontSize = preferences.fontSizeSp.sp
                    val lineHeight = (preferences.fontSizeSp * preferences.lineHeightMultiplier).sp
                    val lineHeightPx = with(density) { lineHeight.toPx() }
                    val topPaddingPx = with(density) { innerPaperTopPad.toPx() }
                    val horizontalPadPx = with(density) { innerPaperSidePad.toPx() }
                    val approxCharWidthPx = with(density) { (preferences.fontSizeSp * 0.58f).sp.toPx() }

                    // Scrollable Paper Content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(paperScrollState)
                            .pointerInput(
                                preferences.touchToEditEnabled,
                                preferences.freeCursorPlacement,
                                preferences.useMechanicalKeyboard,
                                editorState.content.text,
                                lineHeightPx,
                                titleBlockHeightPx
                            ) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        onSetActiveField(EditorFocusField.BODY)
                                        if (!preferences.useMechanicalKeyboard) {
                                            systemFocusRequester.requestFocus()
                                            keyboardController?.show()
                                        }
                                        if (!preferences.touchToEditEnabled) {
                                            return@detectTapGestures
                                        }
                                        val layout = textLayoutResult ?: return@detectTapGestures
                                        val localY = (tapOffset.y - topPaddingPx - titleBlockHeightPx).coerceAtLeast(0f)
                                        val localX = (tapOffset.x - horizontalPadPx).coerceAtLeast(0f)
                                        if (preferences.freeCursorPlacement) {
                                            val tappedRow = (localY / lineHeightPx).toInt().coerceAtLeast(0)
                                            val lines = editorState.content.text.split("\n")
                                            if (tappedRow >= lines.size) {
                                                val targetCol = (localX / approxCharWidthPx).roundToInt().coerceAtLeast(0)
                                                onPlaceCarriageAt(tappedRow, targetCol)
                                            } else {
                                                val clampedRow = tappedRow.coerceIn(0, layout.lineCount - 1)
                                                val lineStart = layout.getLineStart(clampedRow)
                                                val lineEnd = layout.getLineEnd(clampedRow, visibleEnd = true)
                                                val lineRight = layout.getLineRight(clampedRow)
                                                if (localX > lineRight + approxCharWidthPx * 1.5f) {
                                                    val extraCols = ((localX - lineRight) / approxCharWidthPx).roundToInt()
                                                    val rowLen = lines.getOrNull(tappedRow)?.length ?: 0
                                                    onPlaceCarriageAt(tappedRow, rowLen + extraCols)
                                                } else {
                                                    val offset = layout.getOffsetForPosition(Offset(localX, localY))
                                                    onMoveCursorOffset(offset.coerceIn(lineStart, lineEnd.coerceAtLeast(lineStart)))
                                                }
                                            }
                                        } else {
                                            onMoveCursorOffset(layout.getOffsetForPosition(Offset(localX, localY)))
                                        }
                                    },
                                    onLongPress = { tapOffset ->
                                        onSetActiveField(EditorFocusField.BODY)
                                        val layout = textLayoutResult ?: return@detectTapGestures
                                        val localY = (tapOffset.y - topPaddingPx - titleBlockHeightPx).coerceAtLeast(0f)
                                        val localX = (tapOffset.x - horizontalPadPx).coerceAtLeast(0f)
                                        val offset = layout.getOffsetForPosition(Offset(localX, localY))
                                        onMoveCursorOffset(offset)
                                        onSelectWord()
                                    }
                                )
                            }
                            .padding(
                                start = innerPaperSidePad,
                                end = innerPaperSidePad,
                                top = innerPaperTopPad,
                                bottom = innerPaperBottomPad
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Document Title: Keeps current Mechanical Keyboard style without opening system keyboard!
                            if (!preferences.focusMode) {
                                val titleFieldVal = remember(editorState.title, editorState.titleSelection) {
                                    TextFieldValue(
                                        text = editorState.title,
                                        selection = editorState.titleSelection
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { titleBlockHeightPx = it.height.toFloat() }
                                        .padding(bottom = if (isLandscape) 6.dp else 12.dp)
                                ) {
                                    BasicTextField(
                                        value = titleFieldVal,
                                        onValueChange = { next ->
                                            if (!preferences.useMechanicalKeyboard) {
                                                onTitleValueChange(next)
                                            } else if (next.selection != titleFieldVal.selection) {
                                                onTitleSelectionChange(next.selection)
                                            }
                                        },
                                        readOnly = preferences.useMechanicalKeyboard,
                                        singleLine = true,
                                        onTextLayout = { titleLayoutResult = it },
                                        textStyle = TextStyle(
                                            fontFamily = paperFontFamily,
                                            fontSize = (preferences.fontSizeSp + 3f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = inkColor.copy(alpha = 0.88f),
                                            letterSpacing = 0.3.sp
                                        ),
                                        cursorBrush = SolidColor(
                                            if (preferences.showTypingCursor &&
                                                !preferences.useMechanicalKeyboard &&
                                                editorState.activeField == EditorFocusField.TITLE
                                            ) inkColor else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(titleFocusRequester)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    onSetActiveField(EditorFocusField.TITLE)
                                                }
                                            }
                                            .pointerInput(preferences.useMechanicalKeyboard, editorState.title) {
                                                if (preferences.useMechanicalKeyboard) {
                                                    detectTapGestures { tapOffset ->
                                                        onSetActiveField(EditorFocusField.TITLE)
                                                        val layout = titleLayoutResult
                                                        if (layout != null) {
                                                            val off = layout.getOffsetForPosition(tapOffset)
                                                            onTitleSelectionChange(TextRange(off))
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("editor_title_input"),
                                        decorationBox = { inner ->
                                            if (editorState.title.isEmpty()) {
                                                Text(
                                                    text = "Document Title...",
                                                    fontFamily = paperFontFamily,
                                                    fontSize = (preferences.fontSizeSp + 3f).sp,
                                                    color = inkColor.copy(alpha = 0.30f)
                                                )
                                            }
                                            inner()
                                        }
                                    )

                                    // Blinking Cursor inside Title when Mechanical Keyboard is active on Title
                                    if (preferences.showTypingCursor &&
                                        preferences.useMechanicalKeyboard &&
                                        editorState.activeField == EditorFocusField.TITLE
                                    ) {
                                        val titleLayout = titleLayoutResult
                                        val tRect = remember(titleLayout, editorState.titleSelection.start, editorState.title.length) {
                                            if (titleLayout == null) {
                                                Rect(0f, 2f, 2f, with(density) { (preferences.fontSizeSp + 3f).sp.toPx() * 1.1f })
                                            } else {
                                                val off = editorState.titleSelection.start.coerceIn(0, editorState.title.length)
                                                try {
                                                    titleLayout.getCursorRect(off)
                                                } catch (_: Exception) {
                                                    Rect(0f, 2f, 2f, with(density) { (preferences.fontSizeSp + 3f).sp.toPx() * 1.1f })
                                                }
                                            }
                                        }
                                        val caretAlpha = if (cursorBlinkVisible) 0.92f else 0.12f
                                        Canvas(modifier = Modifier.matchParentSize()) {
                                            drawLine(
                                                color = (if (isDarkPaper) BrassAmber else inkColor).copy(alpha = caretAlpha),
                                                start = Offset(tRect.left.coerceAtLeast(1f), tRect.top + 2f),
                                                end = Offset(tRect.left.coerceAtLeast(1f), (tRect.bottom - 2f).coerceAtLeast(tRect.top + 16f)),
                                                strokeWidth = 3.2f,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            } else {
                                LaunchedEffect(Unit) { titleBlockHeightPx = 0f }
                            }

                            // Main Paper Body TextField
                            Box(modifier = Modifier.fillMaxWidth()) {
                                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                                    BasicTextField(
                                        value = editorState.content,
                                        onValueChange = { next ->
                                            if (!preferences.useMechanicalKeyboard) {
                                                val effective = if (preferences.touchToEditEnabled ||
                                                    !next.selection.collapsed ||
                                                    next.text != editorState.content.text
                                                ) {
                                                    next
                                                } else {
                                                    next.copy(selection = editorState.content.selection)
                                                }
                                                onSystemTextChange(effective)
                                            } else if (next.selection != editorState.content.selection) {
                                                onSetActiveField(EditorFocusField.BODY)
                                                if (!next.selection.collapsed || preferences.touchToEditEnabled) {
                                                    onUpdateSelection(next.selection)
                                                }
                                            }
                                        },
                                        readOnly = preferences.useMechanicalKeyboard,
                                        visualTransformation = boldTransformation,
                                        onTextLayout = { textLayoutResult = it },
                                        textStyle = TextStyle(
                                            fontFamily = paperFontFamily,
                                            fontSize = fontSize,
                                            lineHeight = lineHeight,
                                            letterSpacing = preferences.letterSpacingEm.sp,
                                            color = inkColor
                                        ),
                                        cursorBrush = SolidColor(
                                            if (preferences.showTypingCursor &&
                                                !preferences.useMechanicalKeyboard &&
                                                editorState.activeField == EditorFocusField.BODY
                                            ) inkColor else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(systemFocusRequester)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    onSetActiveField(EditorFocusField.BODY)
                                                }
                                            }
                                            .testTag("editor_body_input"),
                                        decorationBox = { inner ->
                                            if (editorState.content.text.isEmpty()) {
                                                Text(
                                                    text = if (isLandscape) "Start typing..." else "Start typing on the unoffset sheet...",
                                                    fontFamily = paperFontFamily,
                                                    fontSize = fontSize,
                                                    lineHeight = lineHeight,
                                                    color = inkColor.copy(alpha = 0.32f)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                }

                                // Explicit Blinking Typewriter Cursor on Body when Mechanical Keyboard is active
                                if (preferences.showTypingCursor &&
                                    preferences.useMechanicalKeyboard &&
                                    editorState.activeField == EditorFocusField.BODY &&
                                    editorState.content.selection.collapsed
                                ) {
                                    val layout = textLayoutResult
                                    val cursorRect = remember(
                                        layout,
                                        editorState.content.selection.start,
                                        editorState.content.text.length
                                    ) {
                                        if (layout == null) {
                                            Rect(0f, 2f, 2f, with(density) { fontSize.toPx() * 1.15f })
                                        } else {
                                            val offset = editorState.content.selection.start.coerceIn(0, editorState.content.text.length)
                                            try {
                                                layout.getCursorRect(offset)
                                            } catch (_: Exception) {
                                                Rect(0f, 2f, 2f, with(density) { fontSize.toPx() * 1.15f })
                                            }
                                        }
                                    }
                                    val caretAlpha = if (cursorBlinkVisible) 0.94f else 0.10f
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val caretTop = cursorRect.top + 2f
                                        val caretBottom = (cursorRect.bottom - 2f).coerceAtLeast(caretTop + 14f)
                                        drawLine(
                                            color = (if (isDarkPaper) BrassAmber else inkColor).copy(alpha = caretAlpha),
                                            start = Offset(cursorRect.left.coerceAtLeast(1f), caretTop),
                                            end = Offset(cursorRect.left.coerceAtLeast(1f), caretBottom),
                                            strokeWidth = 3.2f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Car Fog-Light Focus Beam:
                    // - Focuses from the typing toggle bar upward onto the active typing word while keeping the rest of the page deeply dimmed.
                    if (preferences.focusMode) {
                        val shade = if (isDarkPaper) Color(0xFF020204) else Color(0xFF050508)
                        val outerAlpha = (0.58f + smoothLightDepth * 0.37f).coerceIn(0.55f, 0.96f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        ) {
                            val w = size.width.coerceAtLeast(1f)
                            val h = size.height.coerceAtLeast(1f)
                            val originX = smoothBarOriginX.coerceIn(w * 0.14f, w * 0.86f)
                            val targetX = smoothFocusWordX.coerceIn(w * 0.10f, w * 0.90f)
                            val focusY = smoothFocusTypingY.coerceIn(h * 0.22f, h * 0.88f)
                            val baseHalf = w * (0.10f + smoothBeamWidth * 0.10f)
                            val wordHalf = w * (0.20f + smoothBeamWidth * 0.32f)
                            val beamTopY = (focusY - h * (0.14f + smoothBeamWidth * 0.14f)).coerceAtLeast(0f)

                            // 1. Deep darkness across the entire paper
                            drawRect(color = shade.copy(alpha = outerAlpha))

                            // 2. Carve out layered conical Car Fog-Light beam from typing toggle bar -> typing word
                            val coneLayers = listOf(
                                Triple(1.36f, 0.36f, beamTopY - h * 0.04f),
                                Triple(1.14f, 0.62f, beamTopY),
                                Triple(0.92f, 0.96f, focusY - h * 0.06f)
                            )
                            coneLayers.forEach { (spreadMult, layerStrength, topLimitY) ->
                                val clampedTop = topLimitY.coerceIn(0f, focusY - 12f)
                                val conePath = Path().apply {
                                    moveTo(originX - baseHalf * spreadMult, h)
                                    lineTo(targetX - wordHalf * spreadMult, clampedTop)
                                    lineTo(targetX + wordHalf * spreadMult, clampedTop)
                                    lineTo(originX + baseHalf * spreadMult, h)
                                    close()
                                }
                                drawPath(
                                    path = conePath,
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.35f to Color.Black.copy(alpha = layerStrength * 0.88f),
                                            0.82f to Color.Black.copy(alpha = layerStrength),
                                            1.0f to Color.Black.copy(alpha = layerStrength * 0.95f)
                                        ),
                                        startY = clampedTop,
                                        endY = h
                                    ),
                                    blendMode = BlendMode.DstOut
                                )
                            }

                            // 3. Soft hotspot right around the active typing word so text pops cleanly
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black,
                                        0.52f to Color.Black.copy(alpha = 0.85f),
                                        1.0f to Color.Transparent
                                    ),
                                    center = Offset(targetX, focusY - 14f),
                                    radius = wordHalf * 1.18f
                                ),
                                center = Offset(targetX, focusY - 14f),
                                radius = wordHalf * 1.18f,
                                blendMode = BlendMode.DstOut
                            )

                            // 4. Subtle warm fog-light beam tint rising from toggle bar
                            val glowPath = Path().apply {
                                moveTo(originX - baseHalf * 0.9f, h)
                                lineTo(targetX - wordHalf * 0.85f, focusY - h * 0.08f)
                                lineTo(targetX + wordHalf * 0.85f, focusY - h * 0.08f)
                                lineTo(originX + baseHalf * 0.9f, h)
                                close()
                            }
                            drawPath(
                                path = glowPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFFFE0A3).copy(alpha = 0.05f + (1f - smoothLightDepth) * 0.04f),
                                        Color(0xFFFFD180).copy(alpha = 0.09f)
                                    ),
                                    startY = (focusY - h * 0.08f).coerceAtLeast(0f),
                                    endY = h
                                )
                            )
                        }
                    } else if ((isDarkRoom || isDarkPaper) && preferences.spotlightEnabled) {
                        val shadeColor = if (isDarkPaper) Color(0xFF040406) else Color(0xFF080706)
                        val roomDim = (smoothDimStrength * (0.65f + smoothLightDepth * 0.38f)).coerceIn(0.12f, 0.96f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        ) {
                            val w = size.width.coerceAtLeast(1f)
                            val h = size.height.coerceAtLeast(1f)
                            val originX = smoothBarOriginX.coerceIn(w * 0.16f, w * 0.84f)
                            val targetX = smoothFocusWordX.coerceIn(w * 0.12f, w * 0.88f)
                            val focusY = smoothFocusTypingY.coerceIn(h * 0.25f, h * 0.85f)
                            val baseHalf = w * (0.14f + smoothBeamWidth * 0.12f)
                            val wordHalf = w * (0.28f + smoothBeamWidth * 0.36f)
                            val topY = (focusY - h * (0.22f + smoothBeamWidth * 0.18f)).coerceAtLeast(0f)

                            drawRect(color = shadeColor.copy(alpha = roomDim))
                            val conePath = Path().apply {
                                moveTo(originX - baseHalf, h)
                                lineTo(targetX - wordHalf, topY)
                                lineTo(targetX + wordHalf, topY)
                                lineTo(originX + baseHalf, h)
                                close()
                            }
                            drawPath(
                                path = conePath,
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.40f to Color.Black.copy(alpha = 0.85f),
                                        1.0f to Color.Black
                                    ),
                                    startY = topY,
                                    endY = h
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    } else if (isDarkPaper) {
                        val edgeDim = (smoothDimStrength * 0.42f).coerceIn(0f, 0.58f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = edgeDim),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = edgeDim * 0.55f)
                                    )
                                )
                            )
                        }
                    }

                    // Smooth Mechanical Key Lever Strike Arm (no bright light flash)
                    val prog = leverProgress.value
                    if (preferences.leverAnimationEnabled && prog > 0.01f && editorState.activeField == EditorFocusField.BODY) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val target = Offset(
                                x = strikePointInPaper.x.coerceIn(28f, size.width - 28f),
                                y = strikePointInPaper.y.coerceIn(24f, size.height - 16f)
                            )
                            val pivot = Offset(
                                x = (size.width * 0.5f + (target.x - size.width * 0.5f) * 0.55f),
                                y = size.height + 16f
                            )
                            val tipX = pivot.x + (target.x - pivot.x) * prog
                            val tipY = pivot.y + (target.y - pivot.y) * prog
                            val tip = Offset(tipX, tipY)

                            drawLine(
                                color = Color.Black.copy(alpha = 0.22f * prog),
                                start = Offset(pivot.x + 8f, pivot.y),
                                end = Offset(tip.x + 5f, tip.y + 5f),
                                strokeWidth = 10f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF42454D), Color(0xFFC2C6CE), Color(0xFF565A64)),
                                    start = pivot,
                                    end = tip
                                ),
                                start = pivot,
                                end = tip,
                                strokeWidth = 7f,
                                cap = StrokeCap.Round
                            )
                            drawRect(
                                color = Color(0xFF23252A),
                                topLeft = Offset(tip.x - 9f, tip.y - 11f),
                                size = Size(18f, 14f)
                            )
                        }
                    }

                    // Brass/Ink Strike Guide ('T' I-beam indicator just above platen)
                    if (preferences.showTypingCursor) {
                        val guideXOffset = ((carriageXFraction - 0.5f) * 180f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isLandscape) 5.dp else 10.dp)
                                .offset { IntOffset(with(density) { guideXOffset.dp.roundToPx() }, 0) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(2.5.dp)
                                        .background(inkColor.copy(alpha = 0.65f))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(if (isLandscape) 10.dp else 14.dp)
                                        .background(inkColor.copy(alpha = 0.65f))
                                )
                            }
                        }
                    }
                }

                // Minimalist Upper Icon Bar: Back, Counter, Focus Mode Icon, Select Icon, Settings Icon
                val topBarIconTint = if (isDarkRoom || isDarkPaper) Color(0xFFEAE5DC) else Color(0xFF2B2823)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 4.dp)
                        .alpha(if (preferences.focusMode) 0.65f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onBackToFolio,
                            modifier = Modifier
                                .size(if (isLandscape) 36.dp else 44.dp)
                                .testTag("editor_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Notes Folio",
                                tint = topBarIconTint
                            )
                        }

                        if (!preferences.focusMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (isDarkRoom || isDarkPaper) Color.Black.copy(alpha = 0.45f)
                                        else Color.White.copy(alpha = 0.72f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (editorState.hasPendingSync || editorState.isDirty) {
                                        Icons.Rounded.CloudOff
                                    } else {
                                        Icons.Rounded.CloudDone
                                    },
                                    contentDescription = if (editorState.hasPendingSync) "Saved offline" else "Synced to cloud",
                                    tint = if (editorState.hasPendingSync || editorState.isDirty) {
                                        BrassAmber
                                    } else {
                                        if (isDarkRoom || isDarkPaper) Color(0xFF8FD19E) else Color(0xFF2E6F40)
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${editorState.wordCount} / ${formatNumber(editorState.wordGoal)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = topBarIconTint,
                                    modifier = Modifier.testTag("word_goal_counter")
                                )
                                if (sessionStats.elapsedSeconds > 0L) {
                                    Text(
                                        text = "· ${formatDuration(sessionStats.elapsedSeconds)} · ${sessionStats.wpm}wpm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDarkRoom || isDarkPaper) BrassAmber else Color(0xFF7B4E1E)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFocusMode,
                            modifier = Modifier
                                .size(if (isLandscape) 36.dp else 44.dp)
                                .testTag("top_focus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = if (preferences.focusMode) "Exit Focus Mode" else "Enter Focus Mode",
                                tint = if (preferences.focusMode) BrassAmber else topBarIconTint
                            )
                        }
                        IconButton(
                            onClick = { selectionToolbarPinned = !selectionToolbarPinned },
                            modifier = Modifier
                                .size(if (isLandscape) 36.dp else 44.dp)
                                .testTag("top_select_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SelectAll,
                                contentDescription = "Select & Delete Tools",
                                tint = if (showSelectionTools) BrassAmber else topBarIconTint
                            )
                        }
                        IconButton(
                            onClick = { onOpenSheet(ActiveEditorSheet.SETTINGS) },
                            modifier = Modifier
                                .size(if (isLandscape) 36.dp else 44.dp)
                                .testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "All Typewriter Settings",
                                tint = topBarIconTint
                            )
                        }
                    }
                }
            }

            // Contextual Select & Delete Bar (Word / Sentence / All / Delete / Clear Doc)
            AnimatedVisibility(
                visible = showSelectionTools,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkRoom || isDarkPaper) Color(0xFF17171C) else Color(0xFFE7E2D8))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectionActionPill(
                        label = "Word",
                        isDark = isDarkRoom || isDarkPaper,
                        testTag = "sel_word_button",
                        onClick = onSelectWord
                    )
                    SelectionActionPill(
                        label = "Sentence",
                        isDark = isDarkRoom || isDarkPaper,
                        testTag = "sel_sentence_button",
                        onClick = onSelectSentence
                    )
                    SelectionActionPill(
                        label = "All",
                        isDark = isDarkRoom || isDarkPaper,
                        testTag = "sel_all_button",
                        onClick = onSelectAll
                    )
                    if (hasSelection) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFB33A3A).copy(alpha = 0.24f))
                                .border(0.8.dp, Color(0xFFE57373), RoundedCornerShape(8.dp))
                                .clickable { onDeleteSelected() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("sel_delete_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete Selected",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Delete (${editorState.content.selection.length})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF8A80)
                            )
                        }
                    }
                    if (editorState.content.text.isNotEmpty()) {
                        SelectionActionPill(
                            label = "Clear Doc",
                            isDark = isDarkRoom || isDarkPaper,
                            isDanger = true,
                            testTag = "sel_clear_doc_button",
                            onClick = { confirmClearDoc = true }
                        )
                    }
                    IconButton(
                        onClick = {
                            selectionToolbarPinned = false
                            if (hasSelection) {
                                onUpdateSelection(TextRange(editorState.content.selection.max))
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Selection Bar",
                            tint = Color(0xFF9E988E),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Live Voice Dictation & Resize Feedback Pill right above Typing Toggle Bar
            AnimatedVisibility(visible = voiceState.errorMessage != null && !voiceState.isListening) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF231D18))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = voiceState.errorMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Platen Roller & Left-to-Right Carriage Scrubber + Hold-to-Resize Keyboard Bar
            PlatenCarriageBar(
                carriageFraction = carriageXFraction,
                spotlightOn = (preferences.focusMode || preferences.spotlightEnabled) && (isDarkRoom || isDarkPaper),
                cursorRow = cursorRow + 1,
                cursorCol = cursorCol + 1,
                compact = isLandscape,
                keyboardHeightScale = preferences.keyboardHeightScale,
                isResizingKeyboard = isResizingKeyboard,
                onScrubFraction = { frac ->
                    onSetActiveField(EditorFocusField.BODY)
                    val lines = editorState.content.text.split("\n")
                    val currentLineLen = lines.getOrNull(cursorRow)?.length ?: 0
                    val targetCol = (frac * maxOf(currentLineLen, 42)).roundToInt().coerceAtLeast(0)
                    onPlaceCarriageAt(cursorRow, targetCol)
                },
                onResizeStateChanged = { resizing -> isResizingKeyboard = resizing },
                onResizeKeyboardScale = onResizeKeyboardScale
            )

            // Vintage Mechanical Keyboard (or Slim Gboard Bar with Bold, Emoji, Voice & Language)
            if (preferences.useMechanicalKeyboard) {
                MechanicalKeyboard(
                    resistance = preferences.keyResistance,
                    language = currentLanguage,
                    heightScale = preferences.keyboardHeightScale,
                    maxKeyboardHeight = maxKbHeight,
                    isBoldActive = isBoldActive,
                    voiceState = voiceState,
                    phoneticBuffer = editorState.phoneticBuffer,
                    phoneticCandidates = editorState.phoneticCandidates,
                    onCharKey = onMechanicalChar,
                    onBackspace = onMechanicalBackspace,
                    onSelectCandidate = onSelectCandidate,
                    onInsertEmoji = onInsertEmoji,
                    onToggleBold = onToggleBold,
                    onToggleVoiceTyping = { toggleVoiceTyping() },
                    onToggleVoiceLanguage = { toggleVoiceLanguage() },
                    onOpenLanguagePicker = { onOpenSheet(ActiveEditorSheet.LANGUAGE) },
                    onSwitchToGboard = {
                        onSwitchToGboard()
                        if (editorState.activeField == EditorFocusField.TITLE) {
                            titleFocusRequester.requestFocus()
                        } else {
                            systemFocusRequester.requestFocus()
                        }
                        keyboardController?.show()
                    }
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = if (isDarkRoom || isDarkPaper) Color(0xFF151519) else Color(0xFFE2DDD2)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(visible = voiceState.isListening) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1A16))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = BrassAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = voiceState.partialText.ifBlank {
                                        "Listening (${voiceState.language.displayName})..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF3EFE6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Stop",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrassAmber,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { toggleVoiceTyping() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        AnimatedVisibility(visible = sysEmojiOpen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickSysEmojis.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF24242C))
                                            .clickable { onInsertEmoji(emoji) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBoldActive) BrassAmber.copy(alpha = 0.24f) else Color(0xFF26262E))
                                    .clickable { onToggleBold() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sys_bold_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBoldActive) BrassAmber else Color(0xFFEAE5DC),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sysEmojiOpen) BrassAmber.copy(alpha = 0.24f) else Color(0xFF26262E))
                                    .clickable { sysEmojiOpen = !sysEmojiOpen }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sys_emoji_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                    contentDescription = "Emoji",
                                    tint = if (sysEmojiOpen) BrassAmber else Color(0xFFEAE5DC),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (voiceState.isListening) BrassAmber.copy(alpha = 0.24f) else Color(0xFF26262E))
                                    .clickable { toggleVoiceTyping() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sys_voice_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (voiceState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                        contentDescription = "Voice Typing",
                                        tint = if (voiceState.isListening) BrassAmber else Color(0xFFEAE5DC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = voiceState.language.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrassAmber
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222229))
                                    .clickable { toggleVoiceLanguage() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("sys_voice_lang_toggle")
                            ) {
                                Text(
                                    text = "EN/বাং",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrassAmber
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF26262E))
                                    .clickable { onOpenSheet(ActiveEditorSheet.LANGUAGE) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sys_lang_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = "Language / IME",
                                        tint = BrassAmber,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Language / Gboard",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFEAE5DC)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleKeyboardMode() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("sys_switch_vintage")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Keyboard,
                                    contentDescription = "Vintage Keys",
                                    tint = BrassAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Vintage",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrassAmber
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionPill(
    label: String,
    isDark: Boolean,
    isDanger: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = when {
        isDanger -> Color(0xFF4A2020).copy(alpha = 0.6f)
        isDark -> Color(0xFF26262E)
        else -> Color(0xFFD7D1C6)
    }
    val fg = when {
        isDanger -> Color(0xFFFF9E9E)
        isDark -> Color(0xFFEAE5DC)
        else -> Color(0xFF22201C)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun PlatenCarriageBar(
    carriageFraction: Float,
    spotlightOn: Boolean,
    cursorRow: Int,
    cursorCol: Int,
    compact: Boolean,
    keyboardHeightScale: Float,
    isResizingKeyboard: Boolean,
    onScrubFraction: (Float) -> Unit,
    onResizeStateChanged: (Boolean) -> Unit,
    onResizeKeyboardScale: (Float) -> Unit
) {
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val currentScaleState by rememberUpdatedState(keyboardHeightScale)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 24.dp else 28.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlatenHighlight, PlatenDark, Color(0xFF0A0A0C))
                )
            )
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(viewConfig.touchSlop) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val holdTimeoutMs = 300L
                    var mode = 0 // 0 = undecided, 1 = horizontal scrub, 2 = hold-resize vertical
                    var lastY = startY
                    var workingScale = currentScaleState
                    var pointerActive = true

                    try {
                        // Phase 1: Wait up to 300ms; if horizontal drag starts early, scrub carriage;
                        // if held still for 300ms, immediately activate resize mode with haptic!
                        val earlyHorizontal = withTimeoutOrNull(holdTimeoutMs) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    pointerActive = false
                                    return@withTimeoutOrNull false
                                }
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                if (abs(dx) > viewConfig.touchSlop && abs(dx) >= abs(dy)) {
                                    change.consume()
                                    val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    onScrubFraction(frac)
                                    return@withTimeoutOrNull true
                                }
                            }
                        }

                        if (!pointerActive) return@awaitEachGesture

                        if (earlyHorizontal == true) {
                            mode = 1
                        } else {
                            mode = 2
                            onResizeStateChanged(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            lastY = startY
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            if (mode == 1) {
                                change.consume()
                                val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                onScrubFraction(frac)
                            } else if (mode == 2) {
                                change.consume()
                                val stepDy = change.position.y - lastY
                                lastY = change.position.y
                                // Dragging UP (negative dy) increases keyboard height; DOWN decreases it
                                workingScale = (workingScale - stepDy / 360f).coerceIn(0.58f, 1.32f)
                                onResizeKeyboardScale(workingScale)
                            }
                        }
                    } finally {
                        if (mode == 2) {
                            onResizeStateChanged(false)
                        }
                    }
                }
            }
            .padding(horizontal = 14.dp)
            .testTag("platen_carriage_bar"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0E0E12))
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val offsetX = maxWidth * carriageFraction.coerceIn(0.06f, 0.94f) - 22.dp
            Row(
                modifier = Modifier
                    .offset(x = offsetX.coerceIn(0.dp, (maxWidth - 44.dp).coerceAtLeast(0.dp)))
                    .size(width = 44.dp, height = if (compact) 14.dp else 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (spotlightOn || isResizingKeyboard) Color(0xFF47433B) else Color(0xFF2E2E36))
                    .border(
                        1.dp,
                        if (spotlightOn || isResizingKeyboard) BrassAmber else Color(0xFF565660),
                        RoundedCornerShape(8.dp)
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 3.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (spotlightOn || isResizingKeyboard) BrassAmber else Color(0xFF9E988E))
                    )
                }
            }
        }
        Text(
            text = if (isResizingKeyboard) {
                "Size ${(keyboardHeightScale * 100).roundToInt()}% ▲▼"
            } else {
                "Hold ▲▼"
            },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = if (isResizingKeyboard) BrassAmber else Color(0xFF8C867C),
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "$cursorRow:$cursorCol",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = Color(0xFF8C867C),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

private fun formatNumber(value: Long): String {
    return String.format("%,d", value)
}
