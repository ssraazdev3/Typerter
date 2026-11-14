package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.model.Note
import com.example.data.model.TypewriterFont
import com.example.data.model.WritingSession
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID

/**
 * Stores notes and writing sessions directly as files in phone storage
 * (`TyperterNotes` inside app Documents directory on phone storage) so users
 * can write, organize, and preserve notes completely offline without an account.
 */
class LocalNotesStorage(context: Context) {

    val storageRootDir: File = run {
        val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        File(docs ?: context.filesDir, "TyperterNotes").apply { mkdirs() }
    }

    private val markdownMirrorDir: File = File(storageRootDir, "Markdown").apply { mkdirs() }
    private val sessionsFile: File = File(storageRootDir, "writing_sessions.json")

    private val notesFlowInternal = MutableStateFlow<List<Note>>(emptyList())
    private val sessionsFlowInternal = MutableStateFlow<List<WritingSession>>(emptyList())

    init {
        refreshFromDisk()
    }

    fun getStorageDisplayPath(): String = storageRootDir.absolutePath

    fun observeNotes(): Flow<NotesFeed> = notesFlowInternal.asStateFlow().map { notes ->
        NotesFeed(
            notes = notes,
            hasPendingWrites = false,
            isFromCache = false
        )
    }

    fun observeWritingSessions(): Flow<List<WritingSession>> = sessionsFlowInternal.asStateFlow()

    suspend fun createNote(
        title: String,
        content: String,
        wordGoal: Long,
        fontId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            storageRootDir.mkdirs()
            markdownMirrorDir.mkdirs()
            val noteId = "local_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val nowMs = System.currentTimeMillis()
            val note = Note(
                id = noteId,
                userId = LOCAL_USER_ID,
                title = title.take(200),
                content = content.take(500_000),
                wordCount = NotesRepository.countWords(content),
                charCount = content.length.toLong(),
                wordGoal = wordGoal.coerceIn(0L, 200_000L),
                fontId = fontId,
                pinned = false,
                archived = false,
                createdAt = Timestamp(Date(nowMs)),
                updatedAt = Timestamp(Date(nowMs))
            )
            writeNoteFiles(note, createdAtMs = nowMs, updatedAtMs = nowMs)
            refreshFromDisk()
            noteId
        }
    }

    suspend fun updateNoteContent(
        noteId: String,
        title: String,
        content: String,
        wordGoal: Long,
        fontId: String,
        pinned: Boolean,
        archived: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = noteJsonFile(noteId)
            val existingCreatedMs = if (file.exists()) {
                runCatching { JSONObject(file.readText()).optLong("createdAtMs", System.currentTimeMillis()) }
                    .getOrDefault(System.currentTimeMillis())
            } else {
                System.currentTimeMillis()
            }
            val nowMs = System.currentTimeMillis()
            val note = Note(
                id = noteId,
                userId = LOCAL_USER_ID,
                title = title.take(200),
                content = content.take(500_000),
                wordCount = NotesRepository.countWords(content),
                charCount = content.length.toLong(),
                wordGoal = wordGoal.coerceIn(0L, 200_000L),
                fontId = fontId,
                pinned = pinned,
                archived = archived,
                createdAt = Timestamp(Date(existingCreatedMs)),
                updatedAt = Timestamp(Date(nowMs))
            )
            writeNoteFiles(note, createdAtMs = existingCreatedMs, updatedAtMs = nowMs)
            refreshFromDisk()
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            noteJsonFile(noteId).delete()
            markdownMirrorDir.listFiles { f -> f.name.endsWith("_$noteId.md") }?.forEach { it.delete() }
            refreshFromDisk()
            Unit
        }
    }

    suspend fun recordWritingSession(
        noteId: String,
        wordsWritten: Long,
        durationSeconds: Long,
        wpm: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val id = "lsess_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val nowMs = System.currentTimeMillis()
            val list = loadSessionsFromDisk().toMutableList()
            list.add(
                0,
                WritingSession(
                    id = id,
                    userId = LOCAL_USER_ID,
                    noteId = noteId,
                    wordsWritten = wordsWritten.coerceIn(0L, 200_000L),
                    durationSeconds = durationSeconds.coerceIn(0L, 86_400L),
                    wpm = wpm.coerceIn(0L, 400L),
                    createdAt = Timestamp(Date(nowMs))
                )
            )
            saveSessionsToDisk(list.take(200))
            sessionsFlowInternal.value = loadSessionsFromDisk()
            id
        }
    }

    fun currentNotesSnapshot(): List<Note> = notesFlowInternal.value

    private fun noteJsonFile(noteId: String): File = File(storageRootDir, "$noteId.json")

    private fun writeNoteFiles(note: Note, createdAtMs: Long, updatedAtMs: Long) {
        val json = JSONObject().apply {
            put("id", note.id)
            put("userId", note.userId)
            put("title", note.title)
            put("content", note.content)
            put("wordCount", note.wordCount)
            put("charCount", note.charCount)
            put("wordGoal", note.wordGoal)
            put("fontId", note.fontId)
            put("pinned", note.pinned)
            put("archived", note.archived)
            put("createdAtMs", createdAtMs)
            put("updatedAtMs", updatedAtMs)
        }
        noteJsonFile(note.id).writeText(json.toString(2))

        // Write readable .md file mirror to phone storage Documents/TyperterNotes/Markdown/
        markdownMirrorDir.listFiles { f -> f.name.endsWith("_${note.id}.md") }?.forEach { it.delete() }
        val safeTitle = note.title
            .ifBlank { "Untitled_Note" }
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(36)
            .ifBlank { "Note" }
        val mdFile = File(markdownMirrorDir, "${safeTitle}_${note.id}.md")
        val mdContent = buildString {
            append("# ").append(note.title.ifBlank { "Untitled Note" }).append("\n\n")
            append(note.content)
            if (!note.content.endsWith("\n")) append("\n")
        }
        mdFile.writeText(mdContent)
    }

    private fun refreshFromDisk() {
        val files = storageRootDir.listFiles { f ->
            f.isFile && f.name.endsWith(".json") && f.name != "writing_sessions.json"
        }.orEmpty()

        val parsed = files.mapNotNull { file ->
            runCatching {
                val obj = JSONObject(file.readText())
                val createdMs = obj.optLong("createdAtMs", file.lastModified())
                val updatedMs = obj.optLong("updatedAtMs", file.lastModified())
                Note(
                    id = obj.optString("id", file.nameWithoutExtension),
                    userId = LOCAL_USER_ID,
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    wordCount = obj.optLong("wordCount", 0L),
                    charCount = obj.optLong("charCount", 0L),
                    wordGoal = obj.optLong("wordGoal", 3000L),
                    fontId = obj.optString("fontId", TypewriterFont.SPECIAL_ELITE.id),
                    pinned = obj.optBoolean("pinned", false),
                    archived = obj.optBoolean("archived", false),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(updatedMs))
                )
            }.getOrNull()
        }.sortedWith(
            compareByDescending<Note> { it.pinned }
                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        )

        notesFlowInternal.value = parsed
        sessionsFlowInternal.value = loadSessionsFromDisk()
    }

    private fun loadSessionsFromDisk(): List<WritingSession> {
        if (!sessionsFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(sessionsFile.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                WritingSession(
                    id = obj.optString("id", ""),
                    userId = LOCAL_USER_ID,
                    noteId = obj.optString("noteId", ""),
                    wordsWritten = obj.optLong("wordsWritten", 0L),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    wpm = obj.optLong("wpm", 0L),
                    createdAt = Timestamp(Date(obj.optLong("createdAtMs", 0L)))
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSessionsToDisk(sessions: List<WritingSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("noteId", s.noteId)
                    put("wordsWritten", s.wordsWritten)
                    put("durationSeconds", s.durationSeconds)
                    put("wpm", s.wpm)
                    put("createdAtMs", s.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        sessionsFile.writeText(arr.toString(2))
    }

    companion object {
        const val LOCAL_USER_ID = "local_device_storage"
    }
}
