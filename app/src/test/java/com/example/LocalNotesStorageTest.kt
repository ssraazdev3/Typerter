package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TypewriterFont
import com.example.data.repository.LocalNotesStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalNotesStorageTest {

    private lateinit var storage: LocalNotesStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "TyperterNotes")
        root.deleteRecursively()
        storage = LocalNotesStorage(context)
    }

    @Test
    fun `create update pin and delete note persists json and markdown mirror in phone storage`() = runBlocking {
        val createdId = storage.createNote(
            title = "Midnight Draft",
            content = "Once upon a typewriter night.",
            wordGoal = 1200L,
            fontId = TypewriterFont.COURIER_PRIME.id
        ).getOrThrow()

        val initialFeed = storage.observeNotes().first()
        assertEquals(1, initialFeed.notes.size)
        assertEquals("Midnight Draft", initialFeed.notes.first().title)
        assertEquals(5L, initialFeed.notes.first().wordCount)

        val mdDir = File(storage.storageRootDir, "Markdown")
        val mdFilesAfterCreate = mdDir.listFiles()?.toList().orEmpty()
        assertEquals(1, mdFilesAfterCreate.size)
        assertTrue(mdFilesAfterCreate.first().readText().contains("# Midnight Draft"))
        assertTrue(mdFilesAfterCreate.first().readText().contains("Once upon a typewriter night."))

        storage.updateNoteContent(
            noteId = createdId,
            title = "Midnight Draft Revised",
            content = "Once upon a typewriter night, ink flowed smoothly.",
            wordGoal = 1500L,
            fontId = TypewriterFont.SPECIAL_ELITE.id,
            pinned = true,
            archived = false
        ).getOrThrow()

        val updatedFeed = storage.observeNotes().first()
        assertTrue(updatedFeed.notes.first().pinned)
        assertEquals("Midnight Draft Revised", updatedFeed.notes.first().title)
        assertEquals(8L, updatedFeed.notes.first().wordCount)

        storage.recordWritingSession(
            noteId = createdId,
            wordsWritten = 42L,
            durationSeconds = 60L,
            wpm = 42L
        ).getOrThrow()
        assertEquals(1, storage.observeWritingSessions().first().size)

        storage.deleteNote(createdId).getOrThrow()
        assertTrue(storage.observeNotes().first().notes.isEmpty())
        assertFalse(File(storage.storageRootDir, "$createdId.json").exists())
    }
}
