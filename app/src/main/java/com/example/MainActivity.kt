package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.AmbientAudioEngine
import com.example.audio.KeySoundType
import com.example.audio.MechanicalAudioEngine
import com.example.data.model.EditorPreferences
import com.example.data.model.ExportFormat
import com.example.data.model.Note
import com.example.data.model.ThemeMode
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.LocalNotesStorage
import com.example.data.repository.NotesRepository
import com.example.export.DocumentExporter
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.editor.ActiveEditorSheet
import com.example.ui.editor.EditorBottomSheetHost
import com.example.ui.editor.TypewriterEditorScreen
import com.example.ui.folio.DashboardSettingsSheet
import com.example.ui.folio.NotesFolioScreen
import com.example.ui.security.AppLockScreen
import com.example.ui.theme.DarkDeskBackground
import com.example.ui.theme.KeystrokeTheme
import com.example.ui.viewmodel.KeystrokeViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var mechanicalAudioEngine: MechanicalAudioEngine
    private lateinit var ambientAudioEngine: AmbientAudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mechanicalAudioEngine = MechanicalAudioEngine(applicationContext)
        ambientAudioEngine = AmbientAudioEngine()

        setContent {
            val prefsRepo = remember { PreferencesRepository(applicationContext) }
            val loadedPrefs by prefsRepo.preferencesFlow
                .map<EditorPreferences, EditorPreferences?> { it }
                .collectAsStateWithLifecycle(initialValue = null)
            val effectivePrefs = loadedPrefs ?: EditorPreferences()

            KeystrokeTheme(themeMode = effectivePrefs.themeMode) {
                TyperterAppRoot(
                    loadedPreferences = loadedPrefs,
                    preferencesRepository = prefsRepo,
                    mechanicalAudioEngine = mechanicalAudioEngine,
                    ambientAudioEngine = ambientAudioEngine
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mechanicalAudioEngine.release()
        ambientAudioEngine.release()
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private enum class TyperterDestination {
    FOLIO,
    EDITOR
}

@Composable
private fun TyperterAppRoot(
    loadedPreferences: EditorPreferences?,
    preferencesRepository: PreferencesRepository,
    mechanicalAudioEngine: MechanicalAudioEngine,
    ambientAudioEngine: AmbientAudioEngine
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    // Hold lock state cleanly across configuration changes; reset on cold process launch
    var isAppUnlocked by rememberSaveable { mutableStateOf(false) }
    var suppressNextBackgroundLock by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, loadedPreferences) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    suppressNextBackgroundLock = false
                }
                Lifecycle.Event.ON_STOP -> {
                    val prefs = loadedPreferences
                    if (!suppressNextBackgroundLock &&
                        prefs != null &&
                        prefs.appLockEnabled &&
                        prefs.appPasswordHash.isNotBlank() &&
                        prefs.lockOnBackground
                    ) {
                        isAppUnlocked = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(loadedPreferences?.useLocalStorageMode) {
        if (auth.currentUser == null && loadedPreferences != null && !loadedPreferences.useLocalStorageMode) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    if (loadedPreferences == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkDeskBackground)
        )
        return
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Close Typerter?") },
            text = {
                Text("Are you sure you want to close Typerter? All your notes and changes are saved.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        activity?.finish()
                    },
                    modifier = Modifier.testTag("exit_confirm_button")
                ) {
                    Text("Close App", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmDialog = false },
                    modifier = Modifier.testTag("exit_stay_button")
                ) {
                    Text("Stay")
                }
            }
        )
    }

    val requiresLock = loadedPreferences.appLockEnabled &&
        loadedPreferences.appPasswordHash.isNotBlank() &&
        !isAppUnlocked

    if (requiresLock) {
        BackHandler {
            showExitConfirmDialog = true
        }
        AppLockScreen(
            preferences = loadedPreferences,
            userEmail = currentUser?.email ?: if (loadedPreferences.useLocalStorageMode) "Phone Storage Mode" else null,
            onUnlocked = { isAppUnlocked = true }
        )
        return
    }

    val user = currentUser
    val isLocalMode = user == null && loadedPreferences.useLocalStorageMode

    if (user == null && !isLocalMode) {
        BackHandler {
            showExitConfirmDialog = true
        }
        AuthScreen(
            onAuthSuccess = {
                scope.launch {
                    preferencesRepository.update { it.copy(useLocalStorageMode = false) }
                }
            },
            onContinueWithoutAccount = {
                scope.launch {
                    preferencesRepository.update { it.copy(useLocalStorageMode = true) }
                }
            }
        )
    } else {
        val activeUserKey = user?.uid ?: LocalNotesStorage.LOCAL_USER_ID
        key(activeUserKey) {
            AuthenticatedTyperterApp(
                currentUserId = activeUserKey,
                isLocalMode = isLocalMode,
                userDisplayName = user?.displayName,
                userEmail = user?.email,
                preferencesRepository = preferencesRepository,
                mechanicalAudioEngine = mechanicalAudioEngine,
                ambientAudioEngine = ambientAudioEngine,
                onSuppressAutoLock = { suppressNextBackgroundLock = true },
                onLockApp = { isAppUnlocked = false },
                onRequestExitApp = { showExitConfirmDialog = true },
                onConnectGoogleAccount = {
                    suppressNextBackgroundLock = true
                    GoogleAuthHelper.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            scope.launch {
                                preferencesRepository.update { it.copy(useLocalStorageMode = false) }
                            }
                            Toast.makeText(context, "Signed in with Google for Cloud Sync", Toast.LENGTH_SHORT).show()
                        },
                        onAuthError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        scope = scope
                    )
                },
                onSignOut = {
                    ambientAudioEngine.update(com.example.data.model.AmbientTrack.NONE, 0f)
                    scope.launch {
                        preferencesRepository.update { it.copy(useLocalStorageMode = false) }
                    }
                    GoogleAuthHelper.signOut(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedTyperterApp(
    currentUserId: String,
    isLocalMode: Boolean,
    userDisplayName: String?,
    userEmail: String?,
    preferencesRepository: PreferencesRepository,
    mechanicalAudioEngine: MechanicalAudioEngine,
    ambientAudioEngine: AmbientAudioEngine,
    onSuppressAutoLock: () -> Unit,
    onLockApp: () -> Unit,
    onRequestExitApp: () -> Unit,
    onConnectGoogleAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localNotesStorage = remember(context, isLocalMode) {
        if (isLocalMode) LocalNotesStorage(context.applicationContext) else null
    }

    val viewModel: KeystrokeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val repo = if (!isLocalMode) {
                    val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
                    NotesRepository(FirebaseFirestore.getInstance(databaseId))
                } else {
                    null
                }
                KeystrokeViewModel(
                    notesRepository = repo,
                    localNotesStorage = localNotesStorage,
                    preferencesRepository = preferencesRepository,
                    mechanicalAudioEngine = mechanicalAudioEngine,
                    ambientAudioEngine = ambientAudioEngine,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionsState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val sessionStats by viewModel.sessionStats.collectAsStateWithLifecycle()
    val leverStrike by viewModel.leverStrike.collectAsStateWithLifecycle()

    // Start app directly on the Notes Dashboard (Folio) showing all notes!
    var destination by rememberSaveable { mutableStateOf(TyperterDestination.FOLIO) }
    var activeSheet by remember { mutableStateOf<ActiveEditorSheet?>(null) }
    var showDashboardSettings by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSaveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            val snapshotNote = Note(
                id = editorState.activeNoteId ?: "draft",
                userId = currentUserId,
                title = editorState.title,
                content = editorState.content.text,
                wordCount = editorState.wordCount,
                charCount = editorState.charCount,
                wordGoal = editorState.wordGoal,
                fontId = editorState.font.id
            )
            scope.launch {
                val res = DocumentExporter.exportToUri(context, snapshotNote, pendingExportFormat, uri)
                val msg = if (res.isSuccess) {
                    "Saved ${pendingExportFormat.label}"
                } else {
                    "Export failed: ${res.exceptionOrNull()?.localizedMessage}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (destination == TyperterDestination.FOLIO) {
        BackHandler {
            if (showDashboardSettings) {
                showDashboardSettings = false
            } else {
                viewModel.flushSaveNow()
                onRequestExitApp()
            }
        }

        val feedSuccess = (notesState as? UiState.Success)?.data
        NotesFolioScreen(
            notesState = notesState,
            sessions = sessions,
            themeMode = preferences.themeMode,
            userEmail = userEmail,
            isLocalMode = isLocalMode,
            appLockEnabled = preferences.appLockEnabled && preferences.appPasswordHash.isNotBlank(),
            biometricEnabled = preferences.biometricLockEnabled,
            onToggleTheme = {
                viewModel.updatePreferences {
                    it.copy(
                        themeMode = if (it.themeMode == ThemeMode.DARK_DESK) {
                            ThemeMode.LIGHT_STUDIO
                        } else {
                            ThemeMode.DARK_DESK
                        }
                    )
                }
            },
            onOpenAppSettings = { showDashboardSettings = true },
            onOpenNote = { note ->
                viewModel.selectNote(note)
                destination = TyperterDestination.EDITOR
            },
            onCreateNote = {
                viewModel.createNewNote {
                    destination = TyperterDestination.EDITOR
                }
            },
            onTogglePin = viewModel::togglePin,
            onDeleteNote = viewModel::deleteNote,
            onSignOut = {
                viewModel.flushSaveNow()
                onSignOut()
            }
        )

        DashboardSettingsSheet(
            visible = showDashboardSettings,
            preferences = preferences,
            userId = currentUserId,
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            isLocalMode = isLocalMode,
            localStoragePath = localNotesStorage?.getStorageDisplayPath().orEmpty(),
            hasPendingSync = feedSuccess?.hasPendingWrites == true,
            totalNotesCount = feedSuccess?.notes?.size ?: 0,
            onDismiss = { showDashboardSettings = false },
            onUpdatePreferences = viewModel::updatePreferences,
            onSuppressAutoLock = onSuppressAutoLock,
            onLockNow = {
                viewModel.flushSaveNow()
                onLockApp()
            },
            onConnectGoogleAccount = onConnectGoogleAccount,
            onSignOut = {
                viewModel.flushSaveNow()
                onSignOut()
            }
        )
    } else {
        BackHandler {
            if (activeSheet != null) {
                activeSheet = null
            } else {
                viewModel.flushSaveNow()
                destination = TyperterDestination.FOLIO
            }
        }

        TypewriterEditorScreen(
            editorState = editorState,
            preferences = preferences,
            sessionStats = sessionStats,
            leverStrike = leverStrike,
            onBackToFolio = {
                viewModel.flushSaveNow()
                destination = TyperterDestination.FOLIO
            },
            onSetActiveField = viewModel::setActiveField,
            onTitleValueChange = viewModel::updateTitleValue,
            onTitleSelectionChange = viewModel::updateTitleSelection,
            onSystemTextChange = viewModel::onSystemTextChanged,
            onMechanicalChar = viewModel::onMechanicalKeyPress,
            onMechanicalBackspace = viewModel::onMechanicalBackspace,
            onSelectCandidate = viewModel::selectPhoneticCandidate,
            onInsertEmoji = viewModel::insertEmoji,
            onToggleBold = viewModel::toggleBoldFormatting,
            onPlaceCarriageAt = viewModel::placeCarriageAt,
            onMoveCursorOffset = viewModel::moveCursorOffset,
            onUpdateSelection = viewModel::updateSelection,
            onSelectWord = viewModel::selectWordAtCursor,
            onSelectSentence = viewModel::selectSentenceAtCursor,
            onSelectAll = viewModel::selectAllText,
            onDeleteSelected = viewModel::deleteSelectedText,
            onClearDocument = viewModel::clearWholeDocument,
            onOpenSheet = { activeSheet = it },
            onToggleKeyboardMode = {
                viewModel.updatePreferences {
                    it.copy(useMechanicalKeyboard = !it.useMechanicalKeyboard)
                }
            },
            onToggleFocusMode = {
                viewModel.updatePreferences { it.copy(focusMode = !it.focusMode) }
            },
            onSwitchToGboard = {
                viewModel.updatePreferences { it.copy(useMechanicalKeyboard = false) }
            },
            onVoiceDictatedText = viewModel::insertVoiceDictatedText,
            onUpdateVoiceLanguage = { lang ->
                viewModel.updatePreferences { it.copy(voiceLanguageTag = lang.localeTag) }
            },
            onResizeKeyboardScale = { scale ->
                viewModel.updatePreferences { it.copy(keyboardHeightScale = scale) }
            },
            onSuppressAutoLock = onSuppressAutoLock
        )

        EditorBottomSheetHost(
            activeSheet = activeSheet,
            preferences = preferences,
            currentFont = editorState.font,
            wordGoal = editorState.wordGoal,
            sessionStats = sessionStats,
            onDismiss = { activeSheet = null },
            onOpenFolio = {
                viewModel.flushSaveNow()
                destination = TyperterDestination.FOLIO
            },
            onSelectFont = viewModel::updateNoteFont,
            onUpdatePreferences = viewModel::updatePreferences,
            onTestKeystroke = {
                mechanicalAudioEngine.playKeystroke(
                    keyType = KeySoundType.ALPHA,
                    profile = preferences.switchProfile,
                    resistance = preferences.keyResistance,
                    volume = preferences.soundVolume,
                    hapticsEnabled = preferences.hapticsEnabled
                )
            },
            onUpdateWordGoal = viewModel::updateWordGoal,
            onSaveExport = { format ->
                pendingExportFormat = format
                activeSheet = null
                val snapshotNote = Note(
                    id = editorState.activeNoteId ?: "draft",
                    title = editorState.title,
                    content = editorState.content.text
                )
                onSuppressAutoLock()
                createDocLauncher.launch(DocumentExporter.suggestedFileName(snapshotNote, format))
            },
            onShareExport = { format ->
                activeSheet = null
                val snapshotNote = Note(
                    id = editorState.activeNoteId ?: "draft",
                    title = editorState.title,
                    content = editorState.content.text,
                    wordCount = editorState.wordCount,
                    charCount = editorState.charCount,
                    wordGoal = editorState.wordGoal,
                    fontId = editorState.font.id
                )
                scope.launch {
                    val intentRes = DocumentExporter.createShareIntent(context, snapshotNote, format)
                    intentRes.onSuccess { intent ->
                        onSuppressAutoLock()
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Share ${format.label}")
                        )
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            "Share failed: ${err.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}
