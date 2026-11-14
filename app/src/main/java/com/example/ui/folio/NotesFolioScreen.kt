package com.example.ui.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Note
import com.example.data.model.ThemeMode
import com.example.data.model.TypewriterFont
import com.example.data.model.WritingSession
import com.example.data.repository.NotesFeed
import com.example.ui.theme.BrassAmber
import com.example.ui.theme.SpecialEliteFamily
import com.example.ui.theme.toComposeFontFamily
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesFolioScreen(
    notesState: UiState<NotesFeed>,
    sessions: List<WritingSession>,
    themeMode: ThemeMode,
    userEmail: String?,
    isLocalMode: Boolean = false,
    appLockEnabled: Boolean,
    biometricEnabled: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onCreateNote: () -> Unit,
    onTogglePin: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Typerter",
                        fontFamily = SpecialEliteFamily,
                        fontSize = 24.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("folio_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK_DESK) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle Light or Dark Desk"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.testTag("folio_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "App Settings & Security"
                        )
                    }
                    if (!isLocalMode) {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("folio_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNote,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_note_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Typewriter Sheet"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account & Sync Status Banner
                val hasPending = (notesState as? UiState.Success)?.data?.hasPendingWrites == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(onClick = onOpenAppSettings)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("folio_account_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLocalMode) "Phone Storage Mode (No Account)" else (userEmail ?: "Synced Typewriter Folio"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val syncText = when {
                            isLocalMode -> "Saved locally on phone storage"
                            hasPending -> "Offline changes saved locally · Syncing"
                            else -> "Cloud synced · Offline ready"
                        }
                        val securityText = when {
                            appLockEnabled && biometricEnabled -> " · Password + Fingerprint Lock"
                            appLockEnabled -> " · Password Lock"
                            else -> ""
                        }
                        Text(
                            text = syncText + securityText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasPending) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (appLockEnabled) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "App Lock Enabled",
                                tint = BrassAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(
                            imageVector = if (hasPending) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (hasPending) BrassAmber else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Writing Statistics Summary
                if (sessions.isNotEmpty() || notesState is UiState.Success) {
                    val notes = (notesState as? UiState.Success)?.data?.notes.orEmpty()
                    val totalWords = notes.sumOf { it.wordCount }
                    val avgWpm = if (sessions.isNotEmpty()) {
                        sessions.map { it.wpm }.average().toInt()
                    } else 0
                    val totalMinutes = sessions.sumOf { it.durationSeconds } / 60L

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FolioMetric("Pages", "${notes.size}")
                        FolioMetric("Total Words", "$totalWords")
                        FolioMetric("Avg Speed", "${avgWpm} WPM")
                        FolioMetric("Focus Time", "${totalMinutes}m")
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search manuscripts...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folio_search_input")
                )

                when (notesState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = notesState.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        val filtered = remember(notesState.data.notes, searchQuery) {
                            val q = searchQuery.trim().lowercase()
                            if (q.isEmpty()) notesState.data.notes
                            else notesState.data.notes.filter {
                                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
                            }
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No pages yet. Tap + to insert fresh paper.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 90.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.id }) { note ->
                                    NoteFolioCard(
                                        note = note,
                                        onClick = { onOpenNote(note) },
                                        onTogglePin = { onTogglePin(note) },
                                        onDelete = { onDeleteNote(note.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteFolioCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val font = TypewriterFont.fromId(note.fontId)
    val dateLabel = remember(note.updatedAt, note.createdAt) {
        val ts = note.updatedAt ?: note.createdAt
        if (ts == null) "Just now"
        else SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(ts.toDate())
    }
    val progress = if (note.wordGoal > 0L) {
        (note.wordCount.toFloat() / note.wordGoal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (note.pinned) BrassAmber.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Page" },
                    fontFamily = font.toComposeFontFamily(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin note",
                            tint = if (note.pinned) BrassAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (note.content.isNotBlank()) {
                Text(
                    text = note.content.trim(),
                    fontFamily = font.toComposeFontFamily(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${note.wordCount} / ${note.wordGoal} words · ${font.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolioMetric(label: String, value: String) {
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
