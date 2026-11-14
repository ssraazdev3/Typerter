package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientAudioEngine
import com.example.audio.KeySoundType
import com.example.audio.MechanicalAudioEngine
import com.example.data.model.EditorPreferences
import com.example.data.model.Note
import com.example.data.model.TypewriterFont
import com.example.data.model.WritingSession
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.LocalNotesStorage
import com.example.data.repository.NotesFeed
import com.example.data.repository.NotesRepository
import com.example.ui.keyboard.BanglaPhoneticEngine
import com.example.ui.keyboard.KeyboardLanguage
import com.example.voice.VoiceTypingEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SessionStats(
    val elapsedSeconds: Long = 0L,
    val wordsWrittenInSession: Long = 0L,
    val wpm: Int = 0,
    val isActive: Boolean = false
)

data class LeverStrikeEvent(
    val sequence: Long = 0L,
    val glyph: String = ""
)

enum class EditorFocusField {
    TITLE,
    BODY
}

data class EditorUiState(
    val activeNoteId: String? = null,
    val title: String = "",
    val titleSelection: TextRange = TextRange(0),
    val activeField: EditorFocusField = EditorFocusField.BODY,
    val content: TextFieldValue = TextFieldValue(""),
    val wordCount: Long = 0L,
    val charCount: Long = 0L,
    val wordGoal: Long = 3000L,
    val font: TypewriterFont = TypewriterFont.SPECIAL_ELITE,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val hasPendingSync: Boolean = false,
    val lastSavedTimeMs: Long? = null,
    val phoneticBuffer: String = "",
    val phoneticCandidates: List<String> = emptyList()
)

class KeystrokeViewModel(
    private val notesRepository: NotesRepository?,
    private val localNotesStorage: LocalNotesStorage?,
    private val preferencesRepository: PreferencesRepository,
    private val mechanicalAudioEngine: MechanicalAudioEngine,
    private val ambientAudioEngine: AmbientAudioEngine,
    private val currentUserId: String
) : ViewModel() {

    private val isLocalMode: Boolean
        get() = currentUserId == LocalNotesStorage.LOCAL_USER_ID && localNotesStorage != null

    val preferences: StateFlow<EditorPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EditorPreferences()
        )

    val notesState: StateFlow<UiState<NotesFeed>> = (
        if (isLocalMode) {
            localNotesStorage!!.observeNotes()
        } else {
            notesRepository!!.observeNotes(currentUserId)
        }
        )
        .map<NotesFeed, UiState<NotesFeed>> { feed ->
            if (!hasSeededInitialNote && feed.notes.isEmpty() && !feed.isFromCache) {
                hasSeededInitialNote = true
                createStarterNote()
            } else if (!hasSeededInitialNote && feed.notes.isNotEmpty()) {
                hasSeededInitialNote = true
                if (_editorState.value.activeNoteId == null) {
                    loadNoteIntoEditor(feed.notes.first(), feed.hasPendingWrites)
                }
            }
            val currentActiveId = _editorState.value.activeNoteId
            if (currentActiveId != null) {
                val matching = feed.notes.firstOrNull { it.id == currentActiveId }
                if (matching != null && !_editorState.value.isDirty) {
                    _editorState.value = _editorState.value.copy(
                        hasPendingSync = feed.hasPendingWrites,
                        pinned = matching.pinned,
                        archived = matching.archived
                    )
                } else {
                    _editorState.value = _editorState.value.copy(
                        hasPendingSync = feed.hasPendingWrites
                    )
                }
            }
            UiState.Success(feed)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing notes", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe notes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<List<WritingSession>> = (
        if (isLocalMode) {
            localNotesStorage!!.observeWritingSessions()
        } else {
            notesRepository!!.observeWritingSessions(currentUserId)
        }
        )
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing writing sessions", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    private val _leverStrike = MutableStateFlow(LeverStrikeEvent())
    val leverStrike: StateFlow<LeverStrikeEvent> = _leverStrike.asStateFlow()

    private var hasSeededInitialNote = false
    private var autoSaveJob: Job? = null
    private var sessionTickerJob: Job? = null
    private var lastKeystrokeAtMs: Long = 0L
    private var baselineWordCount: Long = 0L
    private var phoneticStartOffset: Int = -1
    private var phoneticRenderedLength: Int = 0
    private var leverSeq: Long = 0L

    init {
        viewModelScope.launch {
            preferences.collect { prefs ->
                ambientAudioEngine.update(prefs.ambientTrack, prefs.ambientVolume)
            }
        }
        startSessionTicker()
    }

    private fun startSessionTicker() {
        sessionTickerJob?.cancel()
        sessionTickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val active = lastKeystrokeAtMs > 0L && (now - lastKeystrokeAtMs) <= 15_000L
                val current = _sessionStats.value
                if (active) {
                    val nextSec = current.elapsedSeconds + 1L
                    val words = current.wordsWrittenInSession
                    val wpm = if (nextSec >= 5L && words > 0L) {
                        ((words * 60.0) / nextSec).toInt().coerceIn(0, 350)
                    } else {
                        current.wpm
                    }
                    _sessionStats.value = current.copy(
                        elapsedSeconds = nextSec,
                        wpm = wpm,
                        isActive = true
                    )
                } else if (current.isActive) {
                    _sessionStats.value = current.copy(isActive = false)
                }
            }
        }
    }

    private fun createStarterNote() {
        viewModelScope.launch {
            val prefs = preferences.value
            val starterTitle = "Midnight Draft"
            val starterContent = "Once upon a time there was a quiet page illuminated by a warm lamp.\n"
            val result = if (isLocalMode) {
                localNotesStorage!!.createNote(
                    title = starterTitle,
                    content = starterContent,
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    fontId = prefs.defaultFont.id
                )
            } else {
                notesRepository!!.createNote(
                    title = starterTitle,
                    content = starterContent,
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    fontId = prefs.defaultFont.id
                )
            }
            result.onSuccess { id ->
                _editorState.value = EditorUiState(
                    activeNoteId = id,
                    title = starterTitle,
                    titleSelection = TextRange(starterTitle.length),
                    activeField = EditorFocusField.BODY,
                    content = TextFieldValue(
                        text = starterContent,
                        selection = TextRange(starterContent.length)
                    ),
                    wordCount = NotesRepository.countWords(starterContent),
                    charCount = starterContent.length.toLong(),
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    font = prefs.defaultFont,
                    lastSavedTimeMs = System.currentTimeMillis()
                )
                resetSessionBaseline(NotesRepository.countWords(starterContent))
            }
        }
    }

    fun selectNote(note: Note) {
        viewModelScope.launch {
            commitPhoneticBuffer()
            flushSaveAndRecordSession()
            loadNoteIntoEditor(note, _editorState.value.hasPendingSync)
        }
    }

    private fun loadNoteIntoEditor(note: Note, pendingSync: Boolean) {
        val font = TypewriterFont.fromId(note.fontId)
        clearPhoneticTracking()
        _editorState.value = EditorUiState(
            activeNoteId = note.id,
            title = note.title,
            titleSelection = TextRange(note.title.length),
            activeField = EditorFocusField.BODY,
            content = TextFieldValue(
                text = note.content,
                selection = TextRange(note.content.length)
            ),
            wordCount = NotesRepository.countWords(note.content),
            charCount = note.content.length.toLong(),
            wordGoal = note.wordGoal.coerceAtLeast(100L),
            font = font,
            pinned = note.pinned,
            archived = note.archived,
            isDirty = false,
            hasPendingSync = pendingSync,
            lastSavedTimeMs = note.updatedAt?.toDate()?.time
        )
        resetSessionBaseline(NotesRepository.countWords(note.content))
    }

    fun createNewNote(onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            commitPhoneticBuffer()
            flushSaveAndRecordSession()
            val prefs = preferences.value
            val result = if (isLocalMode) {
                localNotesStorage!!.createNote(
                    title = "Untitled Note",
                    content = "",
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    fontId = prefs.defaultFont.id
                )
            } else {
                notesRepository!!.createNote(
                    title = "Untitled Note",
                    content = "",
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    fontId = prefs.defaultFont.id
                )
            }
            result.onSuccess { newId ->
                clearPhoneticTracking()
                _editorState.value = EditorUiState(
                    activeNoteId = newId,
                    title = "Untitled Note",
                    titleSelection = TextRange("Untitled Note".length),
                    activeField = EditorFocusField.BODY,
                    content = TextFieldValue("", TextRange(0)),
                    wordCount = 0L,
                    charCount = 0L,
                    wordGoal = prefs.defaultWordGoal.toLong(),
                    font = prefs.defaultFont,
                    lastSavedTimeMs = System.currentTimeMillis()
                )
                resetSessionBaseline(0L)
                onCreated()
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            if (_editorState.value.activeNoteId == noteId) {
                autoSaveJob?.cancel()
                clearPhoneticTracking()
                _editorState.value = EditorUiState()
            }
            if (isLocalMode) {
                localNotesStorage!!.deleteNote(noteId)
            } else {
                notesRepository!!.deleteNote(noteId)
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            if (isLocalMode) {
                localNotesStorage!!.updateNoteContent(
                    noteId = note.id,
                    title = note.title,
                    content = note.content,
                    wordGoal = note.wordGoal,
                    fontId = note.fontId,
                    pinned = !note.pinned,
                    archived = note.archived
                )
            } else {
                notesRepository!!.updateNoteContent(
                    noteId = note.id,
                    title = note.title,
                    content = note.content,
                    wordGoal = note.wordGoal,
                    fontId = note.fontId,
                    pinned = !note.pinned,
                    archived = note.archived
                )
            }
        }
    }

    fun setActiveField(field: EditorFocusField) {
        if (_editorState.value.activeField != field) {
            commitPhoneticBuffer()
            _editorState.value = _editorState.value.copy(activeField = field)
        }
    }

    fun updateTitleSelection(selection: TextRange) {
        val current = _editorState.value
        val len = current.title.length
        val clamped = TextRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len))
        _editorState.value = current.copy(
            activeField = EditorFocusField.TITLE,
            titleSelection = clamped
        )
    }

    fun updateTitleValue(value: TextFieldValue) {
        val current = _editorState.value
        val truncated = value.text.replace("\n", "").take(200)
        val sel = TextRange(
            value.selection.start.coerceIn(0, truncated.length),
            value.selection.end.coerceIn(0, truncated.length)
        )
        _editorState.value = current.copy(
            activeField = EditorFocusField.TITLE,
            title = truncated,
            titleSelection = sel,
            isDirty = true
        )
        scheduleAutoSave()
    }

    fun updateTitle(newTitle: String) {
        val current = _editorState.value
        val clean = newTitle.take(200)
        _editorState.value = current.copy(
            title = clean,
            titleSelection = TextRange(clean.length),
            isDirty = true
        )
        scheduleAutoSave()
    }

    fun updateWordGoal(goal: Long) {
        val clamped = goal.coerceIn(100L, 200_000L)
        _editorState.value = _editorState.value.copy(wordGoal = clamped, isDirty = true)
        viewModelScope.launch {
            preferencesRepository.update { it.copy(defaultWordGoal = clamped.toInt()) }
        }
        scheduleAutoSave()
    }

    fun updateNoteFont(font: TypewriterFont) {
        _editorState.value = _editorState.value.copy(font = font, isDirty = true)
        viewModelScope.launch {
            preferencesRepository.update { it.copy(defaultFont = font) }
        }
        scheduleAutoSave()
    }

    fun toggleBoldFormatting() {
        commitPhoneticBuffer()
        triggerTactileKeystroke(KeySoundType.ALPHA, "B")
        val current = _editorState.value.content
        val text = current.text
        val start = current.selection.min.coerceIn(0, text.length)
        val end = current.selection.max.coerceIn(0, text.length)

        if (start != end) {
            val selected = text.substring(start, end)
            val isWrapped = selected.startsWith("**") && selected.endsWith("**") && selected.length >= 4
            val replacement = if (isWrapped) {
                selected.removeSurrounding("**")
            } else {
                "**$selected**"
            }
            val updated = text.replaceRange(start, end, replacement)
            val newEnd = start + replacement.length
            applyContentChange(TextFieldValue(updated, TextRange(start, newEnd)))
        } else {
            // Check if cursor is directly inside **|**
            val before = text.take(start)
            val after = text.drop(start)
            if (before.endsWith("**") && after.startsWith("**")) {
                // Exit bold markers or remove empty **|**
                val updated = text.removeRange(start - 2, start + 2)
                applyContentChange(TextFieldValue(updated, TextRange(start - 2)))
            } else if (isInsideBoldSpan(text, start)) {
                // Jump past closing ** if found, else insert **
                val closeIdx = text.indexOf("**", start)
                if (closeIdx >= 0) {
                    moveCursorOffset(closeIdx + 2)
                } else {
                    val updated = text.substring(0, start) + "**" + text.substring(start)
                    applyContentChange(TextFieldValue(updated, TextRange(start + 2)))
                }
            } else {
                val updated = text.substring(0, start) + "****" + text.substring(start)
                applyContentChange(TextFieldValue(updated, TextRange(start + 2)))
            }
        }
    }

    /**
     * Handles updates from Android IME/Gboard System Keyboard (supports Bangla, Abc->Bangla, and all languages).
     * Preserves IME composing span so Gboard phonetic/syllable composition works seamlessly.
     */
    fun onSystemTextChanged(newValue: TextFieldValue) {
        val oldText = _editorState.value.content.text
        val newText = newValue.text
        if (newText != oldText) {
            val soundType = when {
                newText.length < oldText.length -> KeySoundType.BACKSPACE
                newText.length > oldText.length -> {
                    val insertedIdx = (newValue.selection.start - 1).coerceIn(0, newText.lastIndex)
                    when (newText.getOrNull(insertedIdx)) {
                        '\n' -> KeySoundType.RETURN
                        ' ' -> KeySoundType.SPACE
                        else -> KeySoundType.ALPHA
                    }
                }
                else -> KeySoundType.ALPHA
            }
            val glyph = if (newText.length > oldText.length) {
                val idx = (newValue.selection.start - 1).coerceIn(0, newText.lastIndex)
                newText.getOrNull(idx)?.toString() ?: ""
            } else ""
            triggerTactileKeystroke(soundType, glyph)
            applyContentChange(newValue)
        } else if (newValue.selection != _editorState.value.content.selection ||
            newValue.composition != _editorState.value.content.composition
        ) {
            _editorState.value = _editorState.value.copy(content = newValue)
        }
    }

    /**
     * Handles physical key presses from the built-in Vintage Typewriter Keyboard.
     * Supports both Document Title and Document Body seamlessly without switching to system IME.
     */
    fun onMechanicalKeyPress(charText: String) {
        if (_editorState.value.activeField == EditorFocusField.TITLE && charText == "\n") {
            commitPhoneticBuffer()
            triggerTactileKeystroke(KeySoundType.RETURN, "")
            _editorState.value = _editorState.value.copy(activeField = EditorFocusField.BODY)
            return
        }
        val lang = KeyboardLanguage.fromId(preferences.value.keyboardLanguageId)
        if (lang == KeyboardLanguage.BANGLA_PHONETIC) {
            handleBanglaPhoneticInput(charText)
            return
        }
        commitPhoneticBuffer()
        insertRawText(charText)
    }

    fun insertEmoji(emoji: String) {
        commitPhoneticBuffer()
        insertRawText(emoji)
    }

    fun insertVoiceDictatedText(rawDictated: String) {
        if (rawDictated.isBlank()) return
        commitPhoneticBuffer()
        val state = _editorState.value
        val activeText = if (state.activeField == EditorFocusField.TITLE) state.title else state.content.text
        val activeSel = if (state.activeField == EditorFocusField.TITLE) state.titleSelection else state.content.selection
        val start = activeSel.min.coerceIn(0, activeText.length)
        val formatted = VoiceTypingEngine.formatInsertionWithSpacing(
            existingText = activeText,
            insertStart = start,
            dictated = rawDictated
        )
        if (formatted.isNotEmpty()) {
            insertRawText(formatted)
        }
    }

    fun selectPhoneticCandidate(candidate: String) {
        val state = _editorState.value
        val activeText = if (state.activeField == EditorFocusField.TITLE) state.title else state.content.text
        if (phoneticStartOffset >= 0 && phoneticStartOffset + phoneticRenderedLength <= activeText.length) {
            val updated = activeText.replaceRange(
                phoneticStartOffset,
                phoneticStartOffset + phoneticRenderedLength,
                "$candidate "
            )
            val nextCursor = phoneticStartOffset + candidate.length + 1
            clearPhoneticTracking()
            triggerTactileKeystroke(KeySoundType.SPACE, candidate.take(1))
            if (state.activeField == EditorFocusField.TITLE) {
                val safeTitle = updated.take(200)
                _editorState.value = _editorState.value.copy(
                    title = safeTitle,
                    titleSelection = TextRange(nextCursor.coerceIn(0, safeTitle.length)),
                    isDirty = true,
                    phoneticBuffer = "",
                    phoneticCandidates = emptyList()
                )
                scheduleAutoSave()
            } else {
                applyContentChange(
                    TextFieldValue(updated, TextRange(nextCursor)),
                    phoneticBuffer = "",
                    phoneticCandidates = emptyList()
                )
            }
        }
    }

    private fun handleBanglaPhoneticInput(charText: String) {
        val isPhoneticChar = charText.length == 1 && (
            charText[0] in 'a'..'z' ||
                charText[0] in 'A'..'Z' ||
                charText[0] in '0'..'9' ||
                charText[0] == '^' ||
                charText[0] == '`'
            )

        if (isPhoneticChar) {
            triggerTactileKeystroke(KeySoundType.ALPHA, charText)
            val state = _editorState.value
            val isTitle = state.activeField == EditorFocusField.TITLE
            val currentText = if (isTitle) state.title else state.content.text
            val currentSel = if (isTitle) state.titleSelection else state.content.selection

            val buf = state.phoneticBuffer
            val nextBuf = buf + charText
            val rendered = BanglaPhoneticEngine.transliterate(nextBuf)
            val candidates = BanglaPhoneticEngine.candidatesFor(nextBuf)

            val startPos: Int
            val replaceEnd: Int
            if (buf.isEmpty() || phoneticStartOffset < 0 ||
                phoneticStartOffset + phoneticRenderedLength > currentText.length ||
                currentSel.start != phoneticStartOffset + phoneticRenderedLength
            ) {
                startPos = currentSel.min.coerceIn(0, currentText.length)
                replaceEnd = currentSel.max.coerceIn(0, currentText.length)
            } else {
                startPos = phoneticStartOffset
                replaceEnd = phoneticStartOffset + phoneticRenderedLength
            }

            val updated = currentText.replaceRange(startPos, replaceEnd, rendered)
            phoneticStartOffset = startPos
            phoneticRenderedLength = rendered.length
            val nextCursor = startPos + rendered.length

            if (isTitle) {
                val safeTitle = updated.take(200)
                _editorState.value = state.copy(
                    title = safeTitle,
                    titleSelection = TextRange(nextCursor.coerceIn(0, safeTitle.length)),
                    isDirty = true,
                    phoneticBuffer = nextBuf,
                    phoneticCandidates = candidates
                )
                scheduleAutoSave()
            } else {
                applyContentChange(
                    TextFieldValue(updated, TextRange(nextCursor)),
                    phoneticBuffer = nextBuf,
                    phoneticCandidates = candidates
                )
            }
        } else {
            commitPhoneticBuffer()
            insertRawText(charText)
        }
    }

    private fun insertRawText(charText: String) {
        val soundType = when (charText) {
            "\n" -> KeySoundType.RETURN
            " " -> KeySoundType.SPACE
            else -> KeySoundType.ALPHA
        }
        triggerTactileKeystroke(soundType, charText.trim())

        val state = _editorState.value
        if (state.activeField == EditorFocusField.TITLE) {
            val start = state.titleSelection.min.coerceIn(0, state.title.length)
            val end = state.titleSelection.max.coerceIn(0, state.title.length)
            val cleanInsert = charText.replace("\n", "")
            val updatedTitle = buildString {
                append(state.title, 0, start)
                append(cleanInsert)
                append(state.title, end, state.title.length)
            }.take(200)
            val nextCursor = (start + cleanInsert.length).coerceIn(0, updatedTitle.length)
            _editorState.value = state.copy(
                title = updatedTitle,
                titleSelection = TextRange(nextCursor),
                isDirty = true
            )
            scheduleAutoSave()
            return
        }

        val current = state.content
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(0, current.text.length)
        val updatedText = buildString {
            append(current.text, 0, start)
            append(charText)
            append(current.text, end, current.text.length)
        }
        val nextCursor = start + charText.length
        applyContentChange(TextFieldValue(updatedText, TextRange(nextCursor)))
    }

    fun onMechanicalBackspace() {
        val state = _editorState.value
        val isTitle = state.activeField == EditorFocusField.TITLE
        val activeText = if (isTitle) state.title else state.content.text
        val activeSel = if (isTitle) state.titleSelection else state.content.selection

        if (activeText.isEmpty() && isTitle) {
            triggerTactileKeystroke(KeySoundType.BACKSPACE, "⌫")
            return
        }

        triggerTactileKeystroke(KeySoundType.BACKSPACE, "⌫")
        val buf = state.phoneticBuffer

        if (buf.isNotEmpty() && phoneticStartOffset >= 0 &&
            phoneticStartOffset + phoneticRenderedLength <= activeText.length &&
            activeSel.start == phoneticStartOffset + phoneticRenderedLength
        ) {
            val nextBuf = buf.dropLast(1)
            val rendered = if (nextBuf.isEmpty()) "" else BanglaPhoneticEngine.transliterate(nextBuf)
            val candidates = if (nextBuf.isEmpty()) emptyList() else BanglaPhoneticEngine.candidatesFor(nextBuf)
            val updated = activeText.replaceRange(
                phoneticStartOffset,
                phoneticStartOffset + phoneticRenderedLength,
                rendered
            )
            val nextCursor = phoneticStartOffset + rendered.length
            if (nextBuf.isEmpty()) {
                clearPhoneticTracking()
            } else {
                phoneticRenderedLength = rendered.length
            }
            if (isTitle) {
                val safeTitle = updated.take(200)
                _editorState.value = state.copy(
                    title = safeTitle,
                    titleSelection = TextRange(nextCursor.coerceIn(0, safeTitle.length)),
                    isDirty = true,
                    phoneticBuffer = nextBuf,
                    phoneticCandidates = candidates
                )
                scheduleAutoSave()
            } else {
                applyContentChange(
                    TextFieldValue(updated, TextRange(nextCursor)),
                    phoneticBuffer = nextBuf,
                    phoneticCandidates = candidates
                )
            }
            return
        }

        clearPhoneticTracking()
        val start = activeSel.min.coerceIn(0, activeText.length)
        val end = activeSel.max.coerceIn(0, activeText.length)
        if (start == 0 && end == 0) return

        val (updatedText, nextCursor) = if (start != end) {
            activeText.removeRange(start, end) to start
        } else {
            activeText.removeRange(start - 1, start) to (start - 1)
        }
        if (isTitle) {
            _editorState.value = state.copy(
                title = updatedText,
                titleSelection = TextRange(nextCursor.coerceIn(0, updatedText.length)),
                isDirty = true
            )
            scheduleAutoSave()
        } else {
            applyContentChange(TextFieldValue(updatedText, TextRange(nextCursor)))
        }
    }

    fun commitPhoneticBuffer() {
        if (_editorState.value.phoneticBuffer.isNotEmpty() || phoneticStartOffset >= 0) {
            clearPhoneticTracking()
            _editorState.value = _editorState.value.copy(
                phoneticBuffer = "",
                phoneticCandidates = emptyList()
            )
        }
    }

    private fun clearPhoneticTracking() {
        phoneticStartOffset = -1
        phoneticRenderedLength = 0
    }

    fun moveCursorOffset(offset: Int) {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val clamped = offset.coerceIn(0, current.text.length)
        _editorState.value = _editorState.value.copy(
            content = current.copy(selection = TextRange(clamped))
        )
    }

    fun updateSelection(selection: TextRange) {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val len = current.text.length
        val clamped = TextRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len))
        if (clamped != current.selection) {
            _editorState.value = _editorState.value.copy(
                content = current.copy(selection = clamped)
            )
        }
    }

    fun selectWordAtCursor() {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val range = findWordRange(current.text, current.selection.start) ?: return
        _editorState.value = _editorState.value.copy(
            content = current.copy(selection = range)
        )
    }

    fun selectSentenceAtCursor() {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val range = findSentenceRange(current.text, current.selection.start) ?: return
        _editorState.value = _editorState.value.copy(
            content = current.copy(selection = range)
        )
    }

    fun selectAllText() {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        if (current.text.isEmpty()) return
        _editorState.value = _editorState.value.copy(
            content = current.copy(selection = TextRange(0, current.text.length))
        )
    }

    fun deleteSelectedText() {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val min = current.selection.min.coerceIn(0, current.text.length)
        val max = current.selection.max.coerceIn(0, current.text.length)
        if (min == max) return
        triggerTactileKeystroke(KeySoundType.BACKSPACE, "⌫")
        val updated = current.text.removeRange(min, max)
        applyContentChange(TextFieldValue(updated, TextRange(min)))
    }

    fun clearWholeDocument() {
        commitPhoneticBuffer()
        if (_editorState.value.content.text.isEmpty()) return
        triggerTactileKeystroke(KeySoundType.RETURN, "")
        applyContentChange(TextFieldValue("", TextRange(0)))
    }

    fun placeCarriageAt(targetRow: Int, targetCol: Int) {
        commitPhoneticBuffer()
        val current = _editorState.value.content
        val freeMode = preferences.value.freeCursorPlacement
        val lines = current.text.split("\n").toMutableList()
        val safeRow = targetRow.coerceIn(0, if (freeMode) (lines.size + 12) else (lines.size - 1).coerceAtLeast(0))

        if (!freeMode) {
            val clampedRow = safeRow.coerceIn(0, lines.lastIndex)
            val clampedCol = targetCol.coerceIn(0, lines[clampedRow].length)
            val offset = computeOffsetForLineCol(lines, clampedRow, clampedCol)
            _editorState.value = _editorState.value.copy(
                content = current.copy(selection = TextRange(offset))
            )
            return
        }

        while (lines.size <= safeRow) {
            lines.add("")
        }
        val rowText = lines[safeRow]
        val safeCol = targetCol.coerceIn(0, 72)
        if (safeCol > rowText.length) {
            lines[safeRow] = rowText + " ".repeat(safeCol - rowText.length)
        }
        val newText = lines.joinToString("\n")
        val offset = computeOffsetForLineCol(lines, safeRow, safeCol)
        triggerTactileKeystroke(KeySoundType.SPACE, "")
        applyContentChange(TextFieldValue(newText, TextRange(offset)))
    }

    private fun triggerTactileKeystroke(soundType: KeySoundType, glyph: String) {
        val prefs = preferences.value
        mechanicalAudioEngine.playKeystroke(
            keyType = soundType,
            profile = prefs.switchProfile,
            resistance = prefs.keyResistance,
            volume = prefs.soundVolume,
            hapticsEnabled = prefs.hapticsEnabled
        )
        if (prefs.leverAnimationEnabled) {
            leverSeq += 1L
            _leverStrike.value = LeverStrikeEvent(sequence = leverSeq, glyph = glyph)
        }
        lastKeystrokeAtMs = System.currentTimeMillis()
    }

    private fun applyContentChange(
        newValue: TextFieldValue,
        phoneticBuffer: String = _editorState.value.phoneticBuffer,
        phoneticCandidates: List<String> = _editorState.value.phoneticCandidates
    ) {
        val words = NotesRepository.countWords(newValue.text)
        val chars = newValue.text.length.toLong()
        val deltaWords = (words - baselineWordCount).coerceAtLeast(0L)
        _editorState.value = _editorState.value.copy(
            content = newValue,
            wordCount = words,
            charCount = chars,
            isDirty = true,
            phoneticBuffer = phoneticBuffer,
            phoneticCandidates = phoneticCandidates
        )
        _sessionStats.value = _sessionStats.value.copy(
            wordsWrittenInSession = deltaWords
        )
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            persistCurrentNote()
        }
    }

    fun flushSaveNow() {
        viewModelScope.launch {
            flushSaveAndRecordSession()
        }
    }

    private suspend fun flushSaveAndRecordSession() {
        autoSaveJob?.cancel()
        persistCurrentNote()
        val stats = _sessionStats.value
        val activeId = _editorState.value.activeNoteId
        if (activeId != null && stats.elapsedSeconds >= 10L && stats.wordsWrittenInSession >= 5L) {
            if (isLocalMode) {
                localNotesStorage!!.recordWritingSession(
                    noteId = activeId,
                    wordsWritten = stats.wordsWrittenInSession,
                    durationSeconds = stats.elapsedSeconds,
                    wpm = stats.wpm.toLong()
                )
            } else {
                notesRepository!!.recordWritingSession(
                    noteId = activeId,
                    wordsWritten = stats.wordsWrittenInSession,
                    durationSeconds = stats.elapsedSeconds,
                    wpm = stats.wpm.toLong()
                )
            }
            resetSessionBaseline(_editorState.value.wordCount)
        }
    }

    private suspend fun persistCurrentNote() {
        val state = _editorState.value
        val noteId = state.activeNoteId ?: return
        if (!state.isDirty) return

        _editorState.value = state.copy(isSaving = true)
        val result = if (isLocalMode) {
            localNotesStorage!!.updateNoteContent(
                noteId = noteId,
                title = state.title,
                content = state.content.text,
                wordGoal = state.wordGoal,
                fontId = state.font.id,
                pinned = state.pinned,
                archived = state.archived
            )
        } else {
            notesRepository!!.updateNoteContent(
                noteId = noteId,
                title = state.title,
                content = state.content.text,
                wordGoal = state.wordGoal,
                fontId = state.font.id,
                pinned = state.pinned,
                archived = state.archived
            )
        }
        _editorState.value = _editorState.value.copy(
            isSaving = false,
            isDirty = result.isFailure,
            lastSavedTimeMs = if (result.isSuccess) System.currentTimeMillis() else state.lastSavedTimeMs
        )
    }

    fun updatePreferences(transform: (EditorPreferences) -> EditorPreferences) {
        viewModelScope.launch {
            preferencesRepository.update(transform)
        }
    }

    private fun resetSessionBaseline(wordCount: Long) {
        baselineWordCount = wordCount
        lastKeystrokeAtMs = 0L
        _sessionStats.value = SessionStats()
    }

    override fun onCleared() {
        super.onCleared()
        sessionTickerJob?.cancel()
        autoSaveJob?.cancel()
    }

    companion object {
        private const val TAG = "KeystrokeVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val AUTO_SAVE_DEBOUNCE_MS = 450L

        fun isInsideBoldSpan(text: String, cursorOffset: Int): Boolean {
            val safe = cursorOffset.coerceIn(0, text.length)
            var count = 0
            var idx = 0
            while (idx < safe - 1) {
                if (text[idx] == '*' && text[idx + 1] == '*') {
                    count++
                    idx += 2
                } else {
                    idx++
                }
            }
            return count % 2 == 1
        }

        fun findWordRange(text: String, cursorOffset: Int): TextRange? {
            if (text.isEmpty()) return null
            var anchor = cursorOffset.coerceIn(0, text.length)
            if (anchor == text.length && anchor > 0) anchor--
            if (anchor > 0 && anchor < text.length && text[anchor].isWhitespace() && !text[anchor - 1].isWhitespace()) {
                anchor--
            }
            if (text[anchor].isWhitespace()) return null
            var start = anchor
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            var end = anchor + 1
            while (end < text.length && !text[end].isWhitespace()) end++
            return TextRange(start, end)
        }

        fun findSentenceRange(text: String, cursorOffset: Int): TextRange? {
            if (text.isEmpty()) return null
            val delimiters = charArrayOf('.', '!', '?', '।', '؟', '\n')
            var anchor = cursorOffset.coerceIn(0, text.length)
            if (anchor == text.length && anchor > 0) anchor--
            if (anchor > 0 && text[anchor] in delimiters && text[anchor - 1] !in delimiters) {
                anchor--
            }
            var start = anchor
            while (start > 0 && text[start - 1] !in delimiters) start--
            while (start < text.length && text[start].isWhitespace()) start++
            var end = anchor + 1
            while (end < text.length && text[end] !in delimiters) end++
            if (end < text.length && text[end] in charArrayOf('.', '!', '?', '।', '؟')) end++
            if (start >= end) return findWordRange(text, cursorOffset)
            return TextRange(start, end)
        }

        fun computeLineColForOffset(text: String, offset: Int): Pair<Int, Int> {
            val safe = offset.coerceIn(0, text.length)
            var row = 0
            var col = 0
            for (i in 0 until safe) {
                if (text[i] == '\n') {
                    row++
                    col = 0
                } else {
                    col++
                }
            }
            return row to col
        }

        fun computeOffsetForLineCol(lines: List<String>, row: Int, col: Int): Int {
            var offset = 0
            val safeRow = row.coerceIn(0, lines.lastIndex)
            for (r in 0 until safeRow) {
                offset += lines[r].length + 1
            }
            offset += col.coerceIn(0, lines[safeRow].length)
            return offset
        }
    }
}
