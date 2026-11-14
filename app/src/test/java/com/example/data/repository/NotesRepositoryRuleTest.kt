package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TypewriterFont
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndUpdateNote_authenticatedOwner_succeedsAndEmits(): Unit = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NotesRepository(firestore)

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Chapter One",
                content = "Once upon a time there was a typewriter.",
                wordGoal = 3000L,
                fontId = TypewriterFont.SPECIAL_ELITE.id
            ).getOrThrow()
        }
        assertTrue(noteId.isNotEmpty())

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateNoteContent(
                noteId = noteId,
                title = "Chapter One Revised",
                content = "Once upon a time there was a tactile typewriter on a dark desk.",
                wordGoal = 3000L,
                fontId = TypewriterFont.COURIER_PRIME.id,
                pinned = true,
                archived = false
            )
        }
        assertTrue(updateRes.isSuccess)

        val feed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes(uid).first { feed ->
                feed.notes.any { it.id == noteId && it.title == "Chapter One Revised" }
            }
        }
        val fetched = feed.notes.first { it.id == noteId }
        assertEquals(13L, fetched.wordCount)
        assertTrue(fetched.pinned)

        val delRes = withTimeout(DEFAULT_TIMEOUT_MS) { repository.deleteNote(noteId) }
        assertTrue(delRes.isSuccess)
    }

    @Test
    fun getNote_crossUserAccess_denied(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NotesRepository(firestore)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createNote(title = "Secret Draft", content = "Private words").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NotesRepository(firestore)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getNote(aliceUid, noteId)
        }
        assertTrue(crossResult.isFailure)
        val ex = crossResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeNotes_unauthenticated_denied(): Unit = runBlocking {
        auth.signOut()
        val repository = NotesRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeNotes("alice_unauth_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun recordWritingSession_authenticatedOwner_succeeds(): Unit = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NotesRepository(firestore)

        val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordWritingSession(
                noteId = "note_sample_1",
                wordsWritten = 150L,
                durationSeconds = 240L,
                wpm = 38L
            ).getOrThrow()
        }
        assertTrue(sessionId.isNotEmpty())

        val sessions = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWritingSessions(uid).first { list -> list.any { it.id == sessionId } }
        }
        assertEquals(150L, sessions.first { it.id == sessionId }.wordsWritten)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_keystroke@test.com"
        const val BOB_EMAIL = "bob_keystroke@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
