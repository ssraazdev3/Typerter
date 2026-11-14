package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Note
import com.example.data.model.TypewriterFont
import com.example.data.model.WritingSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class NotesFeed(
    val notes: List<Note>,
    val hasPendingWrites: Boolean,
    val isFromCache: Boolean
)

class NotesRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeNotes(userId: String): Flow<NotesFeed> = flow {
        val path = "users/$userId/notes"
        emitAll(
            db.collection("users").document(userId).collection("notes")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val notes = snapshot.toObjects(
                        Note::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Note> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                    NotesFeed(
                        notes = notes,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getNote(userId: String, noteId: String): Result<Note?> {
        val docRef = db.collection("users").document(userId).collection("notes").document(noteId)
        return try {
            val snap = docRef.get().await()
            val note = snap.toObject(
                Note::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        title: String = "",
        content: String = "",
        wordGoal: Long = 3000L,
        fontId: String = TypewriterFont.SPECIAL_ELITE.id
    ): Result<String> {
        val uid = requireUserId()
        val noteId = "note_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        val wordCount = countWords(content)
        val charCount = content.length.toLong()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.take(200),
            "content" to content.take(500_000),
            "wordCount" to wordCount,
            "charCount" to charCount,
            "wordGoal" to wordGoal.coerceIn(0L, 200_000L),
            "fontId" to fontId,
            "pinned" to false,
            "archived" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
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
    ): Result<Unit> {
        val uid = requireUserId()
        require(noteId.isNotBlank()) { "Note ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        val wordCount = countWords(content)
        val charCount = content.length.toLong()
        val updates = mapOf(
            "title" to title.take(200),
            "content" to content.take(500_000),
            "wordCount" to wordCount,
            "charCount" to charCount,
            "wordGoal" to wordGoal.coerceIn(0L, 200_000L),
            "fontId" to fontId,
            "pinned" to pinned,
            "archived" to archived,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordWritingSession(
        noteId: String,
        wordsWritten: Long,
        durationSeconds: Long,
        wpm: Long
    ): Result<String> {
        val uid = requireUserId()
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid)
            .collection("writing_sessions").document(sessionId)
        val payload = mapOf(
            "userId" to uid,
            "noteId" to noteId,
            "wordsWritten" to wordsWritten.coerceIn(0L, 200_000L),
            "durationSeconds" to durationSeconds.coerceIn(0L, 86_400L),
            "wpm" to wpm.coerceIn(0L, 400L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeWritingSessions(userId: String): Flow<List<WritingSession>> = flow {
        val path = "users/$userId/writing_sessions"
        emitAll(
            db.collection("users").document(userId).collection("writing_sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WritingSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    companion object {
        fun countWords(text: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0L
            return trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }.toLong()
        }
    }
}
